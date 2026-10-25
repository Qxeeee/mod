package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.entity.RocketEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<RocketEntity> ROCKET = Registry.register(Registries.ENTITY_TYPE, OrbitCore.id("rocket"),
            FabricEntityTypeBuilder.<RocketEntity>create(SpawnGroup.MISC, RocketEntity::new)
                    .dimensions(EntityDimensions.changing(1.0f, 4.0f))
                    .trackRangeBlocks(160)
                    .trackedUpdateRate(10)
                    .fireImmune()
                    .build());

    private ModEntities() {
    }

    public static void register() {
    }
}
