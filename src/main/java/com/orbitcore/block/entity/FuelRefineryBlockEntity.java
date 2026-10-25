package com.orbitcore.block.entity;

import com.orbitcore.fluid.FluidTank;
import com.orbitcore.registry.ModBlockEntities;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.screen.FuelRefineryScreenHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.base.SimpleEnergyStorage;

import java.util.List;

/**
 * Топливный синтезатор: уголь + вода + энергия → керосин. Так делают синтетическое
 * топливо и в реальности (процесс Фишера — Тропша).
 */
public class FuelRefineryBlockEntity extends MachineBlockEntity {
    public static final int COAL = 0;
    public static final int WATER_IN = 1;
    public static final int BUCKET_OUT = 2;
    public static final int FILL_IN = 3;
    public static final int FILL_OUT = 4;

    public static final int ENERGY_PER_TICK = 32;
    public static final int TIME = 80;
    public static final int WATER_PER_COAL = 250;
    public static final int KEROSENE_PER_COAL = 500;
    private static final int[] TOP_SLOTS = {COAL, WATER_IN};
    private static final int[] SIDE_SLOTS = {FILL_IN, WATER_IN, COAL};
    private static final int[] BOTTOM_SLOTS = {FILL_OUT, BUCKET_OUT};

    public final SimpleEnergyStorage energy = new SimpleEnergyStorage(10_000, 128, 0) {
        @Override
        protected void onFinalCommit() {
            markDirty();
        }
    };
    public final FluidTank water = new FluidTank(Fluids.WATER, 4000, this::markDirty);
    public final FluidTank kerosene = new FluidTank(ModFluids.KEROSENE, 8000, this::markDirty);
    /** Трубы снаружи: воду можно только заливать, керосин — только забирать. */
    public final Storage<FluidVariant> pipes = new CombinedStorage<>(List.of(
            FilteringStorage.insertOnlyOf(water), FilteringStorage.extractOnlyOf(kerosene)));

    private int progress;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) energy.amount;
                case 1 -> (int) energy.capacity;
                case 2 -> progress;
                case 3 -> TIME;
                case 4 -> water.amountMb();
                case 5 -> water.capacityMb();
                case 6 -> kerosene.amountMb();
                case 7 -> kerosene.capacityMb();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 2) {
                progress = value;
            }
        }

        @Override
        public int size() {
            return 8;
        }
    };

    public FuelRefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_REFINERY, pos, state, 5);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
        water.drainBucketFrom(items, WATER_IN, BUCKET_OUT, Items.WATER_BUCKET);
        kerosene.fillBucketInto(items, FILL_IN, FILL_OUT, ModFluids.KEROSENE_BUCKET);

        boolean canWork = items.get(COAL).isIn(ItemTags.COALS)
                && water.amountMb() >= WATER_PER_COAL
                && kerosene.spaceMb() >= KEROSENE_PER_COAL;
        boolean working = false;
        if (!canWork) {
            progress = 0;
        } else if (energy.amount >= ENERGY_PER_TICK) {
            energy.amount -= ENERGY_PER_TICK;
            working = true;
            if (++progress >= TIME) {
                progress = 0;
                items.get(COAL).decrement(1);
                water.drainMb(WATER_PER_COAL);
                kerosene.fillMb(KEROSENE_PER_COAL);
            }
            markDirty();
        }
        setLit(world, pos, state, working);
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return switch (slot) {
            case COAL -> stack.isIn(ItemTags.COALS);
            case WATER_IN -> stack.isOf(Items.WATER_BUCKET);
            case FILL_IN -> stack.isOf(Items.BUCKET);
            default -> false;
        };
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return switch (side) {
            case UP -> TOP_SLOTS;
            case DOWN -> BOTTOM_SLOTS;
            default -> SIDE_SLOTS;
        };
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == FILL_OUT || slot == BUCKET_OUT;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putLong("Energy", energy.amount);
        nbt.putInt("Progress", progress);
        nbt.put("Water", water.variant.toNbt());
        nbt.putLong("WaterAmount", water.amount);
        nbt.put("Kerosene", kerosene.variant.toNbt());
        nbt.putLong("KeroseneAmount", kerosene.amount);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        energy.amount = Math.min(energy.capacity, nbt.getLong("Energy"));
        progress = nbt.getInt("Progress");
        water.variant = FluidVariant.fromNbt(nbt.getCompound("Water"));
        water.amount = nbt.getLong("WaterAmount");
        kerosene.variant = FluidVariant.fromNbt(nbt.getCompound("Kerosene"));
        kerosene.amount = nbt.getLong("KeroseneAmount");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new FuelRefineryScreenHandler(syncId, playerInventory, this, properties);
    }
}
