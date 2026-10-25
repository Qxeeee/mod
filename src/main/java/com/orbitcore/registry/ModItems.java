package com.orbitcore.registry;

import com.orbitcore.item.SignalScannerItem;
import com.orbitcore.item.RoverItem;
import net.minecraft.util.Rarity;
import com.orbitcore.OrbitCore;
import com.orbitcore.item.DosimeterItem;
import com.orbitcore.item.OxygenTankItem;
import com.orbitcore.item.RocketItem;
import com.orbitcore.item.RocketPartItem;
import com.orbitcore.item.SpacesuitItem;
import com.orbitcore.rocket.RocketPart;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
    public static final Item RAW_BAUXITE = register("raw_bauxite");
    public static final Item RAW_RUTILE = register("raw_rutile");
    public static final Item RAW_ILMENITE = register("raw_ilmenite");

    public static final Item STEEL_INGOT = register("steel_ingot");
    public static final Item ALUMINUM_INGOT = register("aluminum_ingot");
    public static final Item TITANIUM_INGOT = register("titanium_ingot");
    public static final Item SILICON = register("silicon");

    public static final Item STEEL_PLATE = register("steel_plate");
    public static final Item ALUMINUM_PLATE = register("aluminum_plate");
    public static final Item TITANIUM_PLATE = register("titanium_plate");
    public static final Item COPPER_WIRE = register("copper_wire");
    public static final Item CIRCUIT_BOARD = register("circuit_board");

    // Модули ракеты
    public static final Item ENGINE_ISKRA = part(RocketPart.ENGINE_ISKRA);
    public static final Item ENGINE_FAKEL = part(RocketPart.ENGINE_FAKEL);
    public static final Item FUEL_TANK_SMALL = part(RocketPart.FUEL_TANK_SMALL);
    public static final Item FUEL_TANK_LARGE = part(RocketPart.FUEL_TANK_LARGE);
    public static final Item STAGE_DECOUPLER = part(RocketPart.STAGE_DECOUPLER);
    public static final Item CAPSULE_KAPLYA = part(RocketPart.CAPSULE);
    public static final Item ROCKET_FINS = part(RocketPart.FINS);
    public static final Item LANDING_LEGS = part(RocketPart.LANDING_LEGS);
    public static final Item ASSEMBLED_ROCKET = Registry.register(Registries.ITEM, OrbitCore.id("assembled_rocket"),
            new RocketItem(new Item.Settings()));

    // Лунная добыча
    public static final Item GAS_CANISTER = register("gas_canister");
    public static final Item HELIUM3_CANISTER = Registry.register(Registries.ITEM, OrbitCore.id("helium3_canister"),
            new Item(new Item.Settings().maxCount(16)));
    public static final Item RAW_METEORIC_IRON = register("raw_meteoric_iron");

    // «Эхо» и луноход
    public static final Item CRYSTAL_SHARD = register("crystal_shard");
    public static final Item ECHO_SHARD = Registry.register(Registries.ITEM, OrbitCore.id("echo_shard"),
            new Item(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));
    public static final Item SIGNAL_SCANNER = Registry.register(Registries.ITEM, OrbitCore.id("signal_scanner"),
            new SignalScannerItem(new Item.Settings().maxCount(1)));
    public static final Item ROVER = Registry.register(Registries.ITEM, OrbitCore.id("rover"),
            new RoverItem(new Item.Settings().maxCount(1)));

    // Жизнеобеспечение
    public static final Item SPACESUIT_HELMET = suit("spacesuit_helmet", ArmorItem.Type.HELMET);
    public static final Item SPACESUIT_CHESTPLATE = suit("spacesuit_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final Item SPACESUIT_LEGGINGS = suit("spacesuit_leggings", ArmorItem.Type.LEGGINGS);
    public static final Item SPACESUIT_BOOTS = suit("spacesuit_boots", ArmorItem.Type.BOOTS);
    public static final Item OXYGEN_TANK = Registry.register(Registries.ITEM, OrbitCore.id("oxygen_tank"),
            new OxygenTankItem(new Item.Settings().maxCount(1)));
    public static final Item DOSIMETER = Registry.register(Registries.ITEM, OrbitCore.id("dosimeter"),
            new DosimeterItem(new Item.Settings().maxCount(1)));

    private ModItems() {
    }

    private static Item part(RocketPart part) {
        return Registry.register(Registries.ITEM, OrbitCore.id(part.id()), new RocketPartItem(part, new Item.Settings()));
    }

    private static Item suit(String name, ArmorItem.Type type) {
        return Registry.register(Registries.ITEM, OrbitCore.id(name),
                new SpacesuitItem(ModArmorMaterials.SPACESUIT, type, new Item.Settings()));
    }

    private static Item register(String name) {
        return Registry.register(Registries.ITEM, OrbitCore.id(name), new Item(new Item.Settings()));
    }

    public static void register() {
    }
}
