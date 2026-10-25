package com.orbitcore.world;

import com.orbitcore.entity.MeteorEntity;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.registry.ModGameRules;
import com.orbitcore.registry.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.List;

/**
 * Живые события Луны: падающие метеориты и лунотрясения.
 * Выключаются правилом {@code /gamerule orbitcoreLunarEvents false}.
 */
public final class LunarEvents {
    /** В среднем один метеорит рядом с игроком за столько тиков (12 минут). */
    public static final int METEOR_INTERVAL = 20 * 60 * 12;
    /** В среднем одно лунотрясение за столько тиков (20 минут). */
    public static final int QUAKE_INTERVAL = 20 * 60 * 20;
    /** Скорость метеорита, блоков за тик. */
    private static final double METEOR_SPEED = 2.5;

    private LunarEvents() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(LunarEvents::tick);
    }

    private static void tick(ServerWorld world) {
        if (world.getRegistryKey() != ModDimensions.MOON || !world.getGameRules().getBoolean(ModGameRules.LUNAR_EVENTS)) {
            return;
        }
        List<ServerPlayerEntity> players = world.getPlayers(p -> !p.isSpectator() && !(p.getVehicle() instanceof RocketEntity));
        if (players.isEmpty()) {
            return;
        }
        Random random = world.random;
        for (ServerPlayerEntity player : players) {
            if (random.nextInt(METEOR_INTERVAL) == 0) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = 20 + random.nextDouble() * 40;
                BlockPos target = BlockPos.ofFloored(player.getX() + Math.cos(angle) * distance, player.getY(),
                        player.getZ() + Math.sin(angle) * distance);
                spawnMeteor(world, target, 3 + random.nextInt(3));
            }
        }
        if (random.nextInt(QUAKE_INTERVAL) == 0) {
            moonquake(world, 0.6f + random.nextFloat(), 100 + random.nextInt(100));
        }
    }

    /**
     * Запустить метеорит, который упадёт в точку {@code target}: он появляется
     * высоко в стороне и летит наискосок, чтобы его было видно на подлёте.
     */
    public static MeteorEntity spawnMeteor(ServerWorld world, BlockPos target, int size) {
        Random random = world.random;
        int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, target.getX(), target.getZ());
        Vec3d impact = new Vec3d(target.getX() + 0.5, ground, target.getZ() + 0.5);
        double angle = random.nextDouble() * Math.PI * 2;
        Vec3d start = impact.add(Math.cos(angle) * 70, 110, Math.sin(angle) * 70);
        MeteorEntity meteor = ModEntities.METEOR.create(world);
        meteor.refreshPositionAndAngles(start.x, start.y, start.z, 0, 0);
        meteor.setVelocity(impact.subtract(start).normalize().multiply(METEOR_SPEED));
        meteor.setSize(size);
        world.spawnEntity(meteor);
        for (ServerPlayerEntity player : world.getPlayers(p -> p.squaredDistanceTo(impact) < 120 * 120)) {
            player.sendMessage(Text.translatable("message.orbitcore.meteor").formatted(Formatting.GOLD), true);
            player.playSound(ModSounds.METEOR_INCOMING, SoundCategory.AMBIENT, 1.0f, 1.0f);
        }
        return meteor;
    }

    /** Лунотрясение: всех игроков на Луне трясёт, сыплется пыль, гудит грунт. */
    public static void moonquake(ServerWorld world, float strength, int ticks) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            OrbitNetworking.sendQuake(player, strength, ticks);
            player.playSound(ModSounds.MOONQUAKE, SoundCategory.AMBIENT, Math.min(1.0f, strength), 1.0f);
        }
    }
}
