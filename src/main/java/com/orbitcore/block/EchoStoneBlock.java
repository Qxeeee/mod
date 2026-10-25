package com.orbitcore.block;

import com.orbitcore.world.EchoEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Эховый камень — материал Строителей. Его нельзя сломать ничем, а если коснуться
 * монолита рукой, Эхо отвечает.
 */
public class EchoStoneBlock extends Block {
    public EchoStoneBlock(Settings settings) {
        super(settings);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            EchoEvents.touch(serverPlayer);
        }
        return ActionResult.success(world.isClient);
    }
}
