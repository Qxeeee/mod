package com.orbitcore.screen;

import com.orbitcore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;

import static com.orbitcore.block.entity.FuelRefineryBlockEntity.BUCKET_OUT;
import static com.orbitcore.block.entity.FuelRefineryBlockEntity.COAL;
import static com.orbitcore.block.entity.FuelRefineryBlockEntity.FILL_IN;
import static com.orbitcore.block.entity.FuelRefineryBlockEntity.FILL_OUT;
import static com.orbitcore.block.entity.FuelRefineryBlockEntity.WATER_IN;

public class FuelRefineryScreenHandler extends MachineScreenHandler {
    public FuelRefineryScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(5), new ArrayPropertyDelegate(8));
    }

    public FuelRefineryScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.FUEL_REFINERY, syncId, playerInventory, inventory, properties);
        addSlot(new FilteredSlot(inventory, COAL, 56, 35, stack -> stack.isIn(ItemTags.COALS)));
        addSlot(new FilteredSlot(inventory, WATER_IN, 30, 17, stack -> stack.isOf(Items.WATER_BUCKET)));
        addSlot(new OutputSlot(inventory, BUCKET_OUT, 30, 53));
        addSlot(new FilteredSlot(inventory, FILL_IN, 130, 17, stack -> stack.isOf(Items.BUCKET)));
        addSlot(new OutputSlot(inventory, FILL_OUT, 130, 53));
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

    public int water() {
        return property(4);
    }

    public int waterCapacity() {
        return property(5);
    }

    public int kerosene() {
        return property(6);
    }

    public int keroseneCapacity() {
        return property(7);
    }
}
