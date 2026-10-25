package com.orbitcore.screen;

import com.orbitcore.block.entity.RocketAssemblerBlockEntity;
import com.orbitcore.item.RocketPartItem;
import com.orbitcore.registry.ModScreenHandlers;
import com.orbitcore.rocket.RocketDesign;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.slot.Slot;

/**
 * Интерфейс сборочного стенда. Ячейки модулей — два столбца по пять,
 * порядок снизу вверх: сначала левый столбец, потом правый.
 */
public class RocketAssemblerScreenHandler extends MachineScreenHandler {
    public static final int BUTTON_ASSEMBLE = 0;
    public static final int WIDTH = 232;
    public static final int HEIGHT = 206;

    public RocketAssemblerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(RocketAssemblerBlockEntity.PART_SLOTS + 1), new ArrayPropertyDelegate(0));
    }

    public RocketAssemblerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(ModScreenHandlers.ROCKET_ASSEMBLER, syncId, playerInventory, inventory, properties);
        for (int i = 0; i < RocketAssemblerBlockEntity.PART_SLOTS; i++) {
            addSlot(new PartSlot(inventory, i, partX(i), partY(i)));
        }
        addSlot(new OutputSlot(inventory, RocketAssemblerBlockEntity.OUTPUT, 204, 96));
        addPlayerInventory(playerInventory, (WIDTH - 162) / 2, 124);
    }

    public static int partX(int index) {
        return index < 5 ? 16 : 40;
    }

    public static int partY(int index) {
        return 17 + (4 - index % 5) * 18;
    }

    public RocketDesign design() {
        return RocketAssemblerBlockEntity.designOf(inventory);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == BUTTON_ASSEMBLE && inventory instanceof RocketAssemblerBlockEntity assembler) {
            return assembler.assemble();
        }
        return false;
    }

    /** Ячейка модуля: только модули ракеты; двигатели — связкой до четырёх, остальное — по одному. */
    private static class PartSlot extends Slot {
        PartSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.getItem() instanceof RocketPartItem;
        }

        @Override
        public int getMaxItemCount(ItemStack stack) {
            return stack.getItem() instanceof RocketPartItem item ? item.part().maxStack() : 0;
        }
    }
}
