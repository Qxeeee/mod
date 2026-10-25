package com.orbitcore.world;

import com.orbitcore.OrbitCore;
import com.orbitcore.science.CelestialBody;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Миры мода и физика в них. Сам мир Луны описан в data/orbitcore/dimension.
 */
public final class ModDimensions {
    public static final RegistryKey<World> MOON = RegistryKey.of(RegistryKeys.WORLD, OrbitCore.id("moon"));
    public static final double MOON_GRAVITY = 0.17;

    private ModDimensions() {
    }

    /** Во сколько раз сила тяжести в мире меньше земной. */
    public static double gravity(@Nullable World world) {
        return world != null && world.getRegistryKey() == MOON ? MOON_GRAVITY : 1.0;
    }

    /** Есть ли в мире воздух, которым можно дышать. */
    public static boolean hasAir(World world) {
        return world.getRegistryKey() != MOON;
    }

    /** Небесное тело, на котором находится мир. */
    public static CelestialBody body(World world) {
        return world.getRegistryKey() == MOON ? CelestialBody.MOON : CelestialBody.EARTH;
    }

    public static RegistryKey<World> worldOf(CelestialBody body) {
        return body == CelestialBody.MOON ? MOON : World.OVERWORLD;
    }
}
