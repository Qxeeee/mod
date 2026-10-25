package com.orbitcore.block.entity;

import com.orbitcore.recipe.MetallurgyRecipe;
import com.orbitcore.registry.ModBlockEntities;
import com.orbitcore.registry.ModRecipes;
import com.orbitcore.screen.MetallurgicalFurnaceScreenHandler;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Металлургическая печь. Топливо постепенно разогревает печь; плавка идёт,
 * только когда температура не ниже той, что требует рецепт. Без топлива печь остывает.
 */
public class MetallurgicalFurnaceBlockEntity extends MachineBlockEntity {
    public static final int INPUT = 0;
    public static final int REDUCTANT = 1;
    public static final int FUEL = 2;
    public static final int OUTPUT = 3;

    public static final int AMBIENT_HEAT = 20;
    public static final int MAX_HEAT = 1800;
    private static final int HEATING_RATE = 3;
    private static final int COOLING_RATE = 2;

    private static final int[] TOP_SLOTS = {INPUT, REDUCTANT};
    private static final int[] SIDE_SLOTS = {FUEL, REDUCTANT};
    private static final int[] BOTTOM_SLOTS = {OUTPUT};

    private int heat = AMBIENT_HEAT;
    private int burnTime;
    private int fuelTime;
    private int progress;
    private int maxProgress;
    private int requiredHeat;
    @Nullable
    private Identifier currentRecipe;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> heat;
                case 1 -> burnTime;
                case 2 -> fuelTime;
                case 3 -> progress;
                case 4 -> maxProgress;
                case 5 -> requiredHeat;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> heat = value;
                case 1 -> burnTime = value;
                case 2 -> fuelTime = value;
                case 3 -> progress = value;
                case 4 -> maxProgress = value;
                case 5 -> requiredHeat = value;
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return 6;
        }
    };

    public MetallurgicalFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.METALLURGICAL_FURNACE, pos, state, 4);
    }

    public static int fuelTime(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Integer time = FuelRegistry.INSTANCE.get(stack.getItem());
        return time == null ? 0 : time;
    }

    public int getHeat() {
        return heat;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
        Optional<MetallurgyRecipe> match = world.getRecipeManager().getFirstMatch(ModRecipes.METALLURGY, this, world);
        MetallurgyRecipe recipe = match.filter(r -> canOutput(OUTPUT, r.result())).orElse(null);

        if (burnTime > 0) {
            burnTime--;
        }
        if (burnTime == 0 && recipe != null) {
            igniteFuel();
        }

        heat = burnTime > 0 ? Math.min(MAX_HEAT, heat + HEATING_RATE) : Math.max(AMBIENT_HEAT, heat - COOLING_RATE);

        if (recipe == null) {
            progress = 0;
            maxProgress = 0;
            requiredHeat = 0;
            currentRecipe = null;
        } else {
            if (!recipe.id().equals(currentRecipe)) {
                currentRecipe = recipe.id();
                progress = 0;
            }
            maxProgress = recipe.time();
            requiredHeat = recipe.heat();
            if (heat >= requiredHeat && ++progress >= maxProgress) {
                items.get(INPUT).decrement(1);
                items.get(REDUCTANT).decrement(1);
                addOutput(OUTPUT, recipe.craft(this, world.getRegistryManager()));
                progress = 0;
            }
        }

        if (burnTime > 0 || heat > AMBIENT_HEAT) {
            markDirty();
        }
        setLit(world, pos, state, burnTime > 0);
    }

    private void igniteFuel() {
        ItemStack fuel = items.get(FUEL);
        int time = fuelTime(fuel);
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
        return switch (slot) {
            case INPUT -> world == null || world.getRecipeManager().listAllOfType(ModRecipes.METALLURGY).stream()
                    .anyMatch(r -> r.ingredient().test(stack));
            case REDUCTANT -> stack.isIn(ItemTags.COALS);
            case FUEL -> fuelTime(stack) > 0;
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
        return slot == OUTPUT;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Heat", heat);
        nbt.putInt("BurnTime", burnTime);
        nbt.putInt("FuelTime", fuelTime);
        nbt.putInt("Progress", progress);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        heat = Math.max(AMBIENT_HEAT, nbt.getInt("Heat"));
        burnTime = nbt.getInt("BurnTime");
        fuelTime = nbt.getInt("FuelTime");
        progress = nbt.getInt("Progress");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new MetallurgicalFurnaceScreenHandler(syncId, playerInventory, this, properties);
    }
}
