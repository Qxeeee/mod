package com.orbitcore.screen;

import com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity;
import com.orbitcore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.slot.Slot;

import static com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity.FUEL;
import static com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity.INPUT;
import static com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity.OUTPUT;
import static com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity.REDUCTANT;

public class MetallurgicalFurnaceScreenHandler extends MachineScreenHandler {
    public MetallurgicalFurnaceScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(4), new ArrayPropertyDelegate(6));
    }

    public MetallurgicalFurnaceScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.METALLURGICAL_FURNACE, syncId, playerInventory, inventory, properties);
        addSlot(new Slot(inventory, INPUT, 44, 17));
        addSlot(new FilteredSlot(inventory, REDUCTANT, 66, 17, stack -> stack.isIn(ItemTags.COALS)));
        addSlot(new FilteredSlot(inventory, FUEL, 55, 53, stack -> MetallurgicalFurnaceBlockEntity.fuelTime(stack) > 0));
        addSlot(new OutputSlot(inventory, OUTPUT, 120, 35));
        addPlayerInventory(playerInventory);
    }

    public int heat() {
        return property(0);
    }

    public int burnTime() {
        return property(1);
    }

    public int fuelTime() {
        return property(2);
    }

    public int progress() {
        return property(3);
    }

    public int maxProgress() {
        return property(4);
    }

    public int requiredHeat() {
        return property(5);
    }
}
