package com.orbitcore.block;

import com.orbitcore.network.OrbitNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;

/**
 * ЦУП — пульт космической программы: исследования, небесные тела, Космопедия.
 */
public class MissionControlBlock extends HorizontalFacingBlock {
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Type.HORIZONTAL) {
            SHAPES.put(facing, rotated(facing,
                    new double[]{0, 0, 2, 16, 10, 16},    // тумба
                    new double[]{1, 10, 11, 15, 16, 15})); // мониторы
        }
    }

    public MissionControlBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            OrbitNetworking.openScreen((ServerPlayerEntity) player, OrbitNetworking.OPEN_MISSION_CONTROL, pos);
        }
        return ActionResult.success(world.isClient);
    }
}
