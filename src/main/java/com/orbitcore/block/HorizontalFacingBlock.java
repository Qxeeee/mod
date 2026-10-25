package com.orbitcore.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Блок без блок-сущности, который при установке поворачивается к игроку.
 * Умеет поворачивать свою форму (для блоков не во весь куб).
 */
public abstract class HorizontalFacingBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    protected HorizontalFacingBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    /**
     * Поворачивает форму, нарисованную для взгляда на север, в сторону {@code facing}.
     * Координаты — в шестнадцатых долях блока, как в моделях.
     */
    protected static VoxelShape rotated(Direction facing, double[]... boxes) {
        VoxelShape shape = VoxelShapes.empty();
        for (double[] b : boxes) {
            double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
            double[] r = switch (facing) {
                case SOUTH -> new double[]{16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1};
                case WEST -> new double[]{z1, y1, 16 - x2, z2, y2, 16 - x1};
                case EAST -> new double[]{16 - z2, y1, x1, 16 - z1, y2, x2};
                default -> b;
            };
            shape = VoxelShapes.union(shape, Block.createCuboidShape(r[0], r[1], r[2], r[3], r[4], r[5]));
        }
        return shape;
    }
}
