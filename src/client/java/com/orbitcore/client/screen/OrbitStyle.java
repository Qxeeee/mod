package com.orbitcore.client.screen;

import net.minecraft.client.gui.DrawContext;

/**
 * Цвета и панели интерфейсов OrbitCore в одном месте.
 */
public final class OrbitStyle {
    public static final int BORDER = 0xFF06090F;
    public static final int PANEL = 0xFF1B2333;
    public static final int PANEL_LIGHT = 0xFF2C3A54;
    public static final int PANEL_DARK = 0xFF111826;
    public static final int SLOT = 0xFF0A0F18;
    public static final int ACCENT = 0xFF4FD1E8;
    public static final int ACCENT_DARK = 0xFF1C6E80;
    public static final int GOLD = 0xFFF2C66D;
    public static final int RED = 0xFFFF8A6A;
    public static final int GREEN = 0xFF7CF0B0;
    public static final int TEXT = 0xFFDCE8F5;
    public static final int TEXT_DIM = 0xFF8EA2BE;
    public static final int TEXT_FAINT = 0xFF56647A;
    public static final int ENERGY_TOP = 0xFF7CF0B0;
    public static final int ENERGY_BOTTOM = 0xFF1E8C6A;

    private OrbitStyle() {
    }

    /** Панель с рамкой и лёгким объёмом. */
    public static void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, BORDER);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_LIGHT);
        context.fill(x + 2, y + 2, x + width - 1, y + height - 1, PANEL_DARK);
        context.fill(x + 2, y + 2, x + width - 2, y + height - 2, PANEL);
    }

    /** Утопленная область (слот, окно списка). */
    public static void inset(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, PANEL_DARK);
        context.fill(x + 1, y + 1, x + width, y + height, PANEL_LIGHT);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, SLOT);
    }
}
