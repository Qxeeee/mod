package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Генерация руд в обычном мире. Сами жилы описаны в data/orbitcore/worldgen.
 */
public final class ModWorldGen {
    public static final RegistryKey<PlacedFeature> ORE_BAUXITE = key("ore_bauxite");
    public static final RegistryKey<PlacedFeature> ORE_RUTILE = key("ore_rutile");

    private ModWorldGen() {
    }

    private static RegistryKey<PlacedFeature> key(String name) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, OrbitCore.id(name));
    }

    public static void register() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, ORE_BAUXITE);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, ORE_RUTILE);
    }
}
