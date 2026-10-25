package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.block.AirSeparatorBlock;
import com.orbitcore.block.AirlockDoorBlock;
import com.orbitcore.block.CoalGeneratorBlock;
import com.orbitcore.block.ElectrolyzerBlock;
import com.orbitcore.block.EngineeringTableBlock;
import com.orbitcore.block.FuelRefineryBlock;
import com.orbitcore.block.LaunchPadBlock;
import com.orbitcore.block.MachineBlock;
import com.orbitcore.block.MetallurgicalFurnaceBlock;
import com.orbitcore.block.MissionControlBlock;
import com.orbitcore.block.OxygenStationBlock;
import com.orbitcore.block.RegolithBlock;
import com.orbitcore.block.RegolithProcessorBlock;
import com.orbitcore.block.RocketAssemblerBlock;
import com.orbitcore.block.SampleAnalyzerBlock;
import com.orbitcore.block.SealerBlock;
import com.orbitcore.block.TelescopeBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.Blocks;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
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

    // Наука
    public static final Block TELESCOPE = register("telescope",
            new TelescopeBlock(AbstractBlock.Settings.create().mapColor(MapColor.ORANGE)
                    .strength(1.5f).sounds(BlockSoundGroup.COPPER).nonOpaque()));
    public static final Block MISSION_CONTROL = register("mission_control",
            new MissionControlBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
                    .requiresTool().strength(3.5f, 6.0f).sounds(BlockSoundGroup.METAL).nonOpaque()
                    .luminance(state -> 6)));
    public static final Block SAMPLE_ANALYZER = register("sample_analyzer",
            new SampleAnalyzerBlock(machineSettings(9)));

    // Ракеты и топливо
    public static final Block FUEL_REFINERY = register("fuel_refinery", new FuelRefineryBlock(machineSettings(8)));
    public static final Block AIR_SEPARATOR = register("air_separator", new AirSeparatorBlock(machineSettings(6)));
    public static final Block ROCKET_ASSEMBLER = register("rocket_assembler", new RocketAssemblerBlock(machineSettings(0)));
    // Луна
    public static final Block REGOLITH = register("regolith",
            new RegolithBlock(AbstractBlock.Settings.copy(Blocks.SAND).mapColor(MapColor.LIGHT_GRAY).strength(0.6f)));
    public static final Block MARIA_REGOLITH = register("maria_regolith",
            new RegolithBlock(AbstractBlock.Settings.copy(Blocks.SAND).mapColor(MapColor.GRAY).strength(0.6f)));
    public static final Block ANORTHOSITE = register("anorthosite",
            new Block(AbstractBlock.Settings.copy(Blocks.STONE).mapColor(MapColor.WHITE_GRAY)));
    public static final Block MOON_BASALT = register("moon_basalt",
            new Block(AbstractBlock.Settings.copy(Blocks.STONE).mapColor(MapColor.DEEPSLATE_GRAY).strength(1.8f, 6.0f)));
    public static final Block ILMENITE_ORE = register("ilmenite_ore",
            new ExperienceDroppingBlock(AbstractBlock.Settings.copy(Blocks.IRON_ORE).mapColor(MapColor.DEEPSLATE_GRAY)));
    public static final Block ICE_REGOLITH = register("ice_regolith",
            new Block(AbstractBlock.Settings.copy(Blocks.PACKED_ICE).mapColor(MapColor.PALE_PURPLE).slipperiness(0.6f)
                    .sounds(BlockSoundGroup.GLASS)));

    public static final Block LAUNCH_PAD = register("launch_pad",
            new LaunchPadBlock(AbstractBlock.Settings.create().mapColor(MapColor.GRAY)
                    .requiresTool().strength(5.0f, 12.0f).sounds(BlockSoundGroup.METAL)));

    // Лунная добыча и метеориты
    public static final Block REGOLITH_PROCESSOR = register("regolith_processor", new RegolithProcessorBlock(machineSettings(7)));
    public static final Block METEORITE = register("meteorite",
            new Block(AbstractBlock.Settings.copy(Blocks.ANCIENT_DEBRIS).mapColor(MapColor.BLACK).strength(8.0f, 1200.0f)));

    // Жизнеобеспечение
    public static final Block OXYGEN_STATION = register("oxygen_station", new OxygenStationBlock(machineSettings(5)));
    public static final Block SEALER = register("sealer", new SealerBlock(machineSettings(4)));
    /** Тяжёлую дверь открывают рукой, но звучит она как гермозатвор. */
    public static final BlockSetType AIRLOCK = BlockSetTypeBuilder.copyOf(BlockSetType.IRON)
            .openableByHand(true)
            .doorOpenSound(ModSounds.AIRLOCK_OPEN)
            .doorCloseSound(ModSounds.AIRLOCK_CLOSE)
            .register(OrbitCore.id("airlock"));
    public static final Block AIRLOCK_DOOR = register("airlock_door",
            new AirlockDoorBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).requiresTool()
                    .strength(5.0f, 12.0f).sounds(BlockSoundGroup.METAL).nonOpaque()
                    .pistonBehavior(PistonBehavior.DESTROY), AIRLOCK));

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
