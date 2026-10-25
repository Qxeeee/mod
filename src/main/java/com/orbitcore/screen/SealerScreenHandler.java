package com.orbitcore.screen;

import com.orbitcore.block.entity.SealerBlockEntity;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class SealerScreenHandler extends MachineScreenHandler {
    public SealerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(2), new ArrayPropertyDelegate(10));
    }

    public SealerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.SEALER, syncId, playerInventory, inventory, properties);
        addSlot(new FilteredSlot(inventory, SealerBlockEntity.BUCKET_IN, 134, 17, stack -> stack.isOf(ModFluids.LIQUID_OXYGEN_BUCKET)));
        addSlot(new OutputSlot(inventory, SealerBlockEntity.BUCKET_OUT, 134, 53));
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

    public SealerBlockEntity.Status status() {
        SealerBlockEntity.Status[] values = SealerBlockEntity.Status.values();
        return values[Math.max(0, Math.min(values.length - 1, property(4)))];
    }

    public int volume() {
        return property(5);
    }

    /** Где дыра — относительно герметизатора. */
    @Nullable
    public BlockPos leakOffset() {
        return property(6) == 0 ? null : new BlockPos(property(7), property(8), property(9));
    }
}
