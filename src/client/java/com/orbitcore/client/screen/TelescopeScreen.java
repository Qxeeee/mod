package com.orbitcore.client.screen;

import com.orbitcore.OrbitCore;
import com.orbitcore.client.network.OrbitClientNetworking;
import com.orbitcore.client.science.ClientSpaceProgram;
import com.orbitcore.science.Observatory;
import com.orbitcore.science.ObservationTarget;
import com.orbitcore.science.SpaceProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Окуляр телескопа. Небо — развёртка азимут/высота (см. {@link ObservationTarget}).
 * Игрок наводится (WASD, правая кнопка мыши), увеличивает (колесо) и держит цель
 * в перекрестии, пока идёт выдержка (левая кнопка или пробел). Изображение слегка
 * дрожит из-за атмосферы — чем больше увеличение, тем сильнее.
 */
public class TelescopeScreen extends Screen {
    private static final Identifier MOON = OrbitCore.id("textures/gui/telescope/moon.png");
    private static final Identifier EYEPIECE = OrbitCore.id("textures/gui/telescope/eyepiece.png");
    private static final double BASE_FOV = 600;
    private static final float MAX_ZOOM = 4.0f;
    private static final float EXPOSURE_SECONDS = 3.0f;
    private static final double MOON_SIZE = 46;
    private static final double MAX_DISTANCE_SQ = 8 * 8;
    private static final Star[] STARS = generateStars();

    private static final int ACCENT = OrbitStyle.ACCENT;
    private static final int TEXT = OrbitStyle.TEXT;
    private static final int TEXT_DIM = OrbitStyle.TEXT_DIM;
    private static final int GOLD = OrbitStyle.GOLD;

    private final BlockPos telescope;
    private final Set<ObservationTarget> photographed = new HashSet<>();
    private double viewAz;
    private double viewAlt;
    private float zoom = 1.0f;
    private float exposure;
    private float flash;
    private boolean shutterHeld;
    @Nullable
    private ObservationTarget locked;
    private long lastFrame;
    private float time;

    // Пересчитываются каждый кадр
    private int cx;
    private int cy;
    private int radius;
    private double ppu;
    private double jitterAz;
    private double jitterAlt;

    public TelescopeScreen(BlockPos telescope) {
        super(Text.translatable("block.orbitcore.telescope"));
        this.telescope = telescope;
    }

    @Override
    protected void init() {
        if (lastFrame == 0 && client != null && client.world != null) {
            // Начинаем рядом с Луной, но не на ней — её ещё нужно найти
            long t = client.world.getTimeOfDay();
            viewAz = ObservationTarget.MOON.azimuthAt(t) - 260;
            viewAlt = Math.max(150, ObservationTarget.MOON.altitudeAt(t) - 120);
        }
        lastFrame = System.nanoTime();
    }

    /** Навести окуляр на цель (используется фототуром для скриншотов). */
    public void aimAt(ObservationTarget target, long worldTime, float zoom) {
        viewAz = target.azimuthAt(worldTime);
        viewAlt = target.altitudeAt(worldTime);
        this.zoom = MathHelper.clamp(zoom, 1.0f, MAX_ZOOM);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        if (client == null || client.player == null
                || client.player.squaredDistanceTo(telescope.toCenterPos()) > MAX_DISTANCE_SQ) {
            close();
        }
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            shutterHeld = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            shutterHeld = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && ppu > 0) {
            viewAz -= deltaX / ppu;
            viewAlt += deltaY / ppu;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        zoom = MathHelper.clamp(zoom * (amount > 0 ? 1.25f : 0.8f), 1.0f, MAX_ZOOM);
        return true;
    }

    private boolean keyDown(int key) {
        return InputUtil.isKeyPressed(client.getWindow().getHandle(), key);
    }

    private void handleKeys(float dt) {
        double speed = 260 / zoom * dt;
        if (keyDown(GLFW.GLFW_KEY_A) || keyDown(GLFW.GLFW_KEY_LEFT)) viewAz -= speed;
        if (keyDown(GLFW.GLFW_KEY_D) || keyDown(GLFW.GLFW_KEY_RIGHT)) viewAz += speed;
        if (keyDown(GLFW.GLFW_KEY_W) || keyDown(GLFW.GLFW_KEY_UP)) viewAlt += speed;
        if (keyDown(GLFW.GLFW_KEY_S) || keyDown(GLFW.GLFW_KEY_DOWN)) viewAlt -= speed;
        viewAz = MathHelper.floorMod(viewAz, ObservationTarget.SKY_WIDTH);
        viewAlt = MathHelper.clamp(viewAlt, 40, ObservationTarget.SKY_HEIGHT);
    }

    // ------------------------------------------------------------------ кадр

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        time += dt;
        handleKeys(dt);

        long worldTime = client.world.getTimeOfDay();
        boolean night = ObservationTarget.isNight(worldTime);
        SpaceProgram program = ClientSpaceProgram.get();
        long day = Observatory.day(client.world);

