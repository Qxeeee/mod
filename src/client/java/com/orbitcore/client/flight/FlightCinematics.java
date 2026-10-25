package com.orbitcore.client.flight;

import com.orbitcore.client.screen.FlightScreen;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.FlightPhase;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Кинорежиссёр полёта. Пока игрок летит, прячет интерфейс, держит открытым экран полёта.
 * По умолчанию игрок смотрит из кабины ({@link CockpitView}), а в режиме «кино» режиссёр
 * для каждого кадра выбирает план камеры: общий у стола, низкий у двигателей,
 * взгляд снизу на уходящую ракету, погоня, отделение ступени, вход в атмосферу, парашют.
 */
public final class FlightCinematics {
    private static boolean active;
    private static boolean savedHudHidden;
    @Nullable
    private static RegistryKey<World> lastWorld;

    private FlightCinematics() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FlightCinematics::tick);
    }

    /** Ракета, в которой летит игрок, или null. */
    @Nullable
    public static RocketEntity rocket() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getVehicle() instanceof RocketEntity rocket && rocket.phase().inFlight()) {
            return rocket;
        }
        return null;
    }

    /** В каком мире игрок был на прошлом тике: при смене мира так видно, откуда он прилетел. */
    @Nullable
    public static RegistryKey<World> lastWorld() {
        return lastWorld;
    }

    private static void tick(MinecraftClient client) {
        lastWorld = client.world == null ? null : client.world.getRegistryKey();
        RocketEntity rocket = rocket();
        CockpitView.tick(client, rocket);
        if (rocket != null) {
            if (!active) {
                active = true;
                savedHudHidden = client.options.hudHidden;
            }
            client.options.hudHidden = true;
            if (client.currentScreen == null) {
                client.setScreen(new FlightScreen());
            }
        } else if (active) {
            active = false;
            client.options.hudHidden = savedHudHidden;
            if (client.currentScreen instanceof FlightScreen) {
                client.setScreen(null);
            }
        }
    }

    /** Открыть меню паузы поверх полёта; после него экран полёта откроется снова. */
    public static void openPauseMenu() {
        MinecraftClient.getInstance().setScreen(new GameMenuScreen(true));
    }

    // ------------------------------------------------------------------ камера

    @Nullable
    public static CameraShot cameraShot(float tickDelta) {
        RocketEntity rocket = rocket();
        if (rocket == null) {
            return null;
        }
        if (CockpitView.enabled()) {
            return CockpitView.shot(rocket, tickDelta);
        }
        FlightPhase phase = rocket.phase();
        if (phase == FlightPhase.SPACE || phase == FlightPhase.TRANSFER) {
            return null;
        }
        Vec3d r = rocket.getLerpedPos(tickDelta);
        double h = Math.max(1.5, rocket.design().height());
        float t = rocket.flightTick() + tickDelta;
        double base = rocket.baseY();
        CameraShot shot;
        float shake;

        switch (phase) {
            case COUNTDOWN -> {
                if (t < 100) {
                    // Общий план: стол и ракета целиком, камера медленно наезжает
                    double radius = Math.max(17, h * 1.15) - t * 0.04;
                    shot = CameraShot.lookAt(r.x + radius * 0.7, base + 2.5, r.z - radius * 0.7, r.x, base + h * 0.55, r.z);
                } else {
                    // Низкий план у двигателей, камера облетает ракету
                    double angle = Math.toRadians(-40 + (t - 100) * 0.35);
                    shot = CameraShot.lookAt(r.x + Math.sin(angle) * 7.5, base + 0.8, r.z - Math.cos(angle) * 7.5,
                            r.x, base + h * 0.4, r.z);
                }
                shake = t < FlightController.IGNITION_TICK ? 0 : 0.6f + (t - FlightController.IGNITION_TICK) * 0.02f;
            }
            case ASCENT -> {
                float sinceSeparation = t - rocket.separationTick();
                if (sinceSeparation >= 0 && sinceSeparation < 60) {
                    // Отделение ступени: смотрим вниз, как она уходит прочь
                    shot = CameraShot.lookAt(r.x + 5.5, r.y + 2, r.z - 6.5, r.x, r.y - 10, r.z);
                    shake = 1.2f * (1 - sinceSeparation / 60f);
                } else if (t < 70) {
                    // Отрыв от стола: камера на земле, ракета уходит вверх
                    double far = Math.max(11, h * 0.75);
                    shot = CameraShot.lookAt(r.x + far, base + 2, r.z - far, r.x, r.y + h * 0.5, r.z);
                    shake = 2.2f - t * 0.02f;
                } else if (t < 150) {
                    // Взгляд снизу вверх от края стола
                    shot = CameraShot.lookAt(r.x + 4.5, base + 0.6, r.z - 4.5, r.x, r.y + h * 0.3, r.z);
                    shake = 0.8f;
                } else {
                    // Погоня: камера снизу сбоку, медленно обходит ракету
                    double angle = Math.toRadians(30 + (t - 150) * 0.25);
                    shot = CameraShot.lookAt(r.x + Math.sin(angle) * 10, r.y - 5 - h * 0.2, r.z - Math.cos(angle) * 10,
                            r.x, r.y + h * 0.5, r.z);
                    shake = 0.5f;
                }
            }
            case REENTRY -> {
                if (rocket.parachute()) {
                    shot = CameraShot.lookAt(r.x + 11, r.y - 3, r.z - 11, r.x, r.y + 5, r.z);
                    shake = 0.15f;
                } else {
                    shot = CameraShot.lookAt(r.x + 6, r.y + 7, r.z - 6, r.x, r.y + 1, r.z);
                    shake = 1.4f;
                }
            }
            case DESCENT -> {
                shot = CameraShot.lookAt(r.x + 9, r.y + h + 4, r.z - 9, r.x, r.y, r.z);
                shake = 0.3f + rocket.throttle() * 0.8f;
            }
            default -> {
                return null;
            }
        }
        double time = (MinecraftClient.getInstance().world.getTime() + tickDelta) * 0.9;
        float yawShake = (float) (Math.sin(time * 1.7) + Math.sin(time * 4.3 + 1) * 0.5) * shake;
        float pitchShake = (float) (Math.cos(time * 2.1) + Math.sin(time * 5.1 + 2) * 0.5) * shake;
        return shot.shaken(yawShake, pitchShake);
    }

    // ------------------------------------------------------------------ субтитры

    @Nullable
    public static Text subtitle(RocketEntity rocket) {
        int t = rocket.flightTick();
        return switch (rocket.phase()) {
            case COUNTDOWN -> {
                if (t < 40) {
                    yield Text.translatable("flight.orbitcore.sub.ready");
                }
                if (t < 80) {
                    yield Text.translatable("flight.orbitcore.sub.masts");
                }
                int seconds = MathHelper.ceil((FlightController.COUNTDOWN_TICKS - t) / 20f);
                yield t < FlightController.IGNITION_TICK
                        ? Text.translatable("flight.orbitcore.sub.t_minus", seconds)
                        : Text.translatable("flight.orbitcore.sub.ignition", seconds);
            }
            case ASCENT -> {
                if (t - rocket.separationTick() < 60) {
                    yield Text.translatable("flight.orbitcore.sub.separation");
                }
                if (t < 60) {
                    yield Text.translatable("flight.orbitcore.sub.liftoff");
                }
                if (t < 140) {
                    yield Text.translatable("flight.orbitcore.sub.pitch");
                }
                yield altitudeKm(rocket) > 80 ? Text.translatable("flight.orbitcore.sub.space") : null;
            }
            case TRANSFER -> null;
            case DESCENT -> Text.translatable(rocket.autopilot() ? "flight.orbitcore.sub.descent_auto" : "flight.orbitcore.sub.descent");
            case REENTRY -> rocket.parachute()
                    ? Text.translatable("flight.orbitcore.sub.parachute")
                    : Text.translatable(t < 80 ? "flight.orbitcore.sub.reentry" : "flight.orbitcore.sub.plasma");
            default -> null;
        };
    }

    /** Высота для телеметрии, км: от стола до границы космоса на 100 км. */
    public static double altitudeKm(RocketEntity rocket) {
        double above = rocket.getY() - rocket.baseY();
        double scale = rocket.phase() == FlightPhase.REENTRY ? FlightController.REENTRY_ALTITUDE : FlightController.SPACE_ALTITUDE;
        return Math.max(0, above / scale * 100);
    }

    /** Скорость для телеметрии, км/с: растёт до орбитальной 7,8 км/с. */
    public static double speedKms(RocketEntity rocket) {
        double progress = MathHelper.clamp(altitudeKm(rocket) / 100, 0, 1);
        return rocket.phase() == FlightPhase.REENTRY
                ? (rocket.parachute() ? 0.008 : 7.8 * progress)
                : 7.8 * Math.pow(progress, 1.3);
    }
}
