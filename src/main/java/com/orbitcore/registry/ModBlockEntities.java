package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.block.entity.AirSeparatorBlockEntity;
import com.orbitcore.block.entity.CoalGeneratorBlockEntity;
import com.orbitcore.block.entity.ElectrolyzerBlockEntity;
import com.orbitcore.block.entity.FuelRefineryBlockEntity;
import com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity;
import com.orbitcore.block.entity.OxygenStationBlockEntity;
import com.orbitcore.block.entity.RegolithProcessorBlockEntity;
import com.orbitcore.block.entity.RocketAssemblerBlockEntity;
import com.orbitcore.block.entity.SampleAnalyzerBlockEntity;
import com.orbitcore.block.entity.SealerBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
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
    public static final BlockEntityType<SampleAnalyzerBlockEntity> SAMPLE_ANALYZER =
            register("sample_analyzer", SampleAnalyzerBlockEntity::new, ModBlocks.SAMPLE_ANALYZER);
    public static final BlockEntityType<FuelRefineryBlockEntity> FUEL_REFINERY =
            register("fuel_refinery", FuelRefineryBlockEntity::new, ModBlocks.FUEL_REFINERY);
    public static final BlockEntityType<AirSeparatorBlockEntity> AIR_SEPARATOR =
            register("air_separator", AirSeparatorBlockEntity::new, ModBlocks.AIR_SEPARATOR);
    public static final BlockEntityType<RocketAssemblerBlockEntity> ROCKET_ASSEMBLER =
            register("rocket_assembler", RocketAssemblerBlockEntity::new, ModBlocks.ROCKET_ASSEMBLER);
    public static final BlockEntityType<RegolithProcessorBlockEntity> REGOLITH_PROCESSOR =
            register("regolith_processor", RegolithProcessorBlockEntity::new, ModBlocks.REGOLITH_PROCESSOR);
    public static final BlockEntityType<OxygenStationBlockEntity> OXYGEN_STATION =
            register("oxygen_station", OxygenStationBlockEntity::new, ModBlocks.OXYGEN_STATION);
    public static final BlockEntityType<SealerBlockEntity> SEALER =
            register("sealer", SealerBlockEntity::new, ModBlocks.SEALER);

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
        EnergyStorage.SIDED.registerForBlockEntity((analyzer, side) -> analyzer.energy, SAMPLE_ANALYZER);
        EnergyStorage.SIDED.registerForBlockEntity((refinery, side) -> refinery.energy, FUEL_REFINERY);
        EnergyStorage.SIDED.registerForBlockEntity((separator, side) -> separator.energy, AIR_SEPARATOR);
        EnergyStorage.SIDED.registerForBlockEntity((processor, side) -> processor.energy, REGOLITH_PROCESSOR);
        EnergyStorage.SIDED.registerForBlockEntity((station, side) -> station.energy, OXYGEN_STATION);
        EnergyStorage.SIDED.registerForBlockEntity((sealer, side) -> sealer.energy, SEALER);
        FluidStorage.SIDED.registerForBlockEntity((refinery, side) -> refinery.pipes, FUEL_REFINERY);
        FluidStorage.SIDED.registerForBlockEntity((separator, side) -> separator.pipes, AIR_SEPARATOR);
        FluidStorage.SIDED.registerForBlockEntity((station, side) -> station.pipes, OXYGEN_STATION);
        FluidStorage.SIDED.registerForBlockEntity((processor, side) -> processor.pipes, REGOLITH_PROCESSOR);
        FluidStorage.SIDED.registerForBlockEntity((sealer, side) -> sealer.pipes, SEALER);
    }
}
