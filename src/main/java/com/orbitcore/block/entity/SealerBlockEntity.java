package com.orbitcore.block.entity;

import com.orbitcore.block.MachineBlock;
import com.orbitcore.fluid.FluidTank;
import com.orbitcore.registry.ModBlockEntities;
import com.orbitcore.registry.ModFluids;
import com.orbitcore.registry.ModSounds;
import com.orbitcore.screen.SealerScreenHandler;
import com.orbitcore.survival.SealedAreas;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * Герметизатор: раз в две секунды проверяет, замкнуто ли помещение перед его
 * решёткой (лицевой стороной), и наполняет его воздухом из жидкого кислорода. Если есть дыра, воздух
 * со свистом уходит через неё — там видно струю пара.
 */
public class SealerBlockEntity extends MachineBlockEntity {
    public static final int BUCKET_IN = 0;
    public static final int BUCKET_OUT = 1;
    public static final int ENERGY_PER_TICK = 16;
    public static final int MAX_VOLUME = 2000;
    public static final int SCAN_INTERVAL = 40;
    /** Сколько блоков воздуха даёт 1 мБ жидкого кислорода при наполнении. */
    public static final int BLOCKS_PER_MB = 4;
    /** Подпитка: 1 мБ на столько блоков объёма за проверку (воздух понемногу уходит). */
    public static final int UPKEEP_BLOCKS_PER_MB = 500;

    public enum Status {
        NO_ENERGY, NO_OXYGEN, BLOCKED, LEAK, SEALED;

        public String key() {
            return "gui.orbitcore.sealer." + name().toLowerCase();
        }
    }

    private static final int[] INPUT_SLOTS = {BUCKET_IN};
    private static final int[] OUTPUT_SLOTS = {BUCKET_OUT};

    public final SimpleEnergyStorage energy = new SimpleEnergyStorage(10_000, 128, 0) {
        @Override
        protected void onFinalCommit() {
            markDirty();
        }
    };
    public final FluidTank oxygen = new FluidTank(ModFluids.LIQUID_OXYGEN, 4000, this::markDirty);
    public final Storage<FluidVariant> pipes = FilteringStorage.insertOnlyOf(oxygen);

    private Status status = Status.NO_ENERGY;
    private int volume;
    @Nullable
    private BlockPos leak;
    @Nullable
    private Direction leakDirection;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) energy.amount;
                case 1 -> (int) energy.capacity;
                case 2 -> oxygen.amountMb();
                case 3 -> oxygen.capacityMb();
                case 4 -> status.ordinal();
                case 5 -> volume;
                case 6 -> leak == null ? 0 : 1;
                // Смещение дыры от герметизатора: в окне показываем, куда идти
                case 7 -> leak == null ? 0 : leak.getX() - pos.getX();
                case 8 -> leak == null ? 0 : leak.getY() - pos.getY();
                case 9 -> leak == null ? 0 : leak.getZ() - pos.getZ();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int size() {
            return 10;
        }
    };

    public SealerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEALER, pos, state, 2);
    }

    public Status status() {
        return status;
    }

    public int volume() {
        return volume;
    }

    @Nullable
    public BlockPos leak() {
        return leak;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state) {
        oxygen.drainBucketFrom(items, BUCKET_IN, BUCKET_OUT, ModFluids.LIQUID_OXYGEN_BUCKET);
        if (status == Status.SEALED) {
            if (energy.amount >= ENERGY_PER_TICK) {
                energy.amount -= ENERGY_PER_TICK;
            } else {
                depressurize(world, Status.NO_ENERGY);
            }
        }
        // Проверки разных герметизаторов разнесены по времени
        if (Math.floorMod(world.getTime() + pos.hashCode(), SCAN_INTERVAL) == 0) {
            rescan(world, pos);
        }
        if (status == Status.LEAK && leak != null && world instanceof ServerWorld server && world.getTime() % 5 == 0) {
            leakEffects(server);
        }
        setLit(world, pos, state, status == Status.SEALED);
    }

    /** Проверить помещение и, если оно замкнуто, наполнить или подпитать его воздухом. */
    public void rescan(World world, BlockPos pos) {
        if (energy.amount < ENERGY_PER_TICK) {
            depressurize(world, Status.NO_ENERGY);
            return;
        }
        Direction outlet = getCachedState().get(MachineBlock.FACING);
        if (SealedAreas.isAirtight(world, pos.offset(outlet), world.getBlockState(pos.offset(outlet)))) {
            depressurize(world, Status.BLOCKED);
            return;
        }
        SealedAreas.Scan scan = SealedAreas.scan(world, pos, outlet, MAX_VOLUME);
        if (!scan.sealed()) {
            leak = scan.leak();
            leakDirection = scan.leakDirection();
            depressurize(world, Status.LEAK);
            return;
        }
        leak = null;
        boolean wasSealed = status == Status.SEALED;
        int cost = wasSealed
                ? Math.max(1, scan.volume() / UPKEEP_BLOCKS_PER_MB)
                : Math.max(1, scan.volume() / BLOCKS_PER_MB);
        if (oxygen.amountMb() < cost) {
            depressurize(world, Status.NO_OXYGEN);
            return;
        }
        oxygen.drainMb(cost);
        volume = scan.volume();
        status = Status.SEALED;
        SealedAreas.put(world, pos, scan.cells());
        if (!wasSealed) {
            world.playSound(null, pos, ModSounds.SEALER_PRESSURIZE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        markDirty();
    }

    private void depressurize(World world, Status reason) {
        if (status == Status.SEALED && reason == Status.LEAK) {
            world.playSound(null, pos, ModSounds.SEALER_LEAK, SoundCategory.BLOCKS, 1.0f, 0.8f);
        }
        if (reason != Status.LEAK) {
            leak = null;
        }
        status = reason;
        volume = 0;
        SealedAreas.remove(world, pos);
        markDirty();
    }

    /** Струя пара из дыры и свист, пока утечку не заделают. */
    private void leakEffects(ServerWorld world) {
        Direction out = leakDirection == null ? Direction.UP : leakDirection;
        double x = leak.getX() + 0.5;
        double y = leak.getY() + 0.5;
        double z = leak.getZ() + 0.5;
        for (int i = 0; i < 4; i++) {
            // При count = 0 смещения задают скорость частицы: струя бьёт наружу
            double spread = (world.random.nextDouble() - 0.5) * 0.15;
            world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 0,
                    out.getOffsetX() + spread, out.getOffsetY() + spread, out.getOffsetZ() + spread, 0.25);
        }
        if (world.getTime() % 40 == 0) {
            world.playSound(null, leak, ModSounds.SEALER_LEAK, SoundCategory.BLOCKS, 0.7f, 1.2f);
        }
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        if (world != null && !world.isClient) {
            SealedAreas.remove(world, pos);
        }
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == BUCKET_IN && stack.isOf(ModFluids.LIQUID_OXYGEN_BUCKET);
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == BUCKET_OUT;
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
        return new SealerScreenHandler(syncId, playerInventory, this, properties);
    }
}
