package com.orbitcore.screen;

import com.orbitcore.block.entity.OxygenStationBlockEntity;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.registry.ModScreenHandlers;
import com.orbitcore.survival.Oxygen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;

public class OxygenStationScreenHandler extends MachineScreenHandler {
    public OxygenStationScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(3), new ArrayPropertyDelegate(6));
    }

    public OxygenStationScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.OXYGEN_STATION, syncId, playerInventory, inventory, properties);
        addSlot(new FilteredSlot(inventory, OxygenStationBlockEntity.BUCKET_IN, 44, 17, stack -> stack.isOf(ModFluids.LIQUID_OXYGEN_BUCKET)));
        addSlot(new OutputSlot(inventory, OxygenStationBlockEntity.BUCKET_OUT, 44, 53));
        addSlot(new FilteredSlot(inventory, OxygenStationBlockEntity.FILL, 116, 35, stack -> Oxygen.capacity(stack) > 0));
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

    public int itemOxygen() {
        return property(4);
    }

    public int itemCapacity() {
        return property(5);
    }
}
