package com.orbitcore.network;

import com.orbitcore.OrbitCore;
import com.orbitcore.entity.RocketEntity;
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
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendProgram(handler.player, SpaceProgram.get(server)));
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
