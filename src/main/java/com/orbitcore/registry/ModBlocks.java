package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.block.CoalGeneratorBlock;
import com.orbitcore.block.ElectrolyzerBlock;
import com.orbitcore.block.EngineeringTableBlock;
import com.orbitcore.block.MachineBlock;
import com.orbitcore.block.MetallurgicalFurnaceBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    // Руды Земли
    public static final Block BAUXITE_ORE = register("bauxite_ore",
            new ExperienceDroppingBlock(AbstractBlock.Settings.copy(Blocks.IRON_ORE).mapColor(MapColor.TERRACOTTA_ORANGE)));
    public static final Block RUTILE_ORE = register("rutile_ore",
            new ExperienceDroppingBlock(AbstractBlock.Settings.copy(Blocks.IRON_ORE).strength(3.5f, 3.0f)));
    public static final Block DEEPSLATE_RUTILE_ORE = register("deepslate_rutile_ore",
            new ExperienceDroppingBlock(AbstractBlock.Settings.copy(Blocks.DEEPSLATE_IRON_ORE).strength(5.0f, 3.0f)));

    // Блоки металлов
    public static final Block STEEL_BLOCK = register("steel_block",
            new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).mapColor(MapColor.IRON_GRAY)));
    public static final Block ALUMINUM_BLOCK = register("aluminum_block",
            new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).mapColor(MapColor.LIGHT_GRAY).strength(4.0f, 6.0f)));
    public static final Block TITANIUM_BLOCK = register("titanium_block",
            new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).mapColor(MapColor.PURPLE).strength(6.0f, 12.0f)));
    public static final Block MACHINE_CASING = register("machine_casing",
            new Block(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).mapColor(MapColor.IRON_GRAY).strength(3.5f, 6.0f)));

    // Машины
    public static final Block METALLURGICAL_FURNACE = register("metallurgical_furnace",
            new MetallurgicalFurnaceBlock(machineSettings(13)));
    public static final Block COAL_GENERATOR = register("coal_generator",
            new CoalGeneratorBlock(machineSettings(10)));
    public static final Block ELECTROLYZER = register("electrolyzer",
            new ElectrolyzerBlock(machineSettings(7)));
    public static final Block ENGINEERING_TABLE = register("engineering_table",
            new EngineeringTableBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
                    .requiresTool().strength(2.5f, 6.0f).sounds(BlockSoundGroup.METAL)));

    private ModBlocks() {
    }

    private static AbstractBlock.Settings machineSettings(int litLight) {
        return AbstractBlock.Settings.create()
                .mapColor(MapColor.IRON_GRAY)
                .requiresTool()
                .strength(3.5f, 6.0f)
                .sounds(BlockSoundGroup.METAL)
                .luminance(state -> state.get(MachineBlock.LIT) ? litLight : 0);
    }

    private static Block register(String name, Block block) {
        Registry.register(Registries.BLOCK, OrbitCore.id(name), block);
        Registry.register(Registries.ITEM, OrbitCore.id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static void register() {
    }
}
