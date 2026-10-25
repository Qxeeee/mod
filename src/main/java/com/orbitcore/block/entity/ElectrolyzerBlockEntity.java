package com.orbitcore.block.entity;

import com.orbitcore.recipe.ElectrolysisRecipe;
import com.orbitcore.registry.ModBlockEntities;
import com.orbitcore.registry.ModRecipes;
import com.orbitcore.screen.ElectrolyzerScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * Электролизёр: работает на энергии от генератора. На этапе 0.2 получает алюминий
 * из боксита, позже — водород и кислород из воды.
 */
public class ElectrolyzerBlockEntity extends MachineBlockEntity {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int CAPACITY = 10_000;
    private static final int MAX_INPUT = 128;
    private static final int[] INPUT_SLOTS = {INPUT};
    private static final int[] OUTPUT_SLOTS = {OUTPUT};

    public final SimpleEnergyStorage energy = new SimpleEnergyStorage(CAPACITY, MAX_INPUT, 0) {
        @Override
        protected void onFinalCommit() {
            markDirty();
        }
    };

    private int progress;
    private int maxProgress;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) energy.amount;
                case 1 -> CAPACITY;
                case 2 -> progress;
                case 3 -> maxProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energy.amount = value;
                case 2 -> progress = value;
                case 3 -> maxProgress = value;
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return 4;
        }
    };

    public ElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTROLYZER, pos, state, 2);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
        ElectrolysisRecipe recipe = world.getRecipeManager().getFirstMatch(ModRecipes.ELECTROLYSIS, this, world)
                .filter(r -> canOutput(OUTPUT, r.result()))
                .orElse(null);

        boolean working = false;
        if (recipe == null) {
            progress = 0;
            maxProgress = 0;
        } else {
            maxProgress = recipe.time();
            if (energy.amount >= recipe.energyPerTick()) {
                energy.amount -= recipe.energyPerTick();
                working = true;
                if (++progress >= maxProgress) {
                    items.get(INPUT).decrement(1);
                    addOutput(OUTPUT, recipe.craft(this, world.getRegistryManager()));
                    progress = 0;
                }
                markDirty();
            }
        }
        setLit(world, pos, state, working);
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == INPUT && (world == null || world.getRecipeManager().listAllOfType(ModRecipes.ELECTROLYSIS).stream()
                .anyMatch(r -> r.ingredient().test(stack)));
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == OUTPUT;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putLong("Energy", energy.amount);
        nbt.putInt("Progress", progress);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        energy.amount = Math.min(CAPACITY, nbt.getLong("Energy"));
        progress = nbt.getInt("Progress");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new ElectrolyzerScreenHandler(syncId, playerInventory, this, properties);
    }
}
