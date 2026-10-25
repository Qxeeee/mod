package com.orbitcore.client.flight;

import com.orbitcore.client.sound.LoopSound;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.FlightPhase;
import com.orbitcore.rocket.RocketDesign;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Полёт от первого лица: камера у иллюминатора капсулы, кабина вокруг (её рисует
 * {@link com.orbitcore.client.screen.FlightScreen}), тряска на старте, рывок при
 * отделении ступени, перегрузка, гул корпуса и тоны связи с ЦУП.
 * Клавиша C переключает кабину и внешние кинокамеры.
 */
public final class CockpitView {
    /** Иллюминатор смотрит на север, на высоте 1,5 блока над низом капсулы. */
    private static final float WINDOW_YAW = 180.0f;
    private static final double CAPSULE_HEIGHT = 4.0;
    private static final Random RANDOM = Random.create();

    private static boolean enabled = true;
    @Nullable
    private static LoopSound rumble;
    @Nullable
    private static LoopSound plasma;
    private static boolean tracking;
    private static int lastSeparation;
    private static float jolt;
    private static float flash;
    private static int groan = 160;
    @Nullable
    private static String lastSubtitle;

    private CockpitView() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    /** Вспышка на весь экран после отделения ступени, 0–1. */
    public static float flash() {
        return flash;
    }

    /** Насколько трясёт кабину (в пикселях кадра). */
    public static float frameShake(RocketEntity rocket) {
        return shake(rocket, rocket.flightTick()) * 1.6f + jolt * 4;
    }

    /** Перегрузка для приборов, g. */
    public static float gForce(RocketEntity rocket) {
        int t = rocket.flightTick();
        return switch (rocket.phase()) {
            case COUNTDOWN -> 1.0f;
            case ASCENT -> {
                float sinceSeparation = t - rocket.separationTick();
                if (sinceSeparation >= 0 && sinceSeparation < 25) {
                    yield 0.2f;
                }
                yield 1.3f + 2.4f * MathHelper.clamp(t / 420f, 0, 1);
            }
            case REENTRY -> rocket.parachute() ? 1.1f : 1.5f + 3.5f * MathHelper.sin(MathHelper.clamp(t / 160f, 0, 1) * MathHelper.PI);
            case DESCENT -> 0.17f + rocket.throttle() * 0.5f;
            default -> 0.0f;
        };
    }

    // ------------------------------------------------------------------ камера

    @Nullable
    public static CameraShot shot(RocketEntity rocket, float tickDelta) {
        FlightPhase phase = rocket.phase();
        if (phase == FlightPhase.SPACE || phase == FlightPhase.TRANSFER) {
            return null;
        }
        Vec3d r = rocket.getLerpedPos(tickDelta);
        double h = Math.max(CAPSULE_HEIGHT, rocket.design().height());
        float t = rocket.flightTick() + tickDelta;
        double x = r.x;
        double y = r.y + h - CAPSULE_HEIGHT + 1.5;
        double z = r.z - (RocketDesign.RADIUS + 0.35);
        float pitch = switch (phase) {
            // На столе — на горизонт; на подъёме взгляд всё ниже, к уходящей Земле
            case COUNTDOWN -> 6;
            case ASCENT -> MathHelper.lerp(MathHelper.clamp(t / 320f, 0, 1), 8f, 46f);
            case REENTRY -> rocket.parachute() ? 32 + MathHelper.sin(t / 30f) * 4 : 14;
            case DESCENT -> 52;
            default -> 10;
        };
        float yaw = WINDOW_YAW + (phase == FlightPhase.REENTRY && rocket.parachute() ? MathHelper.sin(t / 47f) * 6 : 0);
        float shake = shake(rocket, t);
        double time = (MinecraftClient.getInstance().world.getTime() + tickDelta) * 1.3;
        float yawShake = (float) (Math.sin(time * 2.3) + Math.sin(time * 6.1 + 1) * 0.6) * shake * 0.6f;
        float pitchShake = (float) (Math.cos(time * 2.9) + Math.sin(time * 7.7 + 2) * 0.6) * shake * 0.6f + jolt * 7;
        return new CameraShot(x, y, z, yaw + yawShake, pitch + pitchShake);
    }

