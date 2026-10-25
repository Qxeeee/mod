package com.orbitcore.block;

import com.orbitcore.screen.EngineeringTableScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Верстак инженера: сетка 3×3 для деталей и машин. Как и обычный верстак,
 * ничего не хранит — предметы возвращаются игроку при закрытии.
 */
public class EngineeringTableBlock extends HorizontalFacingBlock {
    private static final Text TITLE = Text.translatable("container.orbitcore.engineering_table");

    public EngineeringTableBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            player.openHandledScreen(state.createScreenHandlerFactory(world, pos));
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        return new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new EngineeringTableScreenHandler(syncId, inventory, ScreenHandlerContext.create(world, pos)),
                TITLE);
    }
}
