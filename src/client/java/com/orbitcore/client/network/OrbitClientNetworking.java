package com.orbitcore.client.network;

import com.orbitcore.registry.ModSounds;
import com.orbitcore.client.story.EchoCutscene;
import com.orbitcore.client.sound.SpaceDread;
import com.orbitcore.client.flight.QuakeShake;
import com.orbitcore.client.screen.MissionControlScreen;
import com.orbitcore.client.screen.RocketScreen;
import com.orbitcore.client.screen.TelescopeScreen;
import com.orbitcore.client.science.ClientSpaceProgram;
import com.orbitcore.client.toast.OrbitToast;
import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.science.CelestialBody;
import com.orbitcore.science.Research;
import com.orbitcore.science.SpaceProgram;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class OrbitClientNetworking {
    private OrbitClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(OrbitNetworking.PROGRAM_SYNC, (client, handler, buf, sender) -> {
            NbtCompound nbt = buf.readNbt();
            client.execute(() -> ClientSpaceProgram.set(nbt == null ? new SpaceProgram() : SpaceProgram.fromNbt(nbt)));
        });
        ClientPlayNetworking.registerGlobalReceiver(OrbitNetworking.OPEN_TELESCOPE, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> client.setScreen(new TelescopeScreen(pos)));
        });
        ClientPlayNetworking.registerGlobalReceiver(OrbitNetworking.OPEN_MISSION_CONTROL, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            client.execute(() -> client.setScreen(new MissionControlScreen(pos)));
        });
        ClientPlayNetworking.registerGlobalReceiver(OrbitNetworking.DISCOVERY, (client, handler, buf, sender) -> {
            CelestialBody body = CelestialBody.byId(buf.readString());
            client.execute(() -> {
                if (body != null) {
                    client.getToastManager().add(OrbitToast.discovery(Text.translatable(body.translationKey()), body == CelestialBody.MOON));
                    client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(OrbitNetworking.RESEARCH_DONE, (client, handler, buf, sender) -> {
            Research research = Research.byId(buf.readIdentifier());
            client.execute(() -> {
                if (research != null) {
                    client.getToastManager().add(OrbitToast.research(Text.translatable(research.translationKey()),
                            new ItemStack(research.icon().get())));
                    client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.4f));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(OrbitNetworking.QUAKE, (client, handler, buf, sender) -> {
            float strength = buf.readFloat();
            int ticks = buf.readVarInt();
            client.execute(() -> QuakeShake.start(strength, ticks));
        });
        ClientPlayNetworking.registerGlobalReceiver(OrbitNetworking.OPEN_ROCKET, (client, handler, buf, sender) -> {
            int entityId = buf.readVarInt();
            client.execute(() -> client.setScreen(new RocketScreen(entityId)));
        });
        ClientPlayNetworking.registerGlobalReceiver(OrbitNetworking.ECHO_SITE, (client, handler, buf, sender) -> {
            BlockPos echo = buf.readBlockPos();
            BlockPos zarya = buf.readBoolean() ? buf.readBlockPos() : null;
            boolean announce = buf.readBoolean();
            client.execute(() -> {
                SpaceDread.setSite(echo, zarya);
                if (announce) {
                    client.getToastManager().add(OrbitToast.signal());
                    client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.FLIGHT_QUINDAR_IN, 1.0f, 0.5f));
                    client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.ECHO_SIGNAL, 1.0f, 0.9f));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(OrbitNetworking.ECHO_CONTACT, (client, handler, buf, sender) -> {
            BlockPos monolith = buf.readBlockPos();
            client.execute(() -> EchoCutscene.start(monolith));
        });
        ClientPlayNetworking.registerGlobalReceiver(OrbitNetworking.ECHO_SCARE, (client, handler, buf, sender) ->
                client.execute(SpaceDread::scare));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientSpaceProgram.reset();
            SpaceDread.setSite(null, null);
        });
    }

    public static void sendObservation(BlockPos telescope, String target) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(telescope);
        buf.writeString(target);
        ClientPlayNetworking.send(OrbitNetworking.OBSERVE, buf);
    }

    public static void sendLaunch(int rocketId, int destination) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(rocketId);
        buf.writeVarInt(destination);
        ClientPlayNetworking.send(OrbitNetworking.LAUNCH, buf);
    }

    public static void sendFlightCommand(int command, float value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(command);
        buf.writeFloat(value);
        ClientPlayNetworking.send(OrbitNetworking.FLIGHT_COMMAND, buf);
    }

    public static void sendUnlockResearch(BlockPos missionControl, Identifier research) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(missionControl);
        buf.writeIdentifier(research);
        ClientPlayNetworking.send(OrbitNetworking.UNLOCK_RESEARCH, buf);
    }
}
