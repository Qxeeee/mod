package com.orbitcore.client.story;

import com.orbitcore.client.flight.CameraShot;
import com.orbitcore.registry.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Катсцена у монолита: камера медленно облетает Эхо и поднимается вдоль него,
 * звук нарастает, субтитры, в конце — вспышка и тишина.
 */
public final class EchoCutscene {
    public static final int DURATION = 240;
    private static final int[] SUBTITLE_AT = {0, 60, 120, 180};

    @Nullable
    private static Vec3d base;
    private static long start;
    private static boolean savedHudHidden;

    private EchoCutscene() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EchoCutscene::tick);
    }

    public static boolean active() {
        return base != null;
    }

    public static void start(BlockPos monolith) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || active()) {
            return;
        }
        base = Vec3d.ofBottomCenter(monolith).add(0, 1, 0);
        start = client.world.getTime();
        savedHudHidden = client.options.hudHidden;
        client.options.hudHidden = true;
        client.getSoundManager().play(new PositionedSoundInstance(ModSounds.ECHO_CONTACT.getId(), SoundCategory.AMBIENT, 1.0f, 1.0f,
                SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true));
        client.setScreen(new Overlay());
    }

    private static void tick(MinecraftClient client) {
        if (!active()) {
            return;
        }
        if (client.world == null || client.player == null || client.world.getTime() - start >= DURATION) {
            finish(client);
        }
    }

    private static void finish(MinecraftClient client) {
        base = null;
        client.options.hudHidden = savedHudHidden;
        if (client.currentScreen instanceof Overlay) {
            client.setScreen(null);
        }
        if (client.player != null) {
            client.inGameHud.setOverlayMessage(Text.translatable("cutscene.orbitcore.echo.reward").formatted(Formatting.LIGHT_PURPLE), false);
        }
    }

    private static float time(float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? 0 : client.world.getTime() - start + tickDelta;
    }

    /** Камера: облёт по спирали снизу вверх, взгляд на монолит. */
    @Nullable
    public static CameraShot cameraShot(float tickDelta) {
        if (base == null) {
            return null;
        }
        float p = MathHelper.clamp(time(tickDelta) / DURATION, 0, 1);
        double angle = Math.toRadians(30 + p * 220);
        double radius = MathHelper.lerp(p, 13, 8);
        double height = MathHelper.lerp(p * p, 6, 17);
        double x = base.x + Math.sin(angle) * radius;
        double z = base.z + Math.cos(angle) * radius;
        // Склоны кратера поднимаются к краю: камера не должна уходить в грунт
        double y = base.y + height;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            y = Math.max(y, client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z)) + 2.5);
        }
        return CameraShot.lookAt(x, y, z, base.x, base.y + MathHelper.lerp(p, 6, 13), base.z);
    }

    /** Кинополосы, субтитры, вспышка в конце. Пока идёт сцена, игрок ничего не нажимает. */
    private static final class Overlay extends Screen {
        Overlay() {
            super(Text.empty());
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE && client != null) {
                client.setScreen(new GameMenuScreen(true));
            }
            return true;
        }

        @Override
        public void renderBackground(DrawContext context) {
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            float t = time(delta);
            int bar = (int) (height * 0.11f * MathHelper.clamp(t / 20f, 0, 1));
            context.fill(0, 0, width, bar, 0xFF000000);
            context.fill(0, height - bar, width, height, 0xFF000000);
            int line = 0;
            for (int i = 0; i < SUBTITLE_AT.length; i++) {
                if (t >= SUBTITLE_AT[i]) {
                    line = i;
                }
            }
            if (t < DURATION - 30) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("cutscene.orbitcore.echo." + (line + 1)),
                        width / 2, height - bar - 18, 0xFFE6D8FF);
            }
            // Вспышка: всё белеет, а потом — тишина
            if (t > DURATION - 45) {
                float k = MathHelper.clamp((t - (DURATION - 45)) / 35f, 0, 1);
                context.fill(0, 0, width, height, (int) (k * 255) << 24 | 0xF4EEFF);
            }
        }
    }
}
