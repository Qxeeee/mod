package com.orbitcore.item;

import com.orbitcore.survival.Oxygen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Скафандр Т1 «Лунный». Защищает от вакуума и лунных перепадов температуры,
 * только если надеты все четыре части. В нагруднике — встроенный баллон.
 */
public class SpacesuitItem extends ArmorItem implements OxygenContainer {
    public static final int CHEST_OXYGEN = 600;
    /** Рабочий диапазон температур скафандра, °C. */
    public static final int MIN_TEMPERATURE = -180;
    public static final int MAX_TEMPERATURE = 150;

    public SpacesuitItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    public int oxygenCapacity() {
        return getType() == Type.CHESTPLATE ? CHEST_OXYGEN : 0;
    }

    public static boolean isWearingFullSuit(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(entity.getEquippedStack(slot).getItem() instanceof SpacesuitItem)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isWearingHelmet(LivingEntity entity) {
        return entity.getEquippedStack(EquipmentSlot.HEAD).getItem() instanceof SpacesuitItem;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (getType() == Type.CHESTPLATE) {
            int oxygen = Oxygen.stored(stack);
            tooltip.add(Text.translatable("tooltip.orbitcore.oxygen", oxygen, CHEST_OXYGEN, Oxygen.time(oxygen))
                    .formatted(Formatting.AQUA));
        }
        tooltip.add(Text.translatable("tooltip.orbitcore.spacesuit").formatted(Formatting.GRAY));
    }
}
