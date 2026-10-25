package com.orbitcore.block.entity;

import com.orbitcore.fluid.FluidTank;
import com.orbitcore.registry.ModBlockEntities;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.screen.AirSeparatorScreenHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * Воздухоразделительная установка: сжимает и охлаждает воздух, пока кислород
 * не станет жидким. Сырья не нужно — только энергия.
 */
public class AirSeparatorBlockEntity extends MachineBlockEntity {
    public static final int FILL_IN = 0;
    public static final int FILL_OUT = 1;
    public static final int ENERGY_PER_TICK = 40;
    public static final int OXYGEN_PER_TICK = 10;
    private static final int[] INPUT_SLOTS = {FILL_IN};
    private static final int[] OUTPUT_SLOTS = {FILL_OUT};

    public final SimpleEnergyStorage energy = new SimpleEnergyStorage(10_000, 128, 0) {
        @Override
        protected void onFinalCommit() {
            markDirty();
        }
    };
    public final FluidTank oxygen = new FluidTank(ModFluids.LIQUID_OXYGEN, 8000, this::markDirty);
    public final Storage<FluidVariant> pipes = FilteringStorage.extractOnlyOf(oxygen);

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) energy.amount;
                case 1 -> (int) energy.capacity;
                case 2 -> oxygen.amountMb();
                case 3 -> oxygen.capacityMb();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int size() {
            return 4;
        }
    };

    public AirSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIR_SEPARATOR, pos, state, 2);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
        oxygen.fillBucketInto(items, FILL_IN, FILL_OUT, ModFluids.LIQUID_OXYGEN_BUCKET);
        boolean working = oxygen.spaceMb() >= OXYGEN_PER_TICK && energy.amount >= ENERGY_PER_TICK;
        if (working) {
            energy.amount -= ENERGY_PER_TICK;
            oxygen.fillMb(OXYGEN_PER_TICK);
        }
        setLit(world, pos, state, working);
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == FILL_IN && stack.isOf(Items.BUCKET);
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == FILL_OUT;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putLong("Energy", energy.amount);
        nbt.put("Oxygen", oxygen.variant.toNbt());
        nbt.putLong("OxygenAmount", oxygen.amount);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        energy.amount = Math.min(energy.capacity, nbt.getLong("Energy"));
        oxygen.variant = FluidVariant.fromNbt(nbt.getCompound("Oxygen"));
        oxygen.amount = nbt.getLong("OxygenAmount");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new AirSeparatorScreenHandler(syncId, playerInventory, this, properties);
    }
}
