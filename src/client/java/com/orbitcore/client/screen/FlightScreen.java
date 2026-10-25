package com.orbitcore.client.screen;

import com.orbitcore.OrbitCore;
import com.orbitcore.client.flight.CockpitView;
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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Экран полёта поверх мира: кинополосы, субтитры и телеметрия, а на орбите —
 * вид из иллюминатора на Землю. Мышь спрятана, пробел пропускает подъём.
 */
public class FlightScreen extends Screen {
    private static final Identifier ORBIT_VIEW = OrbitCore.id("textures/gui/flight/orbit_view.png");
    private static final Identifier EARTH_DISK = OrbitCore.id("textures/gui/flight/earth_disk.png");
    private static final Identifier MOON = OrbitCore.id("textures/gui/telescope/moon.png");
    private static final Identifier COCKPIT = OrbitCore.id("textures/gui/flight/cockpit.png");
    private static final float[][] STARS = stars();
    // Кабина: размер текстуры и экраны приборов — как в tools/textures/cockpit.py
    private static final int COCKPIT_W = 640;
    private static final int COCKPIT_H = 360;
    private static final int[][] SCREENS = {{52, 286, 188, 346}, {252, 282, 388, 350}, {452, 286, 588, 346}};
    private static final int CRT = 0xFF7CF0B0;
    private static final int CRT_AMBER = 0xFFFFC266;

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
        if (keyCode == GLFW.GLFW_KEY_C) {
            CockpitView.toggle();
            return true;
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
        if (CockpitView.enabled()) {
            renderCockpit(context, rocket);
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
        context.drawText(textRenderer, Text.translatable("flight.orbitcore.camera_hint"), 8, (barHeight() - 8) / 2, OrbitStyle.TEXT_DIM, false);
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
        drawTransferBackdrop(context, rocket);
        float p = MathHelper.clamp(rocket.flightTick() / (float) FlightController.TRANSFER_TICKS, 0, 1);
        boolean home = rocket.destination() == RocketDesign.Destination.RETURN_TO_EARTH;
        int panel = 40;
        context.fill(0, height - panel, width, height, 0xC0000000);
        int left = (int) (384 * (1 - p));
        Text title = Text.translatable(home ? "flight.orbitcore.transfer.home" : "flight.orbitcore.transfer.moon");
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFFFF);
        context.drawText(textRenderer, Text.translatable("flight.orbitcore.transfer.distance", left), 10, height - panel + 8,
                OrbitStyle.ACCENT, false);
        context.drawText(textRenderer, Text.translatable("flight.orbitcore.skip"), 10, height - panel + 22, OrbitStyle.TEXT_DIM, false);
    }

    /** Звёзды, уменьшающееся за спиной тело и растущее впереди. */
    private void drawTransferBackdrop(DrawContext context, RocketEntity rocket) {
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
    }

