package com.orbitcore.item;

import com.orbitcore.block.LaunchPadBlock;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModEntities;
import com.orbitcore.rocket.RocketDesign;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Собранная ракета в виде предмета. Ставится на центр стартового стола 3×3 и превращается в ракету.
 */
public class RocketItem extends Item {
    private static final String TAG = "Rocket";

    public RocketItem(Settings settings) {
        super(settings.maxCount(1));
    }

    public static ItemStack create(Item item, RocketDesign design, int kerosene, int oxygen) {
        ItemStack stack = new ItemStack(item);
        NbtCompound rocket = new NbtCompound();
        rocket.put("Parts", design.toNbt());
        rocket.putInt("Kerosene", kerosene);
        rocket.putInt("Oxygen", oxygen);
        stack.getOrCreateNbt().put(TAG, rocket);
        return stack;
    }

    public static RocketDesign design(ItemStack stack) {
        NbtCompound rocket = stack.getSubNbt(TAG);
        return RocketDesign.fromNbt(rocket == null ? new NbtList() : rocket.getList("Parts", NbtElement.COMPOUND_TYPE));
    }

    public static int kerosene(ItemStack stack) {
        NbtCompound rocket = stack.getSubNbt(TAG);
        return rocket == null ? 0 : rocket.getInt("Kerosene");
    }

    public static int oxygen(ItemStack stack) {
        NbtCompound rocket = stack.getSubNbt(TAG);
        return rocket == null ? 0 : rocket.getInt("Oxygen");
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pad = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        if (!world.getBlockState(pad).isOf(ModBlocks.LAUNCH_PAD)) {
            return ActionResult.PASS;
        }
        if (!LaunchPadBlock.isComplete(world, pad)) {
            message(player, "message.orbitcore.rocket.pad_incomplete");
            return ActionResult.FAIL;
        }
        RocketDesign design = design(context.getStack());
        // Ракета шириной во весь стол: над ним должно быть свободно 3×3 блока
        Box above = new Box(pad.up()).stretch(0, Math.max(1, design.height()), 0).expand(1, 0, 1);
        if (!world.getEntitiesByClass(RocketEntity.class, above.expand(1, 0, 1), e -> true).isEmpty()) {
            message(player, "message.orbitcore.rocket.pad_occupied");
            return ActionResult.FAIL;
        }
        if (!world.isSpaceEmpty(above.contract(0.05))) {
            message(player, "message.orbitcore.rocket.no_room");
            return ActionResult.FAIL;
        }
        if (!world.isClient) {
            RocketEntity rocket = ModEntities.ROCKET.create(world);
            if (rocket == null) {
                return ActionResult.FAIL;
            }
            rocket.refreshPositionAndAngles(pad.getX() + 0.5, pad.getY() + 1, pad.getZ() + 0.5, 0, 0);
            rocket.setDesign(design);
            rocket.setFuel(kerosene(context.getStack()), oxygen(context.getStack()));
            world.spawnEntity(rocket);
            world.playSound(null, pad, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6f, 0.6f);
            if (player == null || !player.getAbilities().creativeMode) {
                context.getStack().decrement(1);
            }
        }
        return ActionResult.success(world.isClient);
    }

    private static void message(@Nullable PlayerEntity player, String key) {
        if (player != null && !player.getWorld().isClient) {
            player.sendMessage(Text.translatable(key).formatted(Formatting.RED), true);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        RocketDesign design = design(stack);
        tooltip.add(Text.translatable("rocket.orbitcore.stat.summary", design.stages().size(),
                String.format("%.1f", design.height()), String.format("%.1f", design.mass(1.0))).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("rocket.orbitcore.stat.delta_v", (int) design.deltaV(1.0),
                String.format("%.2f", design.liftoffTwr(1.0))).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.orbitcore.assembled_rocket.place").formatted(Formatting.DARK_GRAY));
    }
}
