package com.orbitcore.screen;

import com.orbitcore.block.entity.RegolithProcessorBlockEntity;
import com.orbitcore.registry.ModItems;
import com.orbitcore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;

public class RegolithProcessorScreenHandler extends MachineScreenHandler {
    public RegolithProcessorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(6), new ArrayPropertyDelegate(8));
    }

    public RegolithProcessorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.REGOLITH_PROCESSOR, syncId, playerInventory, inventory, properties);
        addSlot(new FilteredSlot(inventory, RegolithProcessorBlockEntity.INPUT, 30, 35,
                stack -> RegolithProcessorBlockEntity.process(stack) != null));
        addSlot(new OutputSlot(inventory, RegolithProcessorBlockEntity.OUTPUT, 80, 35));
        addSlot(new FilteredSlot(inventory, RegolithProcessorBlockEntity.CANISTER_IN, 116, 17, stack -> stack.isOf(ModItems.GAS_CANISTER)));
        addSlot(new OutputSlot(inventory, RegolithProcessorBlockEntity.CANISTER_OUT, 116, 53));
        addSlot(new FilteredSlot(inventory, RegolithProcessorBlockEntity.BUCKET_IN, 152, 17, stack -> stack.isOf(Items.BUCKET)));
        addSlot(new OutputSlot(inventory, RegolithProcessorBlockEntity.BUCKET_OUT, 152, 53));
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

    public int helium() {
        return property(4);
    }

    public int heliumCapacity() {
        return property(5);
    }

    public int oxygen() {
        return property(6);
    }

    public int oxygenCapacity() {
        return property(7);
    }
}
