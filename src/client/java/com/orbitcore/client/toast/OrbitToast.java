package com.orbitcore.client.toast;

import com.orbitcore.OrbitCore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Всплывающее уведомление в стиле OrbitCore: открытие небесного тела, завершённое исследование.
 */
public class OrbitToast implements Toast {
    private static final Identifier MOON = OrbitCore.id("textures/gui/telescope/moon.png");
    private static final long DURATION = 6000L;

    private final Text title;
    private final Text description;
    private final int accent;
    @Nullable
    private final ItemStack icon;
    private final boolean moonIcon;

    private OrbitToast(Text title, Text description, int accent, @Nullable ItemStack icon, boolean moonIcon) {
        this.title = title;
        this.description = description;
        this.accent = accent;
        this.icon = icon;
        this.moonIcon = moonIcon;
    }

    public static OrbitToast discovery(Text body, boolean moon) {
        return new OrbitToast(Text.translatable("toast.orbitcore.discovery"), body, 0xFFF2C66D, null, moon);
    }

    public static OrbitToast research(Text name, ItemStack icon) {
        return new OrbitToast(Text.translatable("toast.orbitcore.research"), name, 0xFF4FD1E8, icon, false);
    }

    @Override
    public Visibility draw(DrawContext context, ToastManager manager, long startTime) {
        int width = getWidth();
        int height = getHeight();
        context.fill(0, 0, width, height, 0xF0060A12);
        context.fill(1, 1, width - 1, height - 1, 0xF01B2333);
        context.fill(0, 0, 3, height, accent);
        if (moonIcon) {
            // Полная Луна — первый кадр полосы фаз
            context.drawTexture(MOON, 8, 8, 16, 16, 0, 0, 128, 128, 1024, 128);
        } else if (icon != null) {
            context.drawItem(icon, 8, 8);
        }
        context.drawText(manager.getClient().textRenderer, title, 30, 7, accent, false);
        context.drawText(manager.getClient().textRenderer, description, 30, 18, 0xFFDCE8F5, false);
        return startTime >= DURATION * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
