package com.orbitcore.item;

import com.orbitcore.entity.RoverEntity;
import com.orbitcore.registry.ModEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Луноход в инвентаре: ПКМ по грунту ставит его, заряд батареи сохраняется в предмете. */
public class RoverItem extends Item {
    public RoverItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos().offset(context.getSide());
        RoverEntity rover = ModEntities.ROVER.create(world);
        if (rover == null) {
            return ActionResult.FAIL;
        }
        PlayerEntity player = context.getPlayer();
        rover.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player != null ? player.getYaw() : 0, 0);
        if (!world.isSpaceEmpty(rover, rover.getBoundingBox())) {
            return ActionResult.FAIL;
        }
        if (!world.isClient) {
            ItemStack stack = context.getStack();
            if (stack.hasNbt() && stack.getNbt().contains("Battery")) {
                rover.setBattery(stack.getNbt().getFloat("Battery"));
            }
            world.spawnEntity(rover);
            world.playSound(null, pos, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.NEUTRAL, 1.0f, 0.8f);
            if (player == null || !player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }
        return ActionResult.success(world.isClient);
    }
}