    private void drawBody(DrawContext context, boolean earth, int x, int y, int size) {
        if (earth) {
            context.drawTexture(EARTH_DISK, x, y, size, size, 0, 0, 256, 256, 256, 256);
        } else {
            context.drawTexture(MOON, x, y, size, size, 0, 0, 128, 128, 1024, 128);
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
        drawOrbitBackdrop(context);
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

    /** Звёзды, Земля с высоты 400 км и Солнце. */
    private void drawOrbitBackdrop(DrawContext context) {
        drawStarfield(context, 0.6f);
        long now = System.currentTimeMillis();
        // Земля с высоты 400 км: горизонт с тонкой кромкой атмосферы на трети кадра
        int viewHeight = Math.max(height, width * 720 / 1280);
        int viewWidth = viewHeight * 1280 / 720;
        float drift = MathHelper.sin(now / 9000f) * 4;
        int ex = (width - viewWidth) / 2;
        int ey = (int) (height * 0.38f - viewHeight * 0.33f + drift);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.drawTexture(ORBIT_VIEW, ex, ey, viewWidth, viewHeight, 0, 0, 1280, 720, 1280, 720);
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
    }

    // ------------------------------------------------------------------ кабина

    private record Line(Text text, int color) {
    }

    /** Полёт от первого лица: мир за иллюминатором, кабина вокруг, показания на экранах приборов. */
    private void renderCockpit(DrawContext context, RocketEntity rocket) {
        FlightPhase phase = rocket.phase();
        long now = System.currentTimeMillis();
        drawWindowView(context, rocket, phase, now);
        float flash = CockpitView.flash();
        if (flash > 0.02f) {
            context.fill(0, 0, width, height, (int) (flash * 220) << 24 | 0xFFF6E0);
        }

        // Кабина целиком закрывает экран; при тряске она дрожит вместе с камерой
        float s = Math.max(width / (float) COCKPIT_W, height / (float) COCKPIT_H) * 1.03f;
        float shake = CockpitView.frameShake(rocket);
        float jx = (float) (Math.sin(now / 21.0) + Math.sin(now / 6.3) * 0.5) * shake;
        float jy = (float) (Math.cos(now / 17.0) + Math.sin(now / 5.1) * 0.5) * shake;
        float ox = (width - COCKPIT_W * s) / 2 + jx;
        float oy = (height - COCKPIT_H * s) / 2 + jy;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(ox, oy, 0);
        matrices.scale(s, s, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.drawTexture(COCKPIT, 0, 0, 0, 0, COCKPIT_W, COCKPIT_H, COCKPIT_W, COCKPIT_H);
        drawCockpitLights(context, rocket, now);
        matrices.pop();

        List<List<Line>> screens = cockpitLines(rocket);
        for (int i = 0; i < SCREENS.length; i++) {
            int[] r = SCREENS[i];
            int x = (int) (ox + r[0] * s) + 3;
            int y = (int) (oy + r[1] * s) + 3;
            int bottom = (int) (oy + r[3] * s) - 8;
            int maxWidth = (int) ((r[2] - r[0]) * s) - 6;
            for (Line line : screens.get(i)) {
                if (y > bottom) {
                    break;
                }
                drawFitted(context, line.text(), x, y, maxWidth, line.color());
                y += 10;
            }
        }
        if (phase == FlightPhase.DESCENT) {
            // Газ двигателя — полоса внизу среднего экрана
            int[] r = SCREENS[1];
            int bx = (int) (ox + r[0] * s) + 3;
            int bw = (int) ((r[2] - r[0]) * s) - 6;
            int by = (int) (oy + r[3] * s) - 7;
            context.fill(bx, by, bx + bw, by + 4, 0xFF0A2018);
            context.fill(bx, by, bx + (int) (bw * rocket.throttle()), by + 4, CRT_AMBER);
            if (landingDanger(rocket) && (now / 300) % 2 == 0) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("flight.orbitcore.landing.too_fast"), width / 2,
                        (int) (oy + 222 * s), OrbitStyle.RED);
            }
        }

        float g = CockpitView.gForce(rocket);
        if (g > 2.2f) {
            vignette(context, MathHelper.clamp((g - 2.2f) / 2.8f, 0, 0.85f));
        }
        Text subtitle = FlightCinematics.subtitle(rocket);
        if (subtitle != null) {
            context.drawCenteredTextWithShadow(textRenderer, subtitle, width / 2, (int) (oy + 236 * s), 0xFFFFFFFF);
        }
    }

    /** Что видно в иллюминатор поверх мира: чернеющее небо, Землю с орбиты, плазму. */
    private void drawWindowView(DrawContext context, RocketEntity rocket, FlightPhase phase, long now) {
        if (phase == FlightPhase.SPACE) {
            drawOrbitBackdrop(context);
        } else if (phase == FlightPhase.TRANSFER) {
            drawTransferBackdrop(context, rocket);
        } else if (phase == FlightPhase.ASCENT) {
            double km = FlightCinematics.altitudeKm(rocket);
            float dark = (float) MathHelper.clamp((km - 25) / 60, 0, 1);
            if (dark > 0) {
                context.fillGradient(0, 0, width, height, (int) (dark * 245) << 24 | 0x01020A, (int) (dark * 160) << 24 | 0x01020A);
            }
            // У границы космоса за окном уже Земля целиком: кромка атмосферы и облака
            float orbit = (float) MathHelper.clamp((km - 70) / 30, 0, 1);
            if (orbit > 0) {
                RenderSystem.setShaderColor(1, 1, 1, orbit);
                drawOrbitBackdrop(context);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
        } else if (phase == FlightPhase.REENTRY && !rocket.parachute()) {
            // Сверху ещё космос, вокруг капсулы — раскалённая плазма
            // Плазма закрывает обзор: за стеклом почти ничего не видно, кроме огня
            context.fill(0, 0, width, height, 0xD8080404);
            float flicker = 0.75f + 0.25f * MathHelper.sin(now / 45f) * MathHelper.sin(now / 17f);
            float heat = 0.8f + 0.2f * MathHelper.sin(MathHelper.clamp(rocket.flightTick() / 160f, 0, 1) * MathHelper.PI);
            int alpha = (int) (255 * flicker * heat);
            context.fillGradient(0, 0, width, height, alpha / 2 << 24 | 0xFFB040, alpha << 24 | 0xFF6010);
            // Языки плазмы бегут вверх по стеклу
            for (int i = 0; i < 26; i++) {
                long seed = i * 7919L + now / 70;
                int x = (int) (Math.floorMod(seed * 2654435761L, 1000) / 1000f * width);
                int len = height / 6 + (int) (Math.floorMod(seed * 40503L, 100) / 100f * height / 3);
                int y = height - (int) ((now / 3 + i * 97) % (height + len));
                context.fillGradient(x, y, x + 2 + i % 3, y + len, 0x00FFE070, (int) (230 * heat) << 24 | 0xFFF2B0);
            }
        }
    }

    /** Лампочки на пульте: спокойно перемигиваются, а в плазме верхние горят тревогой. */
    private void drawCockpitLights(DrawContext context, RocketEntity rocket, long now) {
        boolean alarm = rocket.phase() == FlightPhase.REENTRY && !rocket.parachute() || landingDanger(rocket);
        for (int i = 0; i < 25; i++) {
            int x = 60 + 22 * i;
            boolean on = (now / (280 + i * 37) + i) % 3 != 0;
            int color = i % 7 == 3 ? 0xFFFF5040 : i % 5 == 1 ? 0xFFFFC040 : 0xFF50F090;
            context.fill(x + 1, 355, x + 9, 357, on ? color : 0xFF202224);
        }
        for (int i = 0; i < 12; i++) {
            int x = 140 + 30 * i;
            int color;
            if (alarm && i % 2 == 0) {
                color = (now / 200) % 2 == 0 ? 0xFFFF3020 : 0xFF401010;
            } else {
                color = (now / 500 + i * 3) % 5 != 0 ? 0xFF3A6A90 : 0xFF181C22;
            }
            context.fill(x + 3, 8, x + 15, 16, color);
        }
    }

    /** Строки для трёх экранов: слева высота и скорость, в центре главное, справа перегрузка и подсказки. */
    private List<List<Line>> cockpitLines(RocketEntity rocket) {
        List<Line> left = new ArrayList<>();
        List<Line> center = new ArrayList<>();
        List<Line> right = new ArrayList<>();
        FlightPhase phase = rocket.phase();
        int t = rocket.flightTick();
        String g = String.format("%.1f", CockpitView.gForce(rocket));
        Text destination = Text.translatable(rocket.destination().translationKey());
        switch (phase) {
            case SPACE -> {
                String altitude = String.format("%.0f", 400 + MathHelper.sin(t / 80f) * 6);
                left.add(new Line(Text.translatable("cockpit.orbitcore.orbit", altitude), CRT));
                left.add(new Line(Text.translatable("cockpit.orbitcore.speed", "7.80"), CRT));
                center.add(new Line(Text.translatable("cockpit.orbitcore.burn", Math.max(0, (FlightController.ORBIT_TICKS - t) / 20)), CRT_AMBER));
                center.add(new Line(destination, CRT));
                right.add(new Line(Text.translatable("cockpit.orbitcore.g", "0.0"), CRT));
                right.add(new Line(Text.translatable("cockpit.orbitcore.maneuver"), CRT_AMBER));
            }
            case TRANSFER -> {
                float p = MathHelper.clamp(t / (float) FlightController.TRANSFER_TICKS, 0, 1);
                left.add(new Line(Text.translatable("cockpit.orbitcore.transfer", (int) (384 * (1 - p))), CRT));
                center.add(new Line(destination, CRT_AMBER));
                right.add(new Line(Text.translatable("cockpit.orbitcore.g", "0.0"), CRT));
                right.add(new Line(Text.translatable("cockpit.orbitcore.skip"), CRT_AMBER));
            }
            case DESCENT -> {
                int ground = client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, rocket.getBlockX(), rocket.getBlockZ());
                double altitude = Math.max(0, rocket.getY() - ground);
                left.add(new Line(Text.translatable("cockpit.orbitcore.alt_m", String.format("%.0f", altitude)), CRT));
                left.add(new Line(Text.translatable("cockpit.orbitcore.vspeed", String.format("%.1f", rocket.verticalSpeed() * 20)),
                        landingDanger(rocket) ? OrbitStyle.RED : CRT));
                center.add(new Line(Text.translatable("cockpit.orbitcore.dv", (int) rocket.landingDeltaV()),
                        rocket.landingDeltaV() < 150 ? OrbitStyle.RED : CRT_AMBER));
                right.add(new Line(Text.translatable(rocket.autopilot() ? "cockpit.orbitcore.auto" : "cockpit.orbitcore.manual"), CRT));
                if (!rocket.design().has(RocketPart.Kind.LEGS)) {
                    right.add(new Line(Text.translatable("cockpit.orbitcore.no_legs"), OrbitStyle.RED));
                }
            }
            default -> {
                left.add(new Line(Text.translatable("cockpit.orbitcore.alt", String.format("%.1f", FlightCinematics.altitudeKm(rocket))), CRT));
                left.add(new Line(Text.translatable("cockpit.orbitcore.speed", String.format("%.2f", FlightCinematics.speedKms(rocket))), CRT));
                if (phase == FlightPhase.COUNTDOWN) {
                    int seconds = MathHelper.ceil((FlightController.COUNTDOWN_TICKS - t) / 20f);
                    center.add(new Line(t < FlightController.IGNITION_TICK ? Text.translatable("cockpit.orbitcore.t_minus", seconds)
                            : Text.translatable("cockpit.orbitcore.ignition"), CRT_AMBER));
                } else if (phase == FlightPhase.ASCENT) {
                    center.add(new Line(Text.translatable("cockpit.orbitcore.thrust"), CRT_AMBER));
                } else if (rocket.parachute()) {
                    center.add(new Line(Text.translatable("cockpit.orbitcore.parachute"), CRT));
                } else {
                    float heat = MathHelper.sin(MathHelper.clamp(t / 160f, 0, 1) * MathHelper.PI);
                    center.add(new Line(Text.translatable("cockpit.orbitcore.heat", (int) (300 + 1350 * heat)), OrbitStyle.RED));
                }
                center.add(new Line(destination, CRT));
                right.add(new Line(Text.translatable("cockpit.orbitcore.g", g), CockpitView.gForce(rocket) > 3.5f ? CRT_AMBER : CRT));
                right.add(new Line(Text.translatable("cockpit.orbitcore.stages", rocket.design().stages().size()), CRT));
                if (phase != FlightPhase.REENTRY) {
                    right.add(new Line(Text.translatable("cockpit.orbitcore.skip"), CRT_AMBER));
                }
            }
        }
        right.add(new Line(Text.translatable("cockpit.orbitcore.camera"), 0xFF6E8C80));
        return List.of(left, center, right);
    }

    private boolean landingDanger(RocketEntity rocket) {
        if (rocket.phase() != FlightPhase.DESCENT || client == null || client.world == null) {
            return false;
        }
        int ground = client.world.getTopY(Heightmap.Type.MOTION_BLOCKING, rocket.getBlockX(), rocket.getBlockZ());
        double altitude = Math.max(0, rocket.getY() - ground);
        return rocket.verticalSpeed() * 20 < -FlightController.SAFE_TOUCHDOWN * 20 && altitude < 30;
    }

    /** Текст, ужатый по ширине экрана прибора, если не помещается. */
    private void drawFitted(DrawContext context, Text text, int x, int y, int maxWidth, int color) {
        int w = textRenderer.getWidth(text);
        if (w <= maxWidth || maxWidth <= 0) {
            context.drawText(textRenderer, text, x, y, color, false);
            return;
        }
        float k = maxWidth / (float) w;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y + (1 - k) * 4, 0);
        matrices.scale(k, k, 1);
        context.drawText(textRenderer, text, 0, 0, color, false);
        matrices.pop();
    }

    /** Перегрузка: в глазах темнеет по краям. */
    private void vignette(DrawContext context, float strength) {
        int band = (int) (Math.min(width, height) * (0.12f + 0.25f * strength));
        int alpha = (int) (235 * strength);
        context.fillGradient(0, 0, width, band, alpha << 24, 0);
        context.fillGradient(0, height - band, width, height, 0, alpha << 24);
        for (int i = 0; i < band; i += 2) {
            int a = (int) (alpha * (1 - i / (float) band));
            context.fill(i, 0, i + 2, height, a << 24);
            context.fill(width - i - 2, 0, width - i, height, a << 24);
        }
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
