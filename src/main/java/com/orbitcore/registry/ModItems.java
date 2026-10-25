package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
    public static final Item RAW_BAUXITE = register("raw_bauxite");
    public static final Item RAW_RUTILE = register("raw_rutile");

    public static final Item STEEL_INGOT = register("steel_ingot");
    public static final Item ALUMINUM_INGOT = register("aluminum_ingot");
    public static final Item TITANIUM_INGOT = register("titanium_ingot");
    public static final Item SILICON = register("silicon");

    public static final Item STEEL_PLATE = register("steel_plate");
    public static final Item ALUMINUM_PLATE = register("aluminum_plate");
    public static final Item TITANIUM_PLATE = register("titanium_plate");
    public static final Item COPPER_WIRE = register("copper_wire");
    public static final Item CIRCUIT_BOARD = register("circuit_board");

    private ModItems() {
    }

    private static Item register(String name) {
        return Registry.register(Registries.ITEM, OrbitCore.id(name), new Item(new Item.Settings()));
    }

    public static void register() {
    }
}
