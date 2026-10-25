package com.orbitcore.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;

import java.util.function.Predicate;

/**
 * Основа интерфейса машины: слоты машины, инвентарь игрока, синхронизация чисел
 * (температура, энергия, прогресс) и перекладывание по Shift+клик.
 */
public abstract class MachineScreenHandler extends ScreenHandler {
    protected final Inventory inventory;
    protected final PropertyDelegate properties;

    protected MachineScreenHandler(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInventory,
                                   Inventory inventory, PropertyDelegate properties) {
        super(type, syncId);
        this.inventory = inventory;
        this.properties = properties;
        inventory.onOpen(playerInventory.player);
        addProperties(properties);
    }

    protected void addPlayerInventory(PlayerInventory playerInventory) {
        addPlayerInventory(playerInventory, 8, 84);
    }

    /** Инвентарь игрока: три ряда с левым верхним углом в (x, y) и панель быстрого доступа под ними. */
    protected void addPlayerInventory(PlayerInventory playerInventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, x + column * 18, y + 58));
        }
    }

    public int property(int index) {
        return properties.get(index);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        int machineSlots = inventory.size();
        if (index < machineSlots) {
            if (!insertItem(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickTransfer(stack, original);
        } else if (!insertItem(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTakeItem(player, stack);
        return original;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inventory.canPlayerUse(player);
    }

    /** Слот, в который можно положить только подходящие предметы. */
    public static class FilteredSlot extends Slot {
        private final Predicate<ItemStack> filter;

        public FilteredSlot(Inventory inventory, int index, int x, int y, Predicate<ItemStack> filter) {
            super(inventory, index, x, y);
            this.filter = filter;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return filter.test(stack);
        }
    }

    /** Выходной слот: забрать можно, положить нельзя. */
    public static class OutputSlot extends Slot {
        public OutputSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }
    }
}
