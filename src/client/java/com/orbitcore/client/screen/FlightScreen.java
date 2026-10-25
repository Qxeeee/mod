package com.orbitcore.client.screen;

import com.orbitcore.OrbitCore;
import com.orbitcore.client.flight.FlightCinematics;
import com.orbitcore.client.network.OrbitClientNetworking;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.FlightPhase;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.rocket.RocketPart;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * Экран полёта поверх мира: кинополосы, субтитры и телеметрия, а на орбите —
 * вид из иллюминатора на Землю. Мышь спрятана, пробел пропускает подъём.
 */
public class FlightScreen extends Screen {
    private static final Identifier EARTH = OrbitCore.id("textures/gui/flight/earth.png");
    private static final Identifier EARTH_DISK = OrbitCore.id("textures/gui/flight/earth_disk.png");
    private static final Identifier MOON = OrbitCore.id("textures/gui/telescope/moon.png");
    private static final float[][] STARS = stars();

    private float bars;
    private long opened;
    /** Газ тормозного двигателя при ручной посадке, 0–1. */
    private float throttle;
    private float sentThrottle = -1;

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
        if (keyCode == GLFW.GLFW_KEY_SPACE && (phase == FlightPhase.COUNTDOWN || phase == FlightPhase.ASCENT || phase == FlightPhase.TRANSFER)) {
            OrbitClientNetworking.sendFlightCommand(OrbitNetworking.COMMAND_SKIP, 0);
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && phase == FlightPhase.SPACE) {
            OrbitClientNetworking.sendFlightCommand(OrbitNetworking.COMMAND_MANEUVER, 0);
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
        if (phase == FlightPhase.TRANSFER) {
            drawTransfer(context, rocket);
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
        if (phase == FlightPhase.DESCENT) {
            drawLandingInstruments(context, rocket);
        } else {
            drawTelemetry(context, rocket);
        }
        if (phase == FlightPhase.COUNTDOWN || phase == FlightPhase.ASCENT) {
            Text skip = Text.translatable("flight.orbitcore.skip");
            context.drawText(textRenderer, skip, width - textRenderer.getWidth(skip) - 8, (barHeight() - 8) / 2, OrbitStyle.TEXT_DIM, false);
        }
    }

    /** Ручная посадка: W/S — газ, пробел — полный газ, пока зажат, X — выключить двигатель. */
    @Override
    public void tick() {
        RocketEntity rocket = FlightCinematics.rocket();
        if (rocket == null || rocket.phase() != FlightPhase.DESCENT || rocket.autopilot() || client == null) {
            sentThrottle = -1;
            return;
        }
        long window = client.getWindow().getHandle();
        if (sentThrottle < 0) {
            throttle = rocket.throttle();
        }
        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_W) || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_UP)) {
            throttle = Math.min(1, throttle + 0.05f);
        }
        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_S) || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_DOWN)) {
            throttle = Math.max(0, throttle - 0.05f);
        }
        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_X)) {
            throttle = 0;
        }
        float effective = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_SPACE) ? 1.0f : throttle;
        if (Math.abs(effective - sentThrottle) > 0.001f) {
            sentThrottle = effective;
            OrbitClientNetworking.sendFlightCommand(OrbitNetworking.COMMAND_THROTTLE, effective);
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

    // ------------------------------------------------------------------ посадка

    private void drawLandingInstruments(DrawContext context, RocketEntity rocket) {
        int ground = client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, rocket.getBlockX(), rocket.getBlockZ());
        double altitude = Math.max(0, rocket.getY() - ground);
        double speed = rocket.verticalSpeed() * 20;
        boolean danger = speed < -FlightController.SAFE_TOUCHDOWN * 20 && altitude < 30;
        int y = height - barHeight() + 6;
        if (barHeight() < 20) {
            return;
        }
        context.drawText(textRenderer, Text.translatable("flight.orbitcore.landing.altitude", String.format("%.0f", altitude)), 10, y,
                OrbitStyle.ACCENT, false);
        context.drawText(textRenderer, Text.translatable("flight.orbitcore.landing.speed", String.format("%.1f", speed)), 10, y + 11,
                danger ? OrbitStyle.RED : OrbitStyle.GREEN, false);
        Text dv = Text.translatable("flight.orbitcore.landing.dv", (int) rocket.landingDeltaV());
        int bx = width / 2 - 60;
        context.drawText(textRenderer, dv, bx, y, rocket.landingDeltaV() < 150 ? OrbitStyle.RED : OrbitStyle.TEXT, false);
        // Газ двигателя
        int by = y + 12;
        context.fill(bx, by, bx + 120, by + 7, OrbitStyle.PANEL_DARK);
        context.fill(bx + 1, by + 1, bx + 1 + (int) (118 * rocket.throttle()), by + 6, 0xFFFF9A3A);
        Text mode = Text.translatable(rocket.autopilot() ? "flight.orbitcore.landing.autopilot" : "flight.orbitcore.landing.controls");
        context.drawText(textRenderer, mode, width - textRenderer.getWidth(mode) - 10, y + 11, OrbitStyle.TEXT_DIM, false);
        if (!rocket.design().has(RocketPart.Kind.LEGS)) {
            Text legs = Text.translatable("flight.orbitcore.landing.no_legs");
            context.drawText(textRenderer, legs, width - textRenderer.getWidth(legs) - 10, y, OrbitStyle.RED, false);
        }
        if (danger && (System.currentTimeMillis() / 300) % 2 == 0) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("flight.orbitcore.landing.too_fast"), width / 2,
                    barHeight() + 12, OrbitStyle.RED);
        }
    }

    // ------------------------------------------------------------------ перелёт

    /** Перелёт: одно тело уменьшается за спиной, другое растёт впереди. */
    private void drawTransfer(DrawContext context, RocketEntity rocket) {
        drawStarfield(context, 1.0f);
        float p = MathHelper.clamp(rocket.flightTick() / (float) FlightController.TRANSFER_TICKS, 0, 1);
        boolean home = rocket.destination() == RocketDesign.Destination.RETURN_TO_EARTH;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int fromSize = (int) (height * MathHelper.lerp(p, 1.1f, 0.06f));
        int toSize = (int) (height * MathHelper.lerp(p * p, 0.03f, 0.75f));
        int fromX = (int) MathHelper.lerp(p, width * 0.15f, width * 0.05f) - fromSize / 2;
        int fromY = (int) MathHelper.lerp(p, height * 0.95f, height * 0.85f) - fromSize / 2;
        int toX = (int) (width * 0.62f) - toSize / 2;
        int toY = (int) (height * 0.42f) - toSize / 2;
        drawBody(context, !home, fromX, fromY, fromSize);
        drawBody(context, home, toX, toY, toSize);
        RenderSystem.disableBlend();

        int panel = 40;
        context.fill(0, height - panel, width, height, 0xC0000000);
        int left = (int) (384 * (1 - p));
        Text title = Text.translatable(home ? "flight.orbitcore.transfer.home" : "flight.orbitcore.transfer.moon");
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFFFF);
        context.drawText(textRenderer, Text.translatable("flight.orbitcore.transfer.distance", left), 10, height - panel + 8,
                OrbitStyle.ACCENT, false);
        context.drawText(textRenderer, Text.translatable("flight.orbitcore.skip"), 10, height - panel + 22, OrbitStyle.TEXT_DIM, false);
    }

    private void drawBody(DrawContext context, boolean earth, int x, int y, int size) {
        if (earth) {
            context.drawTexture(EARTH_DISK, x, y, size, size, 0, 0, 128, 128, 128, 128);
        } else {
            context.drawTexture(MOON, x, y, size, size, 0, 0, 64, 64, 512, 64);
        }
    }

    private void drawStarfield(DrawContext context, float heightShare) {
        context.fill(0, 0, width, height, 0xFF01020A);
        long now = System.currentTimeMillis();
        for (float[] star : STARS) {
            int x = (int) (star[0] * width);
            int y = (int) (star[1] * height * heightShare);
            float twinkle = 0.6f + 0.4f * MathHelper.sin(now / 400f * star[3] + star[2] * 10);
            int a = (int) (255 * star[2] * twinkle);
            context.fill(x, y, x + 1, y + 1, a << 24 | 0xFFFFFF);
        }
    }

    // ------------------------------------------------------------------ орбита

    private void drawOrbit(DrawContext context, RocketEntity rocket) {
        drawStarfield(context, 0.75f);
        long now = System.currentTimeMillis();
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
        boolean toMoon = rocket.destination() == RocketDesign.Destination.MOON_LANDING;
        Text deorbit = Text.translatable(toMoon ? "flight.orbitcore.burn_to_moon" : "flight.orbitcore.deorbit", left);
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
