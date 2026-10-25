package com.orbitcore.gametest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.SaveLoading;
import net.minecraft.test.TestServer;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.level.LevelInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Тестовый сервер Minecraft создаёт мир с пустым списком измерений из данных —
 * без Луны. Подставляем настоящий список, чтобы тесты могли летать на Луну.
 * Только для тестов, в готовый мод не попадает.
 */
@Mixin(TestServer.class)
public class TestServerMixin {
    @WrapOperation(method = "method_40377", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/dimension/DimensionOptionsRegistryHolder;toConfig(Lnet/minecraft/registry/Registry;)Lnet/minecraft/world/dimension/DimensionOptionsRegistryHolder$DimensionsConfig;"))
    private static DimensionOptionsRegistryHolder.DimensionsConfig orbitcore$withDataDimensions(
            DimensionOptionsRegistryHolder holder, Registry<DimensionOptions> empty,
            Operation<DimensionOptionsRegistryHolder.DimensionsConfig> original,
            LevelInfo levelInfo, SaveLoading.LoadContextSupplierContext context) {
        return original.call(holder, context.dimensionsRegistryManager().get(RegistryKeys.DIMENSION));
    }
}
