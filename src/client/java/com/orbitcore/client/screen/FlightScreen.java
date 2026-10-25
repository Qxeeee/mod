package com.orbitcore.client.screen;

import com.orbitcore.OrbitCore;
import com.orbitcore.client.flight.FlightCinematics;
import com.orbitcore.client.network.OrbitClientNetworking;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.FlightPhase;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Экран полёта поверх мира: кинополосы, субтитры и телеметрия, а на орбите —
 * вид из иллюминатора на Землю. Мышь спрятана, пробел пропускает подъём.
 */
public class FlightScreen extends Screen {
    private static final Identifier EARTH = OrbitCore.id("textures/gui/flight/earth.png");
    private static final float[][] STARS = stars();

    private float bars;
    private long opened;

    public FlightScreen() {
        super(Text.translatable("flight.orbitcore.title"));
    }

    @Override
    protected void init() {
        opened = System.currentTimeMillis();
        if (client != null) {
            InputUtil.setCursorParameters(client.getWindow().getHandle(), GLFW.GLFW_CURSOR_HIDDEN, width / 2.0, height / 2.0);
        }
    }

    @Override
    public void removed() {
        if (client != null) {
            InputUtil.setCursorParameters(client.getWindow().getHandle(), GLFW.GLFW_CURSOR_NORMAL, width / 2.0, height / 2.0);
        }
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
        RocketEntity rocket = FlightCinematics.rocket();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            FlightCinematics.openPauseMenu();
            return true;
        }
        if (rocket == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        FlightPhase phase = rocket.phase();
        if (keyCode == GLFW.GLFW_KEY_SPACE && (phase == FlightPhase.COUNTDOWN || phase == FlightPhase.ASCENT)) {
            OrbitClientNetworking.sendFlightCommand(OrbitNetworking.COMMAND_SKIP, 0);
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && phase == FlightPhase.SPACE) {
            OrbitClientNetworking.sendFlightCommand(OrbitNetworking.COMMAND_DEORBIT, 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RocketEntity rocket = FlightCinematics.rocket();
        if (rocket == null) {
            return;
        }
        FlightPhase phase = rocket.phase();
        if (phase == FlightPhase.SPACE) {
            drawOrbit(context, rocket);
            return;
        }
        if (phase == FlightPhase.REENTRY && !rocket.parachute()) {
            drawPlasma(context);
        }
        drawBars(context, delta);
        Text subtitle = FlightCinematics.subtitle(rocket);
        if (subtitle != null) {
            context.drawCenteredTextWithShadow(textRenderer, subtitle, width / 2, height - barHeight() - 18, 0xFFFFFFFF);
        }
        drawTelemetry(context, rocket);
        if (phase == FlightPhase.COUNTDOWN || phase == FlightPhase.ASCENT) {
            Text skip = Text.translatable("flight.orbitcore.skip");
            context.drawText(textRenderer, skip, width - textRenderer.getWidth(skip) - 8, (barHeight() - 8) / 2, OrbitStyle.TEXT_DIM, false);
        }
    }

    private int barHeight() {
        return (int) (height * 0.11f * bars);
    }

    /** Кинополосы плавно выезжают сверху и снизу. */
    private void drawBars(DrawContext context, float delta) {
        bars = Math.min(1f, (System.currentTimeMillis() - opened) / 900f);
        int bar = barHeight();
        context.fill(0, 0, width, bar, 0xFF000000);
        context.fill(0, height - bar, width, height, 0xFF000000);
    }

    private void drawTelemetry(DrawContext context, RocketEntity rocket) {
        int y = height - barHeight() + 6;
        if (barHeight() < 20) {
            return;
        }
        String altitude = String.format("%.1f", FlightCinematics.altitudeKm(rocket));
        String speed = String.format("%.2f", FlightCinematics.speedKms(rocket));
        context.drawText(textRenderer, Text.translatable("flight.orbitcore.telemetry", altitude, speed), 10, y, OrbitStyle.ACCENT, false);
        Text destination = Text.translatable(rocket.destination().translationKey());
        context.drawText(textRenderer, Text.translatable("flight.orbitcore.destination", destination), 10, y + 11, OrbitStyle.TEXT_DIM, false);
        int stages = rocket.design().stages().size();
        Text stage = Text.translatable("flight.orbitcore.stages_left", stages);
        context.drawText(textRenderer, stage, width - textRenderer.getWidth(stage) - 10, y, OrbitStyle.TEXT_DIM, false);
    }

    /** Края экрана раскалены — капсула в плазме. */
    private void drawPlasma(DrawContext context) {
        float flicker = 0.75f + 0.25f * MathHelper.sin(System.currentTimeMillis() / 60f);
        int alpha = (int) (150 * flicker);
        int edge = height / 4;
        context.fillGradient(0, height - edge, width, height, 0x00FF7A1A, alpha << 24 | 0xFF7A1A);
        context.fillGradient(0, 0, width, edge / 2, alpha / 2 << 24 | 0xFFB040, 0x00FFB040);
    }

    // ------------------------------------------------------------------ орбита

    private void drawOrbit(DrawContext context, RocketEntity rocket) {
        context.fill(0, 0, width, height, 0xFF01020A);
        long now = System.currentTimeMillis();
        for (float[] star : STARS) {
            int x = (int) (star[0] * width);
            int y = (int) (star[1] * height * 0.75f);
            float twinkle = 0.6f + 0.4f * MathHelper.sin(now / 400f * star[3] + star[2] * 10);
            int a = (int) (255 * star[2] * twinkle);
            context.fill(x, y, x + 1, y + 1, a << 24 | 0xFFFFFF);
        }
        // Земля занимает низ экрана, видна кривизна горизонта
        int size = (int) (width * 1.9f);
        float drift = MathHelper.sin(now / 9000f) * 4;
        int ex = width / 2 - size / 2;
        int ey = (int) (height * 0.58f + drift);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.drawTexture(EARTH, ex, ey, size, size, 0, 0, 512, 512, 512, 512);
        RenderSystem.disableBlend();
        // Солнце за краем иллюминатора: мягкий блик из полупрозрачных слоёв
        int sx = (int) (width * 0.82f);
        int sy = (int) (height * 0.18f);
        for (int r = 30; r > 2; r -= 2) {
            int a = (int) (6 + (30 - r) * 0.9f);
            int half = (int) Math.round(r * 0.72);
            context.fill(sx - r, sy - half, sx + r, sy + half, a << 24 | 0xFFF4D8);
            context.fill(sx - half, sy - r, sx + half, sy + r, a << 24 | 0xFFF4D8);
        }
        context.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFFFFFFFF);

        int panel = 40;
        context.fill(0, height - panel, width, height, 0xC0000000);
        String altitude = String.format("%.0f", 400 + MathHelper.sin(rocket.flightTick() / 80f) * 6);
        context.drawText(textRenderer, Text.translatable("flight.orbitcore.orbit", altitude), 10, height - panel + 8, OrbitStyle.ACCENT, false);
        int left = Math.max(0, (FlightController.ORBIT_TICKS - rocket.flightTick()) / 20);
        Text deorbit = Text.translatable("flight.orbitcore.deorbit", left);
        context.drawText(textRenderer, deorbit, 10, height - panel + 22, OrbitStyle.TEXT, false);
        Text title = Text.translatable("flight.orbitcore.sub.orbit");
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFFFF);
    }

    private static float[][] stars() {
        Random random = new Random(77);
        float[][] stars = new float[420][];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new float[]{random.nextFloat(), random.nextFloat(), 0.2f + (float) Math.pow(random.nextFloat(), 2) * 0.8f,
                    1 + random.nextFloat() * 3};
        }
        return stars;
    }
}
