package com.orbitcore.block;

import com.orbitcore.network.OrbitNetworking;
import com.orbitcore.science.Observatory;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
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
 * Телескоп на треноге. Ночью при ясном небе открывает окуляр — экран наблюдения.
 */
public class TelescopeBlock extends HorizontalFacingBlock {
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Type.HORIZONTAL) {
            SHAPES.put(facing, rotated(facing,
                    new double[]{3, 0, 3, 13, 9, 13},     // тренога
                    new double[]{5, 8, 1, 11, 15, 15}));  // труба
        }
    }

    public TelescopeBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        Text problem = Observatory.whyCannotObserve(world, pos);
        if (problem != null) {
            player.sendMessage(problem.copy().formatted(Formatting.GRAY), true);
        } else {
            OrbitNetworking.openScreen((ServerPlayerEntity) player, OrbitNetworking.OPEN_TELESCOPE, pos);
        }
        return ActionResult.CONSUME;
    }
}
