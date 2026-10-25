package com.orbitcore.client.sound;

import com.orbitcore.OrbitCore;
import com.orbitcore.survival.LifeSupport;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

/** Насколько тише звук, когда вокруг игрока вакуум. */
public final class VacuumAudio {
    /** Далёкие звуки почти не слышны. */
    private static final float FAR = 0.12f;
    /** Свои шаги и то, что рядом, доходят через подошвы и перчатки. */
    private static final float NEAR = 0.45f;

    private VacuumAudio() {
    }

    public static float factor(SoundInstance sound) {
        SoundCategory category = sound.getCategory();
        if (category == SoundCategory.MASTER || category == SoundCategory.MUSIC || category == SoundCategory.RECORDS
                || category == SoundCategory.VOICE) {
            return 1.0f;
        }
        // Сигналы скафандра, эфир и то, что звучит «в голове», слышны внутри шлема
        String path = sound.getId().getPath();
        if (OrbitCore.MOD_ID.equals(sound.getId().getNamespace())
                && (path.startsWith("item.") || path.startsWith("ambient.void.") || path.startsWith("echo.") || path.startsWith("flight."))) {
            return 1.0f;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || LifeSupport.get(player).breathable) {
            return 1.0f;
        }
        if (sound.isRelative()) {
            return 1.0f;
        }
        double distance = player.squaredDistanceTo(sound.getX(), sound.getY(), sound.getZ());
        return distance < 4 ? NEAR : FAR;
    }
}
