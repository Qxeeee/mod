package com.orbitcore.block.entity;

import com.orbitcore.recipe.AnalysisRecipe;
import com.orbitcore.registry.ModBlockEntities;
import com.orbitcore.registry.ModRecipes;
import com.orbitcore.science.SpaceProgram;
import com.orbitcore.screen.SampleAnalyzerScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * Анализатор образцов: разбирает образец и отдаёт очки в космическую программу.
 * Каждый вид образца приносит очки ограниченное число раз — дальше нужно искать новые.
 */
public class SampleAnalyzerBlockEntity extends MachineBlockEntity {
    public static final int INPUT = 0;
    public static final int CAPACITY = 8_000;
    public static final int ENERGY_PER_TICK = 16;
    public static final int TIME = 100;
    private static final int[] SLOTS = {INPUT};

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_WORKING = 1;
    public static final int STATUS_STUDIED = 2;
    public static final int STATUS_NO_ENERGY = 3;

    public final SimpleEnergyStorage energy = new SimpleEnergyStorage(CAPACITY, 128, 0) {
        @Override
        protected void onFinalCommit() {
            markDirty();
        }
    };

    private int progress;
    private int status;
    private int timesAnalyzed;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) energy.amount;
                case 1 -> CAPACITY;
                case 2 -> progress;
                case 3 -> TIME;
                case 4 -> status;
                case 5 -> timesAnalyzed;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energy.amount = value;
                case 2 -> progress = value;
                case 4 -> status = value;
                case 5 -> timesAnalyzed = value;
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return 6;
        }
    };

    public SampleAnalyzerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAMPLE_ANALYZER, pos, state, 1);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
        AnalysisRecipe recipe = world.getRecipeManager().getFirstMatch(ModRecipes.ANALYSIS, this, world).orElse(null);
        if (recipe == null || world.getServer() == null) {
            progress = 0;
            status = STATUS_IDLE;
            timesAnalyzed = 0;
            setLit(world, pos, state, false);
            return;
        }

        SpaceProgram program = SpaceProgram.get(world.getServer());
        Identifier item = Registries.ITEM.getId(items.get(INPUT).getItem());
        timesAnalyzed = program.timesAnalyzed(item);
        if (timesAnalyzed >= SpaceProgram.ANALYSIS_LIMIT) {
            progress = 0;
            status = STATUS_STUDIED;
        } else if (energy.amount < ENERGY_PER_TICK) {
            status = STATUS_NO_ENERGY;
        } else {
            status = STATUS_WORKING;
            energy.amount -= ENERGY_PER_TICK;
            if (++progress >= TIME) {
                progress = 0;
                items.get(INPUT).decrement(1);
                program.recordAnalysis(world.getServer(), item, recipe.samples(), recipe.entry());
            }
            markDirty();
        }
        setLit(world, pos, state, status == STATUS_WORKING);
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return world == null || world.getRecipeManager().listAllOfType(ModRecipes.ANALYSIS).stream()
                .anyMatch(r -> r.ingredient().test(stack));
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
        return new SampleAnalyzerScreenHandler(syncId, playerInventory, this, properties);
    }
}
