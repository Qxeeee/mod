package com.orbitcore.client.flight;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Лунотрясение на клиенте: камера дрожит, с грунта вокруг поднимается пыль.
 * Сила нарастает, держится и затихает к концу.
 */
public final class QuakeShake {
    private static float strength;
    private static int total;
    private static int left;

    private QuakeShake() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(QuakeShake::tick);
    }

    public static void start(float power, int ticks) {
        strength = power;
        total = Math.max(1, ticks);
        left = ticks;
    }

    /** Текущая сила с плавным нарастанием и затуханием. */
    public static float intensity(float tickDelta) {
        if (left <= 0) {
            return 0;
        }
        float t = 1 - (left - tickDelta) / total;
        float envelope = Math.min(1, t * 6) * Math.min(1, (1 - t) * 3);
        return strength * MathHelper.clamp(envelope, 0, 1);
    }

    /** Смещение взгляда камеры, градусы: {yaw, pitch}. */
    public static float[] offset(float tickDelta) {
        float k = intensity(tickDelta);
        if (k <= 0) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        double time = (client.world == null ? 0 : client.world.getTime()) + tickDelta;
        float yaw = (float) (Math.sin(time * 2.3) + Math.sin(time * 5.7 + 1.3) * 0.6) * k * 1.2f;
        float pitch = (float) (Math.cos(time * 3.1) + Math.sin(time * 6.9 + 0.4) * 0.5) * k * 1.2f;
        return new float[]{yaw, pitch};
    }

    private static void tick(MinecraftClient client) {
        if (left <= 0) {
            return;
        }
        left--;
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.isPaused()) {
            return;
        }
        Random random = player.getRandom();
        int count = (int) (intensity(0) * 6);
        for (int i = 0; i < count; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 16;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 16;
            BlockPos ground = BlockPos.ofFloored(x, player.getY() - 0.5, z);
            BlockState state = client.world.getBlockState(ground);
            if (state.isAir()) {
                continue;
            }
            client.world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), x, ground.getY() + 1.05, z, 0, 0.15, 0);
        }
    }
}
