package com.orbitcore.client.story;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.orbitcore.OrbitCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Озвучка: у каждой реплики есть русская и английская запись ({@code voice.<реплика>.ru}
 * и {@code .en}); играет та, что на языке игры. Голос идёт «в голове» — по рации.
 */
public final class VoiceLines {
    @Nullable
    private static SoundInstance current;
    @Nullable
    private static String pending;
    private static int pendingTicks;

    private VoiceLines() {
    }

    @Nullable
    public static SoundEvent event(MinecraftClient client, String line) {
        String lang = client.getLanguageManager().getLanguage().startsWith("ru") ? "ru" : "en";
        Identifier id = OrbitCore.id("voice." + line + "." + lang);
        return Registries.SOUND_EVENT.containsId(id) ? Registries.SOUND_EVENT.get(id) : null;
    }

    /** Сыграть реплику, если она озвучена. Новая реплика обрывает предыдущую. */
    public static void play(MinecraftClient client, String line) {
        SoundEvent sound = event(client, line);
        if (sound == null) {
            return;
        }
        stop(client);
        current = new PositionedSoundInstance(sound.getId(), SoundCategory.VOICE, 1.0f, 1.0f, SoundInstance.createRandom(), false, 0,
                SoundInstance.AttenuationType.NONE, 0, 0, 0, true);
        client.getSoundManager().play(current);
    }

    /** Сыграть реплику через {@code ticks} тиков — например, после сигнала или удара. */
    public static void playLater(String line, int ticks) {
        pending = line;
        pendingTicks = ticks;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pending != null && --pendingTicks <= 0) {
                String line = pending;
                pending = null;
                play(client, line);
            }
        });
    }

    public static void stop(MinecraftClient client) {
        if (current != null) {
            client.getSoundManager().stop(current);
            current = null;
        }
    }
}
