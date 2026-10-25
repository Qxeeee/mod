package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

/**
 * Звуки мода. Какие файлы играют и какие у них субтитры — в assets/orbitcore/sounds.json.
 */
public final class ModSounds {
    public static final SoundEvent SEALER_PRESSURIZE = register("block.sealer.pressurize");
    public static final SoundEvent SEALER_LEAK = register("block.sealer.leak");
    public static final SoundEvent AIRLOCK_OPEN = register("block.airlock_door.open");
    public static final SoundEvent AIRLOCK_CLOSE = register("block.airlock_door.close");
    public static final SoundEvent OXYGEN_REFILL = register("block.oxygen_station.refill");
    public static final SoundEvent SUIT_ALARM = register("item.spacesuit.alarm");
    public static final SoundEvent DOSIMETER_CLICK = register("item.dosimeter.click");
    public static final SoundEvent METEOR_INCOMING = register("event.meteor.incoming");
    public static final SoundEvent METEOR_IMPACT = register("event.meteor.impact");
    public static final SoundEvent MOONQUAKE = register("event.moonquake");
    public static final SoundEvent VOID_DRONE = register("ambient.void.drone");
    public static final SoundEvent VOID_SWELL = register("ambient.void.swell");
    public static final SoundEvent VOID_WHISPER = register("ambient.void.whisper");
    public static final SoundEvent VOID_CREAK = register("ambient.void.creak");
    public static final SoundEvent VOID_HEARTBEAT = register("ambient.void.heartbeat");
    public static final SoundEvent VOID_BREATH = register("ambient.void.breath");
    public static final SoundEvent VOID_KNOCK = register("ambient.void.knock");
    public static final SoundEvent VOID_RADIO_GHOST = register("ambient.void.radio_ghost");
    public static final SoundEvent ECHO_SIGNAL = register("echo.signal");
    public static final SoundEvent ECHO_HUM = register("echo.hum");
    public static final SoundEvent ECHO_STINGER = register("echo.stinger");
    public static final SoundEvent ECHO_STATIC = register("echo.static");
    public static final SoundEvent ECHO_CONTACT = register("echo.contact");
    public static final SoundEvent ECHO_CRYSTAL = register("echo.crystal");
    public static final SoundEvent FLIGHT_QUINDAR_IN = register("flight.quindar_in");
    public static final SoundEvent FLIGHT_QUINDAR_OUT = register("flight.quindar_out");
    public static final SoundEvent FLIGHT_CABIN_RUMBLE = register("flight.cabin_rumble");
    public static final SoundEvent FLIGHT_SEPARATION = register("flight.separation");
    public static final SoundEvent FLIGHT_PLASMA = register("flight.plasma");
    public static final SoundEvent FLIGHT_HULL_GROAN = register("flight.hull_groan");
    public static final SoundEvent ROVER_MOTOR = register("vehicle.rover.motor");

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        return Registry.register(Registries.SOUND_EVENT, OrbitCore.id(name), SoundEvent.of(OrbitCore.id(name)));
    }

    public static void register() {
    }
}
