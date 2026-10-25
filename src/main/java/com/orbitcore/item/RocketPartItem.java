package com.orbitcore.item;

import com.orbitcore.rocket.RocketPart;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Модуль ракеты. Характеристики показываются в подсказке.
 */
public class RocketPartItem extends Item {
    private final RocketPart part;

    public RocketPartItem(RocketPart part, Settings settings) {
        super(settings.maxCount(part.maxStack() > 1 ? 16 : 4));
        this.part = part;
    }

    public RocketPart part() {
        return part;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("rocket.orbitcore.stat.mass", String.format("%.1f", part.dryMass())).formatted(Formatting.GRAY));
        if (part.kind() == RocketPart.Kind.ENGINE) {
            tooltip.add(Text.translatable("rocket.orbitcore.stat.thrust", (int) part.thrust()).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("rocket.orbitcore.stat.isp", (int) part.isp()).formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("rocket.orbitcore.stat.cluster", part.maxStack()).formatted(Formatting.DARK_GRAY));
        }
        if (part.propellant() > 0) {
            tooltip.add(Text.translatable("rocket.orbitcore.stat.propellant", part.propellant()).formatted(Formatting.GRAY));
        }
    }
}
