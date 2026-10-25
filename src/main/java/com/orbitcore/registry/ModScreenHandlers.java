package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.screen.CoalGeneratorScreenHandler;
import com.orbitcore.screen.ElectrolyzerScreenHandler;
import com.orbitcore.screen.EngineeringTableScreenHandler;
import com.orbitcore.screen.MetallurgicalFurnaceScreenHandler;
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

    private ModScreenHandlers() {
    }

    private static <T extends ScreenHandler> ScreenHandlerType<T> register(String name, ScreenHandlerType.Factory<T> factory) {
        return Registry.register(Registries.SCREEN_HANDLER, OrbitCore.id(name), new ScreenHandlerType<>(factory, FeatureFlags.VANILLA_FEATURES));
    }

    public static void register() {
    }
}
