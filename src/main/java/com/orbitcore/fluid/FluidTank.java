package com.orbitcore.fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.collection.DefaultedList;

/**
 * Бак машины для одной жидкости. Совместим с трубами других модов (Transfer API),
 * а для логики машины есть простые методы в миллибакетах (1 ведро = 1000 мБ).
 */
public class FluidTank extends SingleFluidStorage {
    public static final long MB = FluidConstants.BUCKET / 1000;

    private final Fluid fluid;
    private final long capacity;
    private final Runnable onChange;

    public FluidTank(Fluid fluid, int capacityMb, Runnable onChange) {
        this.fluid = fluid;
        this.capacity = capacityMb * MB;
        this.onChange = onChange;
    }

    @Override
    protected long getCapacity(FluidVariant variant) {
        return capacity;
    }

    @Override
    protected boolean canInsert(FluidVariant variant) {
        return variant.isOf(fluid);
    }

    @Override
    protected void onFinalCommit() {
        onChange.run();
    }

    public int amountMb() {
        return (int) (amount / MB);
    }

    public int capacityMb() {
        return (int) (capacity / MB);
    }

    public int spaceMb() {
        return (int) ((capacity - amount) / MB);
    }

    /** Добавить жидкость в обход транзакций (из логики самой машины). */
    public void fillMb(int mb) {
        if (variant.isBlank()) {
            variant = FluidVariant.of(fluid);
        }
        amount = Math.min(capacity, amount + mb * MB);
        onChange.run();
    }

    /** Забрать жидкость в обход транзакций. */
    public void drainMb(int mb) {
        amount = Math.max(0, amount - mb * MB);
        if (amount == 0) {
            variant = FluidVariant.blank();
        }
        onChange.run();
    }

    /** Ведро жидкости из слота {@code in} — в бак, пустое ведро — в слот {@code out}. */
    public boolean drainBucketFrom(DefaultedList<ItemStack> items, int in, int out, Item fullBucket) {
        ItemStack input = items.get(in);
        ItemStack output = items.get(out);
        if (!input.isOf(fullBucket) || spaceMb() < 1000
                || !(output.isEmpty() || output.isOf(Items.BUCKET) && output.getCount() < output.getMaxCount())) {
            return false;
        }
        input.decrement(1);
        if (output.isEmpty()) {
            items.set(out, new ItemStack(Items.BUCKET));
        } else {
            output.increment(1);
        }
        fillMb(1000);
        return true;
    }

    /** Пустое ведро из слота {@code in} наполняется из бака и кладётся в слот {@code out}. */
    public boolean fillBucketInto(DefaultedList<ItemStack> items, int in, int out, Item fullBucket) {
        if (!items.get(in).isOf(Items.BUCKET) || amountMb() < 1000 || !items.get(out).isEmpty()) {
            return false;
        }
        items.get(in).decrement(1);
        items.set(out, new ItemStack(fullBucket));
        drainMb(1000);
        return true;
    }
}
