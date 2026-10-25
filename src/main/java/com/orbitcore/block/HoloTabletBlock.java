package com.orbitcore.block;

import com.orbitcore.block.entity.HoloTabletBlockEntity;
import com.orbitcore.story.HoloTablets;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Голографический планшет: тонкая плита, над которой мерцает голограмма.
 * Пока планшет «живой» ({@link #ACTIVE}), он источает сигнал — его ловит сканер.
 * Запись прочитали — сигнал иссяк, голограмма гаснет, но перечитать можно.
 */
public class HoloTabletBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");
    private static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 3, 14, 2, 13);

    public HoloTabletBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH).with(ACTIVE, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new HoloTabletBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(pos) instanceof HoloTabletBlockEntity tablet) {
            HoloTablets.read(serverPlayer, pos, tablet);
        }
        return ActionResult.success(world.isClient);
    }

    /** Живой планшет выдаёт себя издали: тонкий столб искр над голограммой. */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (state.get(ACTIVE) && random.nextInt(3) == 0) {
            world.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + random.nextGaussian() * 0.05, pos.getY() + 1.6,
                    pos.getZ() + 0.5 + random.nextGaussian() * 0.05, 0, 0.05 + random.nextDouble() * 0.05, 0);
        }
    }
}
