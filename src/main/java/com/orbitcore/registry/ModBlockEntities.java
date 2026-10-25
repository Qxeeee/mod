package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.block.entity.CoalGeneratorBlockEntity;
import com.orbitcore.block.entity.ElectrolyzerBlockEntity;
import com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import team.reborn.energy.api.EnergyStorage;

public final class ModBlockEntities {
    public static final BlockEntityType<MetallurgicalFurnaceBlockEntity> METALLURGICAL_FURNACE =
            register("metallurgical_furnace", MetallurgicalFurnaceBlockEntity::new, ModBlocks.METALLURGICAL_FURNACE);
    public static final BlockEntityType<CoalGeneratorBlockEntity> COAL_GENERATOR =
            register("coal_generator", CoalGeneratorBlockEntity::new, ModBlocks.COAL_GENERATOR);
    public static final BlockEntityType<ElectrolyzerBlockEntity> ELECTROLYZER =
            register("electrolyzer", ElectrolyzerBlockEntity::new, ModBlocks.ELECTROLYZER);

    private ModBlockEntities() {
    }

    private static <T extends BlockEntity> BlockEntityType<T> register(
            String name, FabricBlockEntityTypeBuilder.Factory<T> factory, Block block) {
        return Registry.register(Registries.BLOCK_ENTITY_TYPE, OrbitCore.id(name),
                FabricBlockEntityTypeBuilder.create(factory, block).build());
    }

    public static void register() {
        // Энергия доступна с любой стороны: генератор отдаёт, электролизёр принимает
        EnergyStorage.SIDED.registerForBlockEntity((generator, side) -> generator.energy, COAL_GENERATOR);
        EnergyStorage.SIDED.registerForBlockEntity((electrolyzer, side) -> electrolyzer.energy, ELECTROLYZER);
    }
}
