package com.orbitcore.block.entity;

import com.orbitcore.item.RocketItem;
import com.orbitcore.item.RocketPartItem;
import com.orbitcore.registry.ModBlockEntities;
import com.orbitcore.registry.ModItems;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.screen.RocketAssemblerScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Сборочный стенд: десять ячеек под модули (снизу вверх) и ячейка для готовой ракеты.
 */
public class RocketAssemblerBlockEntity extends MachineBlockEntity {
    public static final int PART_SLOTS = RocketDesign.MAX_PARTS;
    public static final int OUTPUT = PART_SLOTS;
    private static final int[] SLOTS = {OUTPUT};

    public RocketAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROCKET_ASSEMBLER, pos, state, PART_SLOTS + 1);
    }

    /** Конструкция из модулей в ячейках, пустые ячейки пропускаются. */
    public static RocketDesign designOf(Inventory inventory) {
        List<RocketDesign.Entry> parts = new ArrayList<>();
        for (int i = 0; i < PART_SLOTS; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() instanceof RocketPartItem item) {
                parts.add(new RocketDesign.Entry(item.part(), Math.min(stack.getCount(), item.part().maxStack())));
            }
        }
        return new RocketDesign(parts);
    }

    /** Собрать ракету. @return true, если ракета появилась в выходной ячейке. */
    public boolean assemble() {
        RocketDesign design = designOf(this);
        if (!design.isValid() || !items.get(OUTPUT).isEmpty()) {
            return false;
        }
        for (int i = 0; i < PART_SLOTS; i++) {
            ItemStack stack = items.get(i);
            if (stack.getItem() instanceof RocketPartItem item) {
                stack.decrement(Math.min(stack.getCount(), item.part().maxStack()));
            }
        }
        items.set(OUTPUT, RocketItem.create(ModItems.ASSEMBLED_ROCKET, design, 0, 0));
        markDirty();
        return true;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot < PART_SLOTS && stack.getItem() instanceof RocketPartItem;
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == OUTPUT;
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new RocketAssemblerScreenHandler(syncId, playerInventory, this, new ArrayPropertyDelegate(0));
    }
}
