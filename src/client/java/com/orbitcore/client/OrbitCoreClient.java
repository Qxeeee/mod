package com.orbitcore.client;

import com.orbitcore.OrbitCore;
import com.orbitcore.client.flight.FlightCinematics;
import com.orbitcore.client.hud.SuitHud;
import com.orbitcore.client.network.OrbitClientNetworking;
import com.orbitcore.client.render.MoonSky;
import com.orbitcore.client.render.RocketEntityRenderer;
import com.orbitcore.client.render.RocketStageEntityRenderer;
import com.orbitcore.client.screen.AirSeparatorScreen;
import com.orbitcore.client.screen.CoalGeneratorScreen;
import com.orbitcore.client.screen.ElectrolyzerScreen;
import com.orbitcore.client.screen.EngineeringTableScreen;
import com.orbitcore.client.screen.FuelRefineryScreen;
import com.orbitcore.client.screen.MetallurgicalFurnaceScreen;
import com.orbitcore.client.screen.OxygenStationScreen;
import com.orbitcore.client.screen.RocketAssemblerScreen;
import com.orbitcore.client.screen.SampleAnalyzerScreen;
import com.orbitcore.client.screen.SealerScreen;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Клиентская часть мода: рендер моделей и неба, экраны, катсцены, звук, постобработка.
 * Этот код не загружается на выделенном сервере.
 */
public class OrbitCoreClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.METALLURGICAL_FURNACE, MetallurgicalFurnaceScreen::new);
        HandledScreens.register(ModScreenHandlers.COAL_GENERATOR, CoalGeneratorScreen::new);
        HandledScreens.register(ModScreenHandlers.ELECTROLYZER, ElectrolyzerScreen::new);
        HandledScreens.register(ModScreenHandlers.ENGINEERING_TABLE, EngineeringTableScreen::new);
        HandledScreens.register(ModScreenHandlers.SAMPLE_ANALYZER, SampleAnalyzerScreen::new);
        HandledScreens.register(ModScreenHandlers.FUEL_REFINERY, FuelRefineryScreen::new);
        HandledScreens.register(ModScreenHandlers.AIR_SEPARATOR, AirSeparatorScreen::new);
        HandledScreens.register(ModScreenHandlers.ROCKET_ASSEMBLER, RocketAssemblerScreen::new);
        HandledScreens.register(ModScreenHandlers.OXYGEN_STATION, OxygenStationScreen::new);
        HandledScreens.register(ModScreenHandlers.SEALER, SealerScreen::new);
        EntityRendererRegistry.register(ModEntities.ROCKET, RocketEntityRenderer::new);

        // Топливо: свои текстуры и полупрозрачность, как у воды
        FluidRenderHandlerRegistry.INSTANCE.register(ModFluids.KEROSENE, ModFluids.FLOWING_KEROSENE, new SimpleFluidRenderHandler(
                OrbitCore.id("block/kerosene_still"), OrbitCore.id("block/kerosene_flow")));
        FluidRenderHandlerRegistry.INSTANCE.register(ModFluids.LIQUID_OXYGEN, ModFluids.FLOWING_LIQUID_OXYGEN, new SimpleFluidRenderHandler(
                OrbitCore.id("block/liquid_oxygen_still"), OrbitCore.id("block/liquid_oxygen_flow")));
        BlockRenderLayerMap.INSTANCE.putFluids(RenderLayer.getTranslucent(),
                ModFluids.KEROSENE, ModFluids.FLOWING_KEROSENE, ModFluids.LIQUID_OXYGEN, ModFluids.FLOWING_LIQUID_OXYGEN);
        OrbitClientNetworking.register();
        FlightCinematics.register();
        MoonSky.register();
        SuitHud.register();
        EntityRendererRegistry.register(ModEntities.ROCKET_STAGE, RocketStageEntityRenderer::new);
        ModelLoadingPlugin.register(context -> context.addModels(RocketEntityRenderer.PARACHUTE));

        // Руды нарисованы слоем поверх камня, им нужна прозрачность
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutoutMipped(),
                ModBlocks.BAUXITE_ORE, ModBlocks.RUTILE_ORE, ModBlocks.DEEPSLATE_RUTILE_ORE);
        // У шлюзовой двери прозрачный иллюминатор
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.AIRLOCK_DOOR, RenderLayer.getCutout());

        // Подсказка к предмету, если в переводах есть ключ tooltip.orbitcore.<имя>
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (!OrbitCore.MOD_ID.equals(id.getNamespace())) {
                return;
            }
            String key = "tooltip.orbitcore." + id.getPath();
            if (I18n.hasTranslation(key)) {
                lines.add(Text.translatable(key).formatted(Formatting.GRAY));
            }
        });
    }
}
