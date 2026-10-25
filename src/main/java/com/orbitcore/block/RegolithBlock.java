package com.orbitcore.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Лунный реголит — тонкая пыль. На ней остаются следы ботинок, и без ветра
 * они не исчезают никогда (как следы «Аполлонов»).
 */
public class RegolithBlock extends Block {
    public static final BooleanProperty TRODDEN = BooleanProperty.of("trodden");

    public RegolithBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(TRODDEN, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(TRODDEN);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient && !state.get(TRODDEN) && entity instanceof LivingEntity && !entity.isSneaking()) {
            world.setBlockState(pos, state.with(TRODDEN, true), Block.NOTIFY_LISTENERS);
        }
        super.onSteppedOn(world, pos, state, entity);
    }
}
