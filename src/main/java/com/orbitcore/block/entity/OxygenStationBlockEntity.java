package com.orbitcore.block.entity;

import com.orbitcore.fluid.FluidTank;
import com.orbitcore.item.SpacesuitItem;
import com.orbitcore.registry.ModBlockEntities;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.screen.OxygenStationScreenHandler;
import com.orbitcore.survival.Oxygen;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * Кислородная станция: газифицирует жидкий кислород и заправляет баллоны
 * и нагрудник скафандра — в слоте или прямо на игроке, который стоит рядом.
 */
public class OxygenStationBlockEntity extends MachineBlockEntity {
    public static final int BUCKET_IN = 0;
    public static final int BUCKET_OUT = 1;
    public static final int FILL = 2;
    /** Единиц кислорода за тик и энергии на каждую. */
    public static final int FILL_PER_TICK = 10;
    public static final int ENERGY_PER_UNIT = 2;
    public static final double REACH = 2.5;

    private static final int[] INPUT_SLOTS = {BUCKET_IN, FILL};
    private static final int[] OUTPUT_SLOTS = {BUCKET_OUT, FILL};

    public final SimpleEnergyStorage energy = new SimpleEnergyStorage(10_000, 128, 0) {
        @Override
        protected void onFinalCommit() {
            markDirty();
        }
    };
    public final FluidTank oxygen = new FluidTank(ModFluids.LIQUID_OXYGEN, 8000, this::markDirty);
    public final Storage<FluidVariant> pipes = FilteringStorage.insertOnlyOf(oxygen);
    private int refillSoundCooldown;
    /** Заправляет ли станция кого-то рядом (проверяется раз в 10 тиков). */
    private boolean refilling;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            ItemStack fill = items.get(FILL);
            return switch (index) {
                case 0 -> (int) energy.amount;
                case 1 -> (int) energy.capacity;
                case 2 -> oxygen.amountMb();
                case 3 -> oxygen.capacityMb();
                case 4 -> Oxygen.stored(fill);
                case 5 -> Oxygen.capacity(fill);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int size() {
            return 6;
        }
    };

    public OxygenStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OXYGEN_STATION, pos, state, 3);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
        oxygen.drainBucketFrom(items, BUCKET_IN, BUCKET_OUT, ModFluids.LIQUID_OXYGEN_BUCKET);
        boolean working = fill(items.get(FILL), FILL_PER_TICK) > 0;
        if (world.getTime() % 10 == 0) {
            refilling = false;
            Box area = new Box(pos).expand(REACH);
            for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, area, SpacesuitItem::isWearingHelmet)) {
                refilling |= refillPlayer(player);
            }
            if (refilling && refillSoundCooldown <= 0) {
                world.playSound(null, pos, ModSounds.OXYGEN_REFILL, SoundCategory.BLOCKS, 0.8f, 1.0f);
                refillSoundCooldown = 4;
            }
            refillSoundCooldown--;
        }
        setLit(world, pos, state, working || refilling);
    }

    /** Заправить скафандр и баллоны игрока: за раз — столько же, сколько слот получает за 10 тиков. */
    private boolean refillPlayer(PlayerEntity player) {
        int budget = FILL_PER_TICK * 10;
        budget -= fill(player.getEquippedStack(EquipmentSlot.CHEST), budget);
        for (ItemStack stack : player.getInventory().main) {
            if (budget <= 0) {
                break;
            }
            budget -= fill(stack, budget);
        }
        return budget < FILL_PER_TICK * 10;
    }

    /** Перелить кислород из бака в предмет; возвращает, сколько перелили. */
    private int fill(ItemStack stack, int max) {
        int space = Oxygen.capacity(stack) - Oxygen.stored(stack);
        int amount = Math.min(Math.min(space, max), Math.min(oxygen.amountMb(), (int) (energy.amount / ENERGY_PER_UNIT)));
        if (amount <= 0) {
            return 0;
        }
        Oxygen.fill(stack, amount);
        oxygen.drainMb(amount);
        energy.amount -= (long) amount * ENERGY_PER_UNIT;
        markDirty();
        return amount;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == BUCKET_IN && stack.isOf(ModFluids.LIQUID_OXYGEN_BUCKET)
                || slot == FILL && Oxygen.capacity(stack) > 0;
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        // Полный баллон воронка забирает снизу, пустой остаётся заправляться
        return slot == BUCKET_OUT || slot == FILL && Oxygen.stored(stack) >= Oxygen.capacity(stack);
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
        return new OxygenStationScreenHandler(syncId, playerInventory, this, properties);
    }
}
