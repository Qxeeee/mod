package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.fluid.OrbitFluid;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.MapColor;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Ракетное топливо: керосин и жидкий кислород. Обе жидкости совместимы с трубами
 * других модов через Fabric Transfer API и разливаются вёдрами.
 */
public final class ModFluids {
    public static final FlowableFluid KEROSENE = Registry.register(Registries.FLUID, OrbitCore.id("kerosene"),
            new OrbitFluid.Still(() -> ModFluids.KEROSENE, () -> ModFluids.FLOWING_KEROSENE, () -> ModFluids.KEROSENE_BUCKET, () -> ModFluids.KEROSENE_BLOCK));
    public static final FlowableFluid FLOWING_KEROSENE = Registry.register(Registries.FLUID, OrbitCore.id("flowing_kerosene"),
            new OrbitFluid.Flowing(() -> ModFluids.KEROSENE, () -> ModFluids.FLOWING_KEROSENE, () -> ModFluids.KEROSENE_BUCKET, () -> ModFluids.KEROSENE_BLOCK));
    public static final FlowableFluid LIQUID_OXYGEN = Registry.register(Registries.FLUID, OrbitCore.id("liquid_oxygen"),
            new OrbitFluid.Still(() -> ModFluids.LIQUID_OXYGEN, () -> ModFluids.FLOWING_LIQUID_OXYGEN, () -> ModFluids.LIQUID_OXYGEN_BUCKET, () -> ModFluids.LIQUID_OXYGEN_BLOCK));
    public static final FlowableFluid FLOWING_LIQUID_OXYGEN = Registry.register(Registries.FLUID, OrbitCore.id("flowing_liquid_oxygen"),
            new OrbitFluid.Flowing(() -> ModFluids.LIQUID_OXYGEN, () -> ModFluids.FLOWING_LIQUID_OXYGEN, () -> ModFluids.LIQUID_OXYGEN_BUCKET, () -> ModFluids.LIQUID_OXYGEN_BLOCK));

    public static final Block KEROSENE_BLOCK = Registry.register(Registries.BLOCK, OrbitCore.id("kerosene"),
            new FluidBlock(KEROSENE, AbstractBlock.Settings.copy(Blocks.WATER).mapColor(MapColor.ORANGE)));
    public static final Block LIQUID_OXYGEN_BLOCK = Registry.register(Registries.BLOCK, OrbitCore.id("liquid_oxygen"),
            new FluidBlock(LIQUID_OXYGEN, AbstractBlock.Settings.copy(Blocks.WATER).mapColor(MapColor.LIGHT_BLUE)));

    public static final Item KEROSENE_BUCKET = Registry.register(Registries.ITEM, OrbitCore.id("kerosene_bucket"),
            new BucketItem(KEROSENE, new Item.Settings().recipeRemainder(Items.BUCKET).maxCount(1)));
    public static final Item LIQUID_OXYGEN_BUCKET = Registry.register(Registries.ITEM, OrbitCore.id("liquid_oxygen_bucket"),
            new BucketItem(LIQUID_OXYGEN, new Item.Settings().recipeRemainder(Items.BUCKET).maxCount(1)));

    private ModFluids() {
    }

    public static void register() {
    }
}
