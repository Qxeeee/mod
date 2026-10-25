package com.orbitcore.block.entity;

import com.orbitcore.fluid.FluidTank;
import com.orbitcore.registry.ModBlockEntities;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.registry.ModItems;
import com.orbitcore.screen.RegolithProcessorScreenHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
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

import java.util.Map;

/**
 * Переработчик реголита — первая «добыча на месте». Прокаливает лунный грунт:
 * из реголита выходит гелий-3, который солнечный ветер копил миллиарды лет
 * (в тёмном реголите морей его больше), из ильменита — кислород и железо,
 * из ледяного реголита — лёд для воды.
 */
public class RegolithProcessorBlockEntity extends MachineBlockEntity {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int CANISTER_IN = 2;
    public static final int CANISTER_OUT = 3;
    public static final int BUCKET_IN = 4;
    public static final int BUCKET_OUT = 5;
    public static final int HELIUM_CAPACITY = 4000;
    /** Столько гелия-3 (в условных мБ) помещается в один баллон. */
    public static final int CANISTER_HELIUM = 1000;

    /**
     * Что даёт сырьё: время, энергия за тик, гелий-3, жидкий кислород и побочный продукт.
     */
    public record Process(int time, int energyPerTick, int helium, int oxygen, Item byproduct, int byproductCount, float byproductChance) {
    }

    private static final Map<Item, Process> PROCESSES = Map.of(
            ModBlocks.REGOLITH.asItem(), new Process(100, 24, 20, 0, Items.IRON_NUGGET, 1, 0.15f),
            ModBlocks.MARIA_REGOLITH.asItem(), new Process(100, 24, 35, 0, Items.IRON_NUGGET, 1, 0.25f),
            ModItems.RAW_ILMENITE, new Process(160, 32, 0, 250, Items.IRON_NUGGET, 2, 1.0f),
            ModBlocks.ICE_REGOLITH.asItem(), new Process(80, 16, 5, 0, Items.ICE, 1, 1.0f));

    private static final int[] TOP_SLOTS = {INPUT, CANISTER_IN, BUCKET_IN};
    private static final int[] BOTTOM_SLOTS = {OUTPUT, CANISTER_OUT, BUCKET_OUT};

    public final SimpleEnergyStorage energy = new SimpleEnergyStorage(10_000, 128, 0) {
        @Override
        protected void onFinalCommit() {
            markDirty();
        }
    };
    public final FluidTank oxygen = new FluidTank(ModFluids.LIQUID_OXYGEN, 4000, this::markDirty);
    public final Storage<FluidVariant> pipes = FilteringStorage.extractOnlyOf(oxygen);

    private int helium;
    private int progress;
    private int maxProgress;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) energy.amount;
                case 1 -> (int) energy.capacity;
                case 2 -> progress;
                case 3 -> maxProgress;
                case 4 -> helium;
                case 5 -> HELIUM_CAPACITY;
                case 6 -> oxygen.amountMb();
                case 7 -> oxygen.capacityMb();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int size() {
            return 8;
        }
    };

    public RegolithProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REGOLITH_PROCESSOR, pos, state, 6);
    }

    @Nullable
    public static Process process(ItemStack stack) {
        return PROCESSES.get(stack.getItem());
    }

    public int helium() {
        return helium;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
        oxygen.fillBucketInto(items, BUCKET_IN, BUCKET_OUT, ModFluids.LIQUID_OXYGEN_BUCKET);
        fillCanister();

        Process process = process(items.get(INPUT));
        boolean working = false;
        if (process == null || !fits(process)) {
            progress = 0;
            maxProgress = 0;
        } else {
            maxProgress = process.time();
            if (energy.amount >= process.energyPerTick()) {
                energy.amount -= process.energyPerTick();
                working = true;
                if (++progress >= maxProgress) {
                    finish(world, process);
                    progress = 0;
                }
                markDirty();
            }
        }
        setLit(world, pos, state, working);
    }

    /** Хватит ли места для всего, что получится. */
    private boolean fits(Process process) {
        return helium + process.helium() <= HELIUM_CAPACITY
                && oxygen.spaceMb() >= process.oxygen()
                && canOutput(OUTPUT, new ItemStack(process.byproduct(), process.byproductCount()));
    }

    private void finish(World world, Process process) {
        items.get(INPUT).decrement(1);
        helium += process.helium();
        if (process.oxygen() > 0) {
            oxygen.fillMb(process.oxygen());
        }
        if (world.random.nextFloat() < process.byproductChance()) {
            addOutput(OUTPUT, new ItemStack(process.byproduct(), process.byproductCount()));
        }
    }

    /** Пустой баллон в слоте наполняется гелием-3, как только его наберётся на целый баллон. */
    private void fillCanister() {
        if (helium < CANISTER_HELIUM || !items.get(CANISTER_IN).isOf(ModItems.GAS_CANISTER)) {
            return;
        }
        ItemStack full = new ItemStack(ModItems.HELIUM3_CANISTER);
        if (!canOutput(CANISTER_OUT, full)) {
            return;
        }
        items.get(CANISTER_IN).decrement(1);
        addOutput(CANISTER_OUT, full);
        helium -= CANISTER_HELIUM;
        markDirty();
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return switch (slot) {
            case INPUT -> process(stack) != null;
            case CANISTER_IN -> stack.isOf(ModItems.GAS_CANISTER);
            case BUCKET_IN -> stack.isOf(Items.BUCKET);
            default -> false;
        };
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return side == Direction.DOWN ? BOTTOM_SLOTS : TOP_SLOTS;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == OUTPUT || slot == CANISTER_OUT || slot == BUCKET_OUT;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putLong("Energy", energy.amount);
        nbt.putInt("Progress", progress);
        nbt.putInt("Helium", helium);
        nbt.put("Oxygen", oxygen.variant.toNbt());
        nbt.putLong("OxygenAmount", oxygen.amount);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        energy.amount = Math.min(energy.capacity, nbt.getLong("Energy"));
        progress = nbt.getInt("Progress");
        helium = nbt.getInt("Helium");
        oxygen.variant = FluidVariant.fromNbt(nbt.getCompound("Oxygen"));
        oxygen.amount = nbt.getLong("OxygenAmount");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new RegolithProcessorScreenHandler(syncId, playerInventory, this, properties);
    }
}
