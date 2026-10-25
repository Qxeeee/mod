package com.orbitcore.screen;

import com.orbitcore.block.entity.AirSeparatorBlockEntity;
import com.orbitcore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;

public class AirSeparatorScreenHandler extends MachineScreenHandler {
    public AirSeparatorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(2), new ArrayPropertyDelegate(4));
    }

    public AirSeparatorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.AIR_SEPARATOR, syncId, playerInventory, inventory, properties);
        addSlot(new FilteredSlot(inventory, AirSeparatorBlockEntity.FILL_IN, 112, 17, stack -> stack.isOf(Items.BUCKET)));
        addSlot(new OutputSlot(inventory, AirSeparatorBlockEntity.FILL_OUT, 112, 53));
        addPlayerInventory(playerInventory);
    }

    public int energy() {
        return property(0);
    }

    public int capacity() {
        return property(1);
    }

    public int oxygen() {
        return property(2);
    }

    public int oxygenCapacity() {
        return property(3);
    }
}
