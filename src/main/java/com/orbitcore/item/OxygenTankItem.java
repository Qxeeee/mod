package com.orbitcore.item;

import com.orbitcore.survival.Oxygen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Запасной кислородный баллон: скафандр берёт из него, когда кончится свой запас. */
public class OxygenTankItem extends Item implements OxygenContainer {
    public static final int CAPACITY = 900;

    public OxygenTankItem(Settings settings) {
        super(settings);
    }

    @Override
    public int oxygenCapacity() {
        return CAPACITY;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13.0f * Oxygen.stored(stack) / CAPACITY);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0x5FB4E6;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        int oxygen = Oxygen.stored(stack);
        tooltip.add(Text.translatable("tooltip.orbitcore.oxygen", oxygen, CAPACITY, Oxygen.time(oxygen)).formatted(Formatting.AQUA));
    }
}