    private static float shake(RocketEntity rocket, float t) {
        return switch (rocket.phase()) {
            case COUNTDOWN -> t < FlightController.IGNITION_TICK ? 0 : 0.4f + (t - FlightController.IGNITION_TICK) * 0.025f;
            case ASCENT -> t < 90 ? 2.2f - t * 0.015f : Math.max(0.25f, 0.8f - (t - 90) * 0.002f);
            case REENTRY -> rocket.parachute() ? 0.1f : 1.6f;
            case DESCENT -> 0.15f + rocket.throttle() * 0.7f;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ звук и события

    /** Каждый тик клиента: гул кабины, плазма, отделение ступени, тоны связи. */
    public static void tick(MinecraftClient client, @Nullable RocketEntity rocket) {
        jolt *= 0.86f;
        flash *= 0.82f;
        if (rocket == null) {
            rumble = LoopSound.drive(rumble, ModSounds.FLIGHT_CABIN_RUMBLE, SoundCategory.NEUTRAL, 0);
            plasma = LoopSound.drive(plasma, ModSounds.FLIGHT_PLASMA, SoundCategory.NEUTRAL, 0);
            lastSubtitle = null;
            tracking = false;
            return;
        }
        float inside = enabled ? 1.0f : 0.4f;
        int t = rocket.flightTick();
        float rumbleVolume = switch (rocket.phase()) {
            case COUNTDOWN -> t < FlightController.IGNITION_TICK ? 0 : Math.min(0.8f, (t - FlightController.IGNITION_TICK) / 60f);
            case ASCENT -> 1.0f - 0.7f * (float) MathHelper.clamp(FlightCinematics.altitudeKm(rocket) / 100, 0, 1);
            case DESCENT -> rocket.throttle() * 0.8f;
            default -> 0;
        };
        rumble = LoopSound.drive(rumble, ModSounds.FLIGHT_CABIN_RUMBLE, SoundCategory.NEUTRAL, rumbleVolume * inside);
        boolean burning = rocket.phase() == FlightPhase.REENTRY && !rocket.parachute();
        plasma = LoopSound.drive(plasma, ModSounds.FLIGHT_PLASMA, SoundCategory.NEUTRAL, burning ? 0.8f * inside : 0);

        int separation = rocket.separationTick();
        if (tracking && separation != lastSeparation && separation > 0) {
            play(client, ModSounds.FLIGHT_SEPARATION, 1.0f, 1.0f);
            jolt = 1.0f;
            flash = 0.8f;
        }
        lastSeparation = separation;
        tracking = true;
        boolean stressed = rocket.phase() == FlightPhase.ASCENT || burning;
        if (stressed && --groan <= 0) {
            play(client, ModSounds.FLIGHT_HULL_GROAN, 0.5f * inside, 0.8f + RANDOM.nextFloat() * 0.3f);
            groan = 140 + RANDOM.nextInt(200);
        }
        // Каждая новая фраза ЦУП — с тоном Квиндар, как в эфире настоящих миссий
        Text subtitle = FlightCinematics.subtitle(rocket);
        String key = subtitle == null ? null
                : subtitle.getContent() instanceof TranslatableTextContent translatable ? translatable.getKey() : subtitle.getString();
        if (key != null && !key.equals(lastSubtitle)) {
            play(client, ModSounds.FLIGHT_QUINDAR_IN, 0.35f, 1.0f);
        }
        lastSubtitle = key;
    }

    private static void play(MinecraftClient client, SoundEvent sound, float volume, float pitch) {
        client.getSoundManager().play(new PositionedSoundInstance(sound.getId(), SoundCategory.NEUTRAL, volume, pitch,
                SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true));
    }
}
