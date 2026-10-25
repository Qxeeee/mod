package com.orbitcore.client.sound;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

/** Бесконечный звук «в голове», который плавно меняет громкость и сам затихает. */
public final class LoopSound extends MovingSoundInstance {
    private float target;

    private LoopSound(SoundEvent sound, SoundCategory category) {
        super(sound, category, SoundInstance.createRandom());
        repeat = true;
        repeatDelay = 0;
        volume = 0.02f;
        relative = true;
        attenuationType = AttenuationType.NONE;
    }

    /**
     * Держать звук на заданной громкости: запускает его, когда нужно, и отпускает, когда он затих.
     * Возвращает звук, который надо передать сюда же в следующий тик.
     */
    @Nullable
    public static LoopSound drive(@Nullable LoopSound sound, SoundEvent event, SoundCategory category, float volume) {
        if (sound != null && !sound.isDone()) {
            sound.target = volume;
            return sound;
        }
        if (volume < 0.02f) {
            return null;
        }
        LoopSound fresh = new LoopSound(event, category);
        fresh.target = volume;
        MinecraftClient.getInstance().getSoundManager().play(fresh);
        return fresh;
    }

    @Override
    public void tick() {
        volume += (target - volume) * 0.04f;
        if (target <= 0 && volume < 0.015f) {
            setDone();
        }
    }

    @Override
    public boolean shouldAlwaysPlay() {
        return true;
    }
}
