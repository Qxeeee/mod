package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

/**
 * Виды урона мода. Сами типы описаны в data/orbitcore/damage_type,
 * сообщения о смерти — в переводах (death.attack.orbitcore.*).
 */
public final class ModDamageTypes {
    public static final RegistryKey<DamageType> VACUUM = key("vacuum");
    public static final RegistryKey<DamageType> RADIATION = key("radiation");
    public static final RegistryKey<DamageType> HEAT = key("heat");
    public static final RegistryKey<DamageType> COLD = key("cold");

    private ModDamageTypes() {
    }

    private static RegistryKey<DamageType> key(String name) {
        return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, OrbitCore.id(name));
    }

    public static DamageSource of(World world, RegistryKey<DamageType> type) {
        return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(type));
    }
}
