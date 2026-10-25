package com.orbitcore.fluid;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

import java.util.function.Supplier;

/**
 * Жидкость мода, которую можно вылить из ведра. Устроена как вода, но не бесконечная
 * и растекается чуть медленнее. Для каждой жидкости два вида: источник и течение.
 */
public abstract class OrbitFluid extends FlowableFluid {
    private final Supplier<FlowableFluid> still;
    private final Supplier<FlowableFluid> flowing;
    private final Supplier<Item> bucket;
    private final Supplier<Block> block;

    protected OrbitFluid(Supplier<FlowableFluid> still, Supplier<FlowableFluid> flowing, Supplier<Item> bucket, Supplier<Block> block) {
        this.still = still;
        this.flowing = flowing;
        this.bucket = bucket;
        this.block = block;
    }

    @Override
    public Fluid getStill() {
        return still.get();
    }

    @Override
    public Fluid getFlowing() {
        return flowing.get();
    }

    @Override
    public Item getBucketItem() {
        return bucket.get();
    }

    @Override
    public boolean matchesType(Fluid fluid) {
        return fluid == getStill() || fluid == getFlowing();
    }

    @Override
    protected boolean isInfinite(World world) {
        return false;
    }

    @Override
    protected void beforeBreakingBlock(WorldAccess world, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
        Block.dropStacks(state, world, pos, blockEntity);
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockView world, BlockPos pos, Fluid fluid, Direction direction) {
        return false;
    }

    @Override
    protected int getFlowSpeed(WorldView world) {
        return 4;
    }

    @Override
    protected int getLevelDecreasePerBlock(WorldView world) {
        return 1;
    }

    @Override
    public int getTickRate(WorldView world) {
        return 6;
    }

    @Override
    protected float getBlastResistance() {
        return 100.0f;
    }

    @Override
    protected BlockState toBlockState(FluidState state) {
        return block.get().getDefaultState().with(Properties.LEVEL_15, getBlockStateLevel(state));
    }

    public static class Flowing extends OrbitFluid {
        public Flowing(Supplier<FlowableFluid> still, Supplier<FlowableFluid> flowing, Supplier<Item> bucket, Supplier<Block> block) {
            super(still, flowing, bucket, block);
        }

        @Override
        protected void appendProperties(StateManager.Builder<Fluid, FluidState> builder) {
            super.appendProperties(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getLevel(FluidState state) {
            return state.get(LEVEL);
        }

        @Override
        public boolean isStill(FluidState state) {
            return false;
        }
    }

    public static class Still extends OrbitFluid {
        public Still(Supplier<FlowableFluid> still, Supplier<FlowableFluid> flowing, Supplier<Item> bucket, Supplier<Block> block) {
            super(still, flowing, bucket, block);
        }

        @Override
        public int getLevel(FluidState state) {
            return 8;
        }

        @Override
        public boolean isStill(FluidState state) {
            return true;
        }
    }
}
