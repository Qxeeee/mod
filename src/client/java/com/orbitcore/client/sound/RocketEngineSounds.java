package com.orbitcore.client.sound;

import com.orbitcore.client.flight.CockpitView;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.FlightPhase;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Рёв ракетных двигателей. Звук привязан к самой ракете и летит вместе с ней:
 * сплошной шум с треском, без ритмичных «хлопков». Громкость — по тяге,
 * при входе в атмосферу вместо двигателей ревёт плазма.
 */
public final class RocketEngineSounds {
    private static final Map<Integer, EngineSound> ROAR = new HashMap<>();
    private static final Map<Integer, EngineSound> PLASMA = new HashMap<>();

    private RocketEngineSounds() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(RocketEngineSounds::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null) {
            ROAR.clear();
            PLASMA.clear();
            return;
        }
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof RocketEntity rocket) {
                ensure(client, ROAR, rocket, ModSounds.FLIGHT_ROCKET_ROAR, roarVolume(rocket));
                ensure(client, PLASMA, rocket, ModSounds.FLIGHT_PLASMA, plasmaVolume(rocket));
            }
        }
        ROAR.values().removeIf(MovingSoundInstance::isDone);
        PLASMA.values().removeIf(MovingSoundInstance::isDone);
    }

    private static void ensure(MinecraftClient client, Map<Integer, EngineSound> sounds, RocketEntity rocket, SoundEvent event, float volume) {
        EngineSound sound = sounds.get(rocket.getId());
        if (sound == null && volume > 0.02f) {
            sound = new EngineSound(rocket, event);
            sounds.put(rocket.getId(), sound);
            client.getSoundManager().play(sound);
        }
    }

    /** Тяга двигателей 0–1: от зажигания на столе до выключения на орбите и на посадке по газу. */
    static float roarVolume(RocketEntity rocket) {
        int t = rocket.flightTick();
        return switch (rocket.phase()) {
            case COUNTDOWN -> t < FlightController.IGNITION_TICK ? 0 : Math.min(1, (t - FlightController.IGNITION_TICK) / 50f);
            case ASCENT -> 1.0f;
            case DESCENT -> rocket.throttle() > 0.03f ? 0.25f + 0.75f * rocket.throttle() : 0;
            default -> 0;
        };
    }

    static float plasmaVolume(RocketEntity rocket) {
        return rocket.phase() == FlightPhase.REENTRY && !rocket.parachute() ? 0.9f : 0;
    }

    /** Звук, который следует за ракетой; экипаж в кабине слышит его глухо — через корпус. */
    private static final class EngineSound extends MovingSoundInstance {
        private final RocketEntity rocket;
        private final boolean plasma;
        private float level;

        EngineSound(RocketEntity rocket, SoundEvent event) {
            super(event, SoundCategory.NEUTRAL, SoundInstance.createRandom());
            this.rocket = rocket;
            this.plasma = event == ModSounds.FLIGHT_PLASMA;
            repeat = true;
            repeatDelay = 0;
            volume = 0.01f;
            pitch = 0.9f + random.nextFloat() * 0.1f;
            x = rocket.getX();
            y = rocket.getY();
            z = rocket.getZ();
        }

        @Override
        public void tick() {
            if (rocket.isRemoved()) {
                setDone();
                return;
            }
            float target = plasma ? plasmaVolume(rocket) : roarVolume(rocket);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.getVehicle() == rocket && CockpitView.enabled()) {
                target *= 0.25f;
            }
            level += (target - level) * 0.1f;
            volume = level * 3.0f;
            x = rocket.getX();
            y = rocket.getY();
            z = rocket.getZ();
            if (target <= 0 && level < 0.01f) {
                setDone();
            }
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }
    }
}
