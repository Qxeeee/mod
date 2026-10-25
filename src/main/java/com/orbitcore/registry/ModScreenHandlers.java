package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.screen.AirSeparatorScreenHandler;
import com.orbitcore.screen.CoalGeneratorScreenHandler;
import com.orbitcore.screen.ElectrolyzerScreenHandler;
import com.orbitcore.screen.EngineeringTableScreenHandler;
import com.orbitcore.screen.FuelRefineryScreenHandler;
import com.orbitcore.screen.MetallurgicalFurnaceScreenHandler;
import com.orbitcore.screen.RocketAssemblerScreenHandler;
import com.orbitcore.screen.SampleAnalyzerScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreenHandlers {
    public static final ScreenHandlerType<MetallurgicalFurnaceScreenHandler> METALLURGICAL_FURNACE =
            register("metallurgical_furnace", MetallurgicalFurnaceScreenHandler::new);
    public static final ScreenHandlerType<CoalGeneratorScreenHandler> COAL_GENERATOR =
            register("coal_generator", CoalGeneratorScreenHandler::new);
    public static final ScreenHandlerType<ElectrolyzerScreenHandler> ELECTROLYZER =
            register("electrolyzer", ElectrolyzerScreenHandler::new);
    public static final ScreenHandlerType<EngineeringTableScreenHandler> ENGINEERING_TABLE =
            register("engineering_table", EngineeringTableScreenHandler::new);
    public static final ScreenHandlerType<SampleAnalyzerScreenHandler> SAMPLE_ANALYZER =
            register("sample_analyzer", SampleAnalyzerScreenHandler::new);
    public static final ScreenHandlerType<FuelRefineryScreenHandler> FUEL_REFINERY =
            register("fuel_refinery", FuelRefineryScreenHandler::new);
    public static final ScreenHandlerType<AirSeparatorScreenHandler> AIR_SEPARATOR =
            register("air_separator", AirSeparatorScreenHandler::new);
    public static final ScreenHandlerType<RocketAssemblerScreenHandler> ROCKET_ASSEMBLER =
            register("rocket_assembler", RocketAssemblerScreenHandler::new);

    private ModScreenHandlers() {
    }

    private static <T extends ScreenHandler> ScreenHandlerType<T> register(String name, ScreenHandlerType.Factory<T> factory) {
        return Registry.register(Registries.SCREEN_HANDLER, OrbitCore.id(name), new ScreenHandlerType<>(factory, FeatureFlags.VANILLA_FEATURES));
    }

    public static void register() {
    }
}
