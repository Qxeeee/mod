package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.entity.MeteorEntity;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.entity.RocketStageEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    // Ракета движется быстро: позиция уходит игрокам каждый такт, иначе полёт дёргается
    public static final EntityType<RocketEntity> ROCKET = Registry.register(Registries.ENTITY_TYPE, OrbitCore.id("rocket"),
            FabricEntityTypeBuilder.<RocketEntity>create(SpawnGroup.MISC, RocketEntity::new)
                    .dimensions(EntityDimensions.changing(1.0f, 4.0f))
                    .trackRangeChunks(32)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .fireImmune()
                    .build());
    public static final EntityType<RocketStageEntity> ROCKET_STAGE = Registry.register(Registries.ENTITY_TYPE, OrbitCore.id("rocket_stage"),
            FabricEntityTypeBuilder.<RocketStageEntity>create(SpawnGroup.MISC, RocketStageEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 2.0f))
                    .trackRangeChunks(32)
                    .trackedUpdateRate(2)
                    .forceTrackedVelocityUpdates(true)
                    .fireImmune()
                    .build());

    public static final EntityType<MeteorEntity> METEOR = Registry.register(Registries.ENTITY_TYPE, OrbitCore.id("meteor"),
            FabricEntityTypeBuilder.<MeteorEntity>create(SpawnGroup.MISC, MeteorEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 1.0f))
                    .trackRangeChunks(16)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .fireImmune()
                    .build());

    private ModEntities() {
    }

    public static void register() {
    }
}
