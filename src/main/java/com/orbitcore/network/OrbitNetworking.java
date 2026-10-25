package com.orbitcore.network;

import com.orbitcore.OrbitCore;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.science.CelestialBody;
import com.orbitcore.science.MissionControl;
import com.orbitcore.science.Observatory;
import com.orbitcore.science.Research;
import com.orbitcore.science.SpaceProgram;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Сетевые каналы мода. Сервер — источник правды: клиент только просит
 * («снимок сделан», «изучить узел»), а сервер проверяет и решает.
 */
public final class OrbitNetworking {
    // Сервер → клиент
    public static final Identifier PROGRAM_SYNC = OrbitCore.id("program_sync");
    public static final Identifier OPEN_TELESCOPE = OrbitCore.id("open_telescope");
    public static final Identifier OPEN_MISSION_CONTROL = OrbitCore.id("open_mission_control");
    public static final Identifier DISCOVERY = OrbitCore.id("discovery");
    public static final Identifier RESEARCH_DONE = OrbitCore.id("research_done");
    public static final Identifier OPEN_ROCKET = OrbitCore.id("open_rocket");
    // Клиент → сервер
    public static final Identifier OBSERVE = OrbitCore.id("observe");
    public static final Identifier UNLOCK_RESEARCH = OrbitCore.id("unlock_research");
    public static final Identifier LAUNCH = OrbitCore.id("launch");
    public static final Identifier FLIGHT_COMMAND = OrbitCore.id("flight_command");
    public static final Identifier QUAKE = OrbitCore.id("quake");

    /** Команды экипажа во время полёта. */
    public static final int COMMAND_SKIP = 0;
    public static final int COMMAND_MANEUVER = 1;
    public static final int COMMAND_THROTTLE = 2;

    private OrbitNetworking() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(OBSERVE, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            String target = buf.readString(64);
            server.execute(() -> Observatory.observe(player, pos, target));
        });
        ServerPlayNetworking.registerGlobalReceiver(UNLOCK_RESEARCH, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            Identifier id = buf.readIdentifier();
            server.execute(() -> MissionControl.unlock(player, pos, id));
        });
        ServerPlayNetworking.registerGlobalReceiver(LAUNCH, (server, player, handler, buf, sender) -> {
            int entityId = buf.readVarInt();
            int destination = buf.readVarInt();
            server.execute(() -> launch(player, entityId, destination));
        });
        ServerPlayNetworking.registerGlobalReceiver(FLIGHT_COMMAND, (server, player, handler, buf, sender) -> {
            int command = buf.readVarInt();
            float value = buf.readFloat();
            server.execute(() -> flightCommand(player, command, value));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendProgram(handler.player, SpaceProgram.get(server)));
    }

    private static void launch(ServerPlayerEntity player, int entityId, int destinationId) {
        if (!(player.getServerWorld().getEntityById(entityId) instanceof RocketEntity rocket)
                || player.squaredDistanceTo(rocket) > 10 * 10
                || destinationId < 0 || destinationId >= RocketDesign.Destination.values().length) {
            return;
        }
        RocketDesign.Destination destination = RocketDesign.Destination.values()[destinationId];
        Text problem = FlightController.whyCannotLaunch(rocket, player, destination);
        if (problem != null) {
            player.sendMessage(problem.copy().formatted(Formatting.RED), true);
            return;
        }
        FlightController.launch(rocket, player, destination);
    }

    private static void flightCommand(ServerPlayerEntity player, int command, float value) {
        if (!(player.getVehicle() instanceof RocketEntity rocket) || rocket.flight() == null) {
            return;
        }
        switch (command) {
            case COMMAND_SKIP -> rocket.flight().skip();
            case COMMAND_MANEUVER -> rocket.flight().requestManeuver();
            case COMMAND_THROTTLE -> rocket.setThrottle(value);
            default -> {
            }
        }
    }

    public static void syncProgram(MinecraftServer server, SpaceProgram program) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendProgram(player, program);
        }
    }

    private static void sendProgram(ServerPlayerEntity player, SpaceProgram program) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(program.writeNbt(new NbtCompound()));
        ServerPlayNetworking.send(player, PROGRAM_SYNC, buf);
    }

    public static void openScreen(ServerPlayerEntity player, Identifier channel, BlockPos pos) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ServerPlayNetworking.send(player, channel, buf);
    }

    public static void openRocket(ServerPlayerEntity player, RocketEntity rocket) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(rocket.getId());
        ServerPlayNetworking.send(player, OPEN_ROCKET, buf);
    }

    /** Лунотрясение: клиент трясёт камеру {@code ticks} тиков с силой {@code strength}. */
    public static void sendQuake(ServerPlayerEntity player, float strength, int ticks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(strength);
        buf.writeVarInt(ticks);
        ServerPlayNetworking.send(player, QUAKE, buf);
    }

    public static void announceDiscovery(MinecraftServer server, CelestialBody body) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(body.id());
            ServerPlayNetworking.send(player, DISCOVERY, buf);
        }
    }

    public static void announceResearch(MinecraftServer server, Research research) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeIdentifier(research.id());
            ServerPlayNetworking.send(player, RESEARCH_DONE, buf);
        }
    }
}
