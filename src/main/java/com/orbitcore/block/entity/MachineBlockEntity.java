package com.orbitcore.block.entity;

import com.orbitcore.block.MachineBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Основа блок-сущности машины: инвентарь, сохранение, доступ для воронок и интерфейс.
 * Логика работы — в {@link #tick}, который вызывается только на сервере.
 */
public abstract class MachineBlockEntity extends BlockEntity implements SidedInventory, NamedScreenHandlerFactory {
    protected final DefaultedList<ItemStack> items;

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state);
        this.items = DefaultedList.ofSize(size, ItemStack.EMPTY);
    }

    public abstract void tick(World world, BlockPos pos, BlockState state);

    /** Переключает состояние «работает» (свечение, анимированная текстура). */
    protected void setLit(World world, BlockPos pos, BlockState state, boolean lit) {
        if (state.get(MachineBlock.LIT) != lit) {
            world.setBlockState(pos, state.with(MachineBlock.LIT, lit), Block.NOTIFY_ALL);
        }
    }

    /** Помещается ли результат в выходной слот. */
    protected boolean canOutput(int slot, ItemStack result) {
        ItemStack current = items.get(slot);
        if (current.isEmpty()) {
            return true;
        }
        return ItemStack.canCombine(current, result) && current.getCount() + result.getCount() <= current.getMaxCount();
    }

    protected void addOutput(int slot, ItemStack result) {
        ItemStack current = items.get(slot);
        if (current.isEmpty()) {
            items.set(slot, result.copy());
        } else {
            current.increment(result.getCount());
        }
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable(getCachedState().getBlock().getTranslationKey());
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, items);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        items.clear();
        Inventories.readNbt(nbt, items);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack stack = Inventories.splitStack(items, slot, amount);
        if (!stack.isEmpty()) {
            markDirty();
        }
        return stack;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    @Override
    public void clear() {
        items.clear();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, Direction dir) {
        return isValid(slot, stack);
    }
}
