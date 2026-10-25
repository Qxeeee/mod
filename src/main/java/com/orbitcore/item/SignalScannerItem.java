package com.orbitcore.item;

import com.orbitcore.story.HoloTablets;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * Сканер сигналов. В руке слышит, насколько близко живой голографический планшет.
 * Если зажать ПКМ и три секунды стоять неподвижно, сканер расшифровывает сигнал
 * и показывает, откуда он идёт. Сдвинулся — сигнал потерян, начинай заново.
 */
public class SignalScannerItem extends Item {
    public static final int DECODE_TICKS = 60;
    /** Насколько можно сдвинуться за время расшифровки. */
    private static final double STILL = 0.6;

    public SignalScannerItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            NbtCompound nbt = stack.getOrCreateNbt();
            nbt.putDouble("DecodeX", user.getX());
            nbt.putDouble("DecodeZ", user.getZ());
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient || !(user instanceof ServerPlayerEntity player) || stack.getNbt() == null) {
            return;
        }
        double dx = player.getX() - stack.getNbt().getDouble("DecodeX");
        double dz = player.getZ() - stack.getNbt().getDouble("DecodeZ");
        if (dx * dx + dz * dz > STILL * STILL) {
            player.stopUsingItem();
            player.sendMessage(Text.translatable("message.orbitcore.scanner.lost").formatted(Formatting.GOLD), true);
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            HoloTablets.decode(player);
        }
        return stack;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return DECODE_TICKS;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }
}
