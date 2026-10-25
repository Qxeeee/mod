package com.orbitcore.screen;

import com.orbitcore.block.entity.SampleAnalyzerBlockEntity;
import com.orbitcore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.slot.Slot;

public class SampleAnalyzerScreenHandler extends MachineScreenHandler {
    public SampleAnalyzerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(1), new ArrayPropertyDelegate(6));
    }

    public SampleAnalyzerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.SAMPLE_ANALYZER, syncId, playerInventory, inventory, properties);
        addSlot(new Slot(inventory, SampleAnalyzerBlockEntity.INPUT, 44, 35));
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

    public int status() {
        return property(4);
    }

    public int timesAnalyzed() {
        return property(5);
    }
}
