package com.orbitcore.screen;

import com.orbitcore.block.entity.CoalGeneratorBlockEntity;
import com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity;
import com.orbitcore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;

public class CoalGeneratorScreenHandler extends MachineScreenHandler {
    public CoalGeneratorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(1), new ArrayPropertyDelegate(4));
    }

    public CoalGeneratorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.COAL_GENERATOR, syncId, playerInventory, inventory, properties);
        addSlot(new FilteredSlot(inventory, CoalGeneratorBlockEntity.FUEL, 56, 53,
                stack -> MetallurgicalFurnaceBlockEntity.fuelTime(stack) > 0));
        addPlayerInventory(playerInventory);
    }

    public int energy() {
        return property(0);
    }

    public int capacity() {
        return property(1);
    }

    public int burnTime() {
        return property(2);
    }

    public int fuelTime() {
        return property(3);
    }
}
