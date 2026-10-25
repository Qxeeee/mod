package com.orbitcore.block.entity;

import com.orbitcore.registry.ModBlockEntities;
import com.orbitcore.screen.CoalGeneratorScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.EnergyStorageUtil;
import team.reborn.energy.api.base.SimpleEnergyStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Угольный генератор: сжигает топливо и отдаёт энергию соседним блокам
 * (нашим машинам и любым машинам на Team Reborn Energy).
 */
public class CoalGeneratorBlockEntity extends MachineBlockEntity {
    public static final int FUEL = 0;
    public static final int CAPACITY = 20_000;
    /** Ровно столько потребляет электролизёр: один генератор держит одну машину. */
    public static final int GENERATION = 24;
    private static final int MAX_OUTPUT = 128;
    private static final int[] SLOTS = {FUEL};

    public final SimpleEnergyStorage energy = new SimpleEnergyStorage(CAPACITY, 0, MAX_OUTPUT) {
        @Override
        protected void onFinalCommit() {
            markDirty();
        }
    };

    private int burnTime;
    private int fuelTime;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) energy.amount;
                case 1 -> CAPACITY;
                case 2 -> burnTime;
                case 3 -> fuelTime;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energy.amount = value;
                case 2 -> burnTime = value;
                case 3 -> fuelTime = value;
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return 4;
        }
    };

    public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COAL_GENERATOR, pos, state, 1);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
        if (burnTime > 0) {
            burnTime--;
            energy.amount = Math.min(CAPACITY, energy.amount + GENERATION);
            markDirty();
        }
        if (burnTime == 0 && energy.amount < CAPACITY) {
            igniteFuel();
        }

        List<EnergyStorage> targets = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            EnergyStorage target = EnergyStorage.SIDED.find(world, pos.offset(direction), direction.getOpposite());
            if (target != null && target.supportsInsertion()) {
                targets.add(target);
            }
        }
        // Делим поровну: иначе первый сосед забирал бы всё, а остальные простаивали.
        // Что сосед не взял, достаётся следующим.
        for (int i = 0; i < targets.size(); i++) {
            long share = Math.min(MAX_OUTPUT, energy.amount) / (targets.size() - i);
            EnergyStorageUtil.move(energy, targets.get(i), share, null);
        }

        setLit(world, pos, state, burnTime > 0);
    }

    private void igniteFuel() {
        ItemStack fuel = items.get(FUEL);
        int time = MetallurgicalFurnaceBlockEntity.fuelTime(fuel);
        if (time <= 0) {
            return;
        }
        burnTime = fuelTime = time;
        Item remainder = fuel.getItem().getRecipeRemainder();
        fuel.decrement(1);
        if (fuel.isEmpty() && remainder != null) {
            items.set(FUEL, new ItemStack(remainder));
        }
        markDirty();
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return MetallurgicalFurnaceBlockEntity.fuelTime(stack) > 0;
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putLong("Energy", energy.amount);
        nbt.putInt("BurnTime", burnTime);
        nbt.putInt("FuelTime", fuelTime);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        energy.amount = Math.min(CAPACITY, nbt.getLong("Energy"));
        burnTime = nbt.getInt("BurnTime");
        fuelTime = nbt.getInt("FuelTime");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CoalGeneratorScreenHandler(syncId, playerInventory, this, properties);
    }
}
