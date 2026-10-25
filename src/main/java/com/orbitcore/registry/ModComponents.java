package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.survival.LifeSupportComponent;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;

/**
 * Компоненты Cardinal Components, прикреплённые к игрокам.
 * Подключается точкой входа {@code cardinal-components-entity} в fabric.mod.json.
 */
public final class ModComponents implements EntityComponentInitializer {
    public static final ComponentKey<LifeSupportComponent> LIFE_SUPPORT =
            ComponentRegistry.getOrCreate(OrbitCore.id("life_support"), LifeSupportComponent.class);

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        // После смерти доза обнуляется, а при выходе из Края — сохраняется
        registry.registerForPlayers(LIFE_SUPPORT, LifeSupportComponent::new, RespawnCopyStrategy.LOSSLESS_ONLY);
    }
}
