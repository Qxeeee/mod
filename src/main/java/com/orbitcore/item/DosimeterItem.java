package com.orbitcore.item;

import com.orbitcore.registry.ModSounds;
import com.orbitcore.survival.LifeSupport;
import com.orbitcore.survival.LifeSupportComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Дозиметр: показывает накопленную дозу и фон. Пока он в руке или на панели
 * быстрого доступа, показания видны и без шлема.
 */
public class DosimeterItem extends Item {
    public DosimeterItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient) {
            LifeSupportComponent data = LifeSupport.get(player);
            player.sendMessage(reading(data), true);
            // Чем выше фон, тем чаще щелчки
            float pitch = 0.8f + Math.min(1.2f, data.doseRate);
            world.playSound(null, player.getBlockPos(), ModSounds.DOSIMETER_CLICK, SoundCategory.PLAYERS, 0.6f, pitch);
        }
        player.getItemCooldownManager().set(this, 10);
        return TypedActionResult.success(stack, world.isClient);
    }

    /** «Доза: 120 мЗв · фон 0,50 мЗв/с» с цветом по степени опасности. */
    public static Text reading(LifeSupportComponent data) {
        Formatting color = data.dose >= LifeSupport.DOSE_WEAKNESS ? Formatting.RED
                : data.dose >= LifeSupport.DOSE_HUNGER ? Formatting.GOLD : Formatting.GREEN;
        return Text.translatable("message.orbitcore.dosimeter", Math.round(data.dose),
                String.format("%.2f", data.doseRate)).formatted(color);
    }
}
