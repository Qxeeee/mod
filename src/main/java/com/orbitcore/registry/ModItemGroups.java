package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroups {
    public static final ItemGroup ORBITCORE = Registry.register(Registries.ITEM_GROUP, OrbitCore.id("main"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.orbitcore"))
                    .icon(() -> new ItemStack(ModBlocks.METALLURGICAL_FURNACE))
                    .entries((context, entries) -> {
                        entries.add(ModBlocks.BAUXITE_ORE);
                        entries.add(ModBlocks.RUTILE_ORE);
                        entries.add(ModBlocks.DEEPSLATE_RUTILE_ORE);
                        entries.add(ModItems.RAW_BAUXITE);
                        entries.add(ModItems.RAW_RUTILE);

                        entries.add(ModItems.STEEL_INGOT);
                        entries.add(ModItems.ALUMINUM_INGOT);
                        entries.add(ModItems.TITANIUM_INGOT);
                        entries.add(ModItems.SILICON);
                        entries.add(ModBlocks.STEEL_BLOCK);
                        entries.add(ModBlocks.ALUMINUM_BLOCK);
                        entries.add(ModBlocks.TITANIUM_BLOCK);

                        entries.add(ModItems.STEEL_PLATE);
                        entries.add(ModItems.ALUMINUM_PLATE);
                        entries.add(ModItems.TITANIUM_PLATE);
                        entries.add(ModItems.COPPER_WIRE);
                        entries.add(ModItems.CIRCUIT_BOARD);
                        entries.add(ModBlocks.MACHINE_CASING);

                        entries.add(ModBlocks.METALLURGICAL_FURNACE);
                        entries.add(ModBlocks.ENGINEERING_TABLE);
                        entries.add(ModBlocks.COAL_GENERATOR);
                        entries.add(ModBlocks.ELECTROLYZER);

                        entries.add(ModBlocks.TELESCOPE);
                        entries.add(ModBlocks.MISSION_CONTROL);
                        entries.add(ModBlocks.SAMPLE_ANALYZER);

                        entries.add(ModBlocks.FUEL_REFINERY);
                        entries.add(ModBlocks.AIR_SEPARATOR);
                        entries.add(ModFluids.KEROSENE_BUCKET);
                        entries.add(ModFluids.LIQUID_OXYGEN_BUCKET);
                        entries.add(ModBlocks.ROCKET_ASSEMBLER);
                        entries.add(ModBlocks.LAUNCH_PAD);
                        entries.add(ModItems.ENGINE_ISKRA);
                        entries.add(ModItems.ENGINE_FAKEL);
                        entries.add(ModItems.FUEL_TANK_SMALL);
                        entries.add(ModItems.FUEL_TANK_LARGE);
                        entries.add(ModItems.STAGE_DECOUPLER);
                        entries.add(ModItems.CAPSULE_KAPLYA);
                        entries.add(ModItems.ROCKET_FINS);
                        entries.add(ModItems.LANDING_LEGS);

                        entries.add(ModBlocks.REGOLITH);
                        entries.add(ModBlocks.MARIA_REGOLITH);
                        entries.add(ModBlocks.ANORTHOSITE);
                        entries.add(ModBlocks.MOON_BASALT);
                        entries.add(ModBlocks.ILMENITE_ORE);
                        entries.add(ModItems.RAW_ILMENITE);
                        entries.add(ModBlocks.ICE_REGOLITH);
                        entries.add(ModBlocks.METEORITE);
                        entries.add(ModItems.RAW_METEORIC_IRON);
                        entries.add(ModBlocks.REGOLITH_PROCESSOR);
                        entries.add(ModItems.GAS_CANISTER);
                        entries.add(ModItems.HELIUM3_CANISTER);

                        entries.add(ModItems.SPACESUIT_HELMET);
                        entries.add(ModItems.SPACESUIT_CHESTPLATE);
                        entries.add(ModItems.SPACESUIT_LEGGINGS);
                        entries.add(ModItems.SPACESUIT_BOOTS);
                        entries.add(ModItems.OXYGEN_TANK);
                        entries.add(ModItems.DOSIMETER);
                        entries.add(ModBlocks.OXYGEN_STATION);
                        entries.add(ModBlocks.SEALER);
                        entries.add(ModBlocks.AIRLOCK_DOOR);
                    })
                    .build());

    private ModItemGroups() {
    }

    public static void register() {
    }
}
