package com.orbitcore.screen;

import com.orbitcore.block.entity.ElectrolyzerBlockEntity;
import com.orbitcore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.slot.Slot;

public class ElectrolyzerScreenHandler extends MachineScreenHandler {
    public ElectrolyzerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(2), new ArrayPropertyDelegate(4));
    }

    public ElectrolyzerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.ELECTROLYZER, syncId, playerInventory, inventory, properties);
        addSlot(new Slot(inventory, ElectrolyzerBlockEntity.INPUT, 48, 35));
        addSlot(new OutputSlot(inventory, ElectrolyzerBlockEntity.OUTPUT, 112, 35));
        addPlayerInventory(playerInventory);
    }

    public int energy() {
        return property(0);
    }

    public int capacity() {
        return property(1);
    }

    public int progress() {
        return property(2);
    }

    public int maxProgress() {
        return property(3);
    }
}
