package com.orbitcore.survival;

import com.orbitcore.item.OxygenContainer;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Запас кислорода в предметах: в нагруднике скафандра и в баллонах.
 * Одна единица — секунда дыхания; 1 мБ жидкого кислорода даёт одну единицу.
 */
public final class Oxygen {
    private static final String KEY = "Oxygen";

    private Oxygen() {
    }

    public static int capacity(ItemStack stack) {
        return stack.getItem() instanceof OxygenContainer container ? container.oxygenCapacity() : 0;
    }

    public static int stored(ItemStack stack) {
        if (capacity(stack) <= 0 || !stack.hasNbt()) {
            return 0;
        }
        return stack.getNbt().getInt(KEY);
    }

    public static void set(ItemStack stack, int amount) {
        stack.getOrCreateNbt().putInt(KEY, Math.max(0, Math.min(capacity(stack), amount)));
    }

    /** Заправить предмет; возвращает, сколько кислорода он принял. */
    public static int fill(ItemStack stack, int amount) {
        int accepted = Math.min(amount, capacity(stack) - stored(stack));
        if (accepted <= 0) {
            return 0;
        }
        set(stack, stored(stack) + accepted);
        return accepted;
    }

    /** Весь кислород, который игрок несёт с собой: в нагруднике и баллонах. */
    public static int total(PlayerEntity player) {
        int total = stored(player.getEquippedStack(EquipmentSlot.CHEST));
        for (ItemStack stack : player.getInventory().main) {
            total += stored(stack);
        }
        return total;
    }

    /** Ёмкость всех баков игрока, чтобы показать запас долей. */
    public static int totalCapacity(PlayerEntity player) {
        int total = capacity(player.getEquippedStack(EquipmentSlot.CHEST));
        for (ItemStack stack : player.getInventory().main) {
            total += capacity(stack);
        }
        return total;
    }

    /** Вдох: сначала из нагрудника, потом из баллонов в инвентаре. */
    public static boolean consume(PlayerEntity player, int amount) {
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        int left = amount;
        left -= take(chest, left);
        for (ItemStack stack : player.getInventory().main) {
            if (left <= 0) {
                break;
            }
            left -= take(stack, left);
        }
        return left <= 0;
    }

    private static int take(ItemStack stack, int amount) {
        int taken = Math.min(amount, stored(stack));
        if (taken > 0) {
            set(stack, stored(stack) - taken);
        }
        return taken;
    }

    /** «М:СС» — на сколько хватит запаса. */
    public static String time(int units) {
        return String.format("%d:%02d", units / 60, units % 60);
    }
}
