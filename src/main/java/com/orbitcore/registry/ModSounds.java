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

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        return Registry.register(Registries.SOUND_EVENT, OrbitCore.id(name), SoundEvent.of(OrbitCore.id(name)));
    }

    public static void register() {
    }
}