        radius = (int) (Math.min(width, height) * 0.46);
        cx = width / 2;
        cy = height / 2;
        ppu = 2.0 * radius / (BASE_FOV / zoom);
        // Атмосферное дрожание изображения
        double seeing = 1.2 + zoom * 1.1;
        jitterAz = (Math.sin(time * 1.7) + 0.6 * Math.sin(time * 4.3 + 1.1)) * seeing;
        jitterAlt = (Math.cos(time * 1.3) + 0.5 * Math.sin(time * 3.7 + 2.0)) * seeing;

        context.fill(0, 0, width, height, 0xFF000000);
        context.enableScissor(cx - radius, cy - radius, cx + radius, cy + radius);
        drawSky(context, night);
        drawStars(context);
        drawConstellations(context, program, day);
        if (night) {
            drawMoon(context, worldTime);
        }
        drawHorizon(context);
        context.disableScissor();
        context.drawTexture(EYEPIECE, cx - radius, cy - radius, radius * 2, radius * 2, 0, 0, 256, 256, 256, 256);

        updateLock(program, day, worldTime, night, dt);
        drawReticle(context);
        if (flash > 0) {
            int alpha = (int) (Math.min(1, flash) * 200);
            context.fill(0, 0, width, height, alpha << 24 | 0xFFFFFF);
            flash -= dt * 2.5f;
        }
        drawHud(context, program, day, night);
    }

    private void drawSky(DrawContext context, boolean night) {
        int top = night ? 0xFF02040C : 0xFF4A6FA8;
        int bottom = night ? 0xFF0B1630 : 0xFF9DB9E0;
        context.fillGradient(cx - radius, cy - radius, cx + radius, cy + radius, top, bottom);
    }

    private void drawStars(DrawContext context) {
        for (Star star : STARS) {
            double[] p = project(star.az, star.alt);
            if (!insideEyepiece(p[0], p[1])) {
                continue;
            }
            float twinkle = 0.75f + 0.25f * MathHelper.sin(time * star.speed + star.phase);
            int alpha = (int) (255 * MathHelper.clamp(star.brightness * twinkle, 0.15f, 1f));
            int size = star.brightness > 0.85f && zoom > 1.5f ? 2 : 1;
            int x = (int) p[0];
            int y = (int) p[1];
            context.fill(x, y, x + size, y + size, alpha << 24 | star.color);
        }
    }

    private void drawConstellations(DrawContext context, SpaceProgram program, long day) {
        for (ObservationTarget target : ObservationTarget.values()) {
            if (target == ObservationTarget.MOON) {
                continue;
            }
            int glow = observedOrTaken(target, program, day) ? 0xFFF2C66D : 0xFFE8F0FF;
            for (int[] star : target.stars()) {
                double[] p = project(target.azimuthAt(0) + star[0] * 0.5, target.altitudeAt(0) + star[1] * 0.5);
                if (!insideEyepiece(p[0], p[1])) {
                    continue;
                }
                int size = Math.max(2, (int) (zoom * 1.2));
                int x = (int) p[0] - size / 2;
                int y = (int) p[1] - size / 2;
                context.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0x40000000 | (glow & 0xFFFFFF));
                context.fill(x, y, x + size, y + size, glow);
            }
        }
    }

    private void drawMoon(DrawContext context, long worldTime) {
        ObservationTarget moon = ObservationTarget.MOON;
        double[] p = project(moon.azimuthAt(worldTime), moon.altitudeAt(worldTime));
        int size = (int) (MOON_SIZE * ppu);
        int x = (int) p[0] - size / 2;
        int y = (int) p[1] - size / 2;
        int phase = client.world.getMoonPhase();
        context.drawTexture(MOON, x, y, size, size, phase * 64, 0, 64, 64, 512, 64);
    }

    /** Тёмная полоса леса у горизонта. */
    private void drawHorizon(DrawContext context) {
        for (int sx = cx - radius; sx < cx + radius; sx += 2) {
            double az = viewAz + (sx - cx) / ppu;
            double treeline = 18 + 22 * noise(az * 0.02) + 10 * noise(az * 0.11 + 7);
            int top = (int) project(az, treeline)[1];
            if (top < cy + radius) {
                context.fill(sx, Math.max(top, cy - radius), sx + 2, cy + radius, 0xFF03050A);
            }
        }
    }

    private void updateLock(SpaceProgram program, long day, long worldTime, boolean night, float dt) {
        ObservationTarget best = null;
        double bestDistance = radius * 0.14;
        if (night) {
            for (ObservationTarget target : ObservationTarget.values()) {
                if (observedOrTaken(target, program, day)) {
                    continue;
                }
                double[] p = project(target.azimuthAt(worldTime), target.altitudeAt(worldTime));
                double distance = Math.hypot(p[0] - cx, p[1] - cy);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = target;
                }
            }
        }
        if (best != locked && best != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.6f, 0.4f));
        }
        locked = best;

        boolean shutter = shutterHeld || keyDown(GLFW.GLFW_KEY_SPACE);
        if (locked != null && zoom >= locked.minZoom() && shutter) {
            exposure += dt / EXPOSURE_SECONDS;
            if (exposure >= 1) {
                takePhoto(locked);
            }
        } else {
            exposure = Math.max(0, exposure - dt * 1.5f);
        }
    }

    private void takePhoto(ObservationTarget target) {
        exposure = 0;
        flash = 1;
        photographed.add(target);
        locked = null;
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0f));
        OrbitClientNetworking.sendObservation(telescope, target.id());
    }

    private void drawReticle(DrawContext context) {
        int color = locked != null ? 0xC04FD1E8 : 0x80DCE8F5;
        int gap = 6;
        int arm = (int) (radius * 0.14);
        context.fill(cx - arm, cy, cx - gap, cy + 1, color);
        context.fill(cx + gap, cy, cx + arm, cy + 1, color);
        context.fill(cx, cy - arm, cx + 1, cy - gap, color);
        context.fill(cx, cy + gap, cx + 1, cy + arm, color);
        // Кольцо выдержки
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2 * i / segments - Math.PI / 2;
            int x = cx + (int) (Math.cos(a) * arm);
            int y = cy + (int) (Math.sin(a) * arm);
            boolean filled = i < exposure * segments;
            context.fill(x, y, x + (filled ? 2 : 1), y + (filled ? 2 : 1), filled ? 0xFF4FD1E8 : 0x50DCE8F5);
        }
    }

    private void drawHud(DrawContext context, SpaceProgram program, long day, boolean night) {
        context.drawText(textRenderer, title, 10, 10, ACCENT, false);
        context.drawText(textRenderer, Text.translatable("gui.orbitcore.telescope.zoom", String.format("%.1f", zoom)), 10, 22, TEXT_DIM, false);

        int y = 10;
        int right = width - 10;
        Text header = Text.translatable("gui.orbitcore.telescope.targets");
        context.drawText(textRenderer, header, right - textRenderer.getWidth(header), y, TEXT, false);
        for (ObservationTarget target : ObservationTarget.values()) {
            y += 12;
            boolean done = observedOrTaken(target, program, day);
            Text line = Text.literal(done ? "✔ " : "○ ").append(Text.translatable(target.translationKey()));
            context.drawText(textRenderer, line, right - textRenderer.getWidth(line), y, done ? GOLD : TEXT_DIM, false);
        }

        Text status;
        int statusColor = TEXT;
        if (!night) {
            status = Text.translatable("gui.orbitcore.telescope.dawn");
            statusColor = OrbitStyle.RED;
        } else if (locked == null) {
            status = Text.translatable("gui.orbitcore.telescope.search");
            statusColor = TEXT_DIM;
        } else if (zoom < locked.minZoom()) {
            status = Text.translatable("gui.orbitcore.telescope.zoom_in", Text.translatable(locked.translationKey()));
            statusColor = GOLD;
        } else {
            status = Text.translatable("gui.orbitcore.telescope.locked", Text.translatable(locked.translationKey()));
            statusColor = ACCENT;
        }
        context.drawCenteredTextWithShadow(textRenderer, status, cx, cy + radius + 4 > height - 24 ? height - 34 : cy + radius + 4, statusColor);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.orbitcore.telescope.controls"), cx, height - 14, TEXT_DIM);
    }

    // ------------------------------------------------------------------ помощники

    private boolean observedOrTaken(ObservationTarget target, SpaceProgram program, long day) {
        return photographed.contains(target) || program.observedToday(target, day);
    }

    /** Экранные координаты точки неба с учётом дрожания. */
    private double[] project(double az, double alt) {
        double dx = MathHelper.floorMod(az - viewAz + ObservationTarget.SKY_WIDTH / 2.0, ObservationTarget.SKY_WIDTH)
                - ObservationTarget.SKY_WIDTH / 2.0;
        return new double[]{cx + (dx + jitterAz) * ppu, cy - (alt - viewAlt + jitterAlt) * ppu};
    }

    private boolean insideEyepiece(double x, double y) {
        return Math.hypot(x - cx, y - cy) < radius;
    }

    private static double noise(double x) {
        return 0.5 + 0.5 * (Math.sin(x) * 0.6 + Math.sin(x * 2.3 + 1.7) * 0.3 + Math.sin(x * 5.1 + 0.3) * 0.1);
    }

    private record Star(float az, float alt, float brightness, float phase, float speed, int color) {
    }

    private static Star[] generateStars() {
        Random random = new Random(4242);
        int[] colors = {0xFFFFFF, 0xDDE8FF, 0xFFF1D8, 0xCFE0FF};
        Star[] stars = new Star[1600];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star(random.nextFloat() * ObservationTarget.SKY_WIDTH,
                    random.nextFloat() * ObservationTarget.SKY_HEIGHT,
                    (float) Math.pow(random.nextFloat(), 3) * 0.9f + 0.1f,
                    random.nextFloat() * 6.28f, 1.5f + random.nextFloat() * 4f,
                    colors[random.nextInt(colors.length)]);
        }
        return stars;
    }
}
