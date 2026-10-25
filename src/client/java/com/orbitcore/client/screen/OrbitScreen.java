package com.orbitcore.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import com.orbitcore.screen.MachineScreenHandler;

import java.util.List;

/**
 * Общий стиль интерфейсов OrbitCore: тёмная панель приборов с голубыми акцентами.
 * Всё рисуется кодом, без текстур, чтобы стиль было легко менять в одном месте.
 */
public abstract class OrbitScreen<T extends ScreenHandler> extends HandledScreen<T> {
    protected static final int BORDER = 0xFF06090F;
    protected static final int PANEL = 0xFF1B2333;
    protected static final int PANEL_LIGHT = 0xFF2C3A54;
    protected static final int PANEL_DARK = 0xFF111826;
    protected static final int SLOT = 0xFF0A0F18;
    protected static final int ACCENT = 0xFF4FD1E8;
    protected static final int ACCENT_DARK = 0xFF1C6E80;
    protected static final int TEXT = 0xFFDCE8F5;
    protected static final int TEXT_DIM = 0xFF8EA2BE;
    protected static final int ENERGY_TOP = 0xFF7CF0B0;
    protected static final int ENERGY_BOTTOM = 0xFF1E8C6A;

    protected OrbitScreen(T handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        List<Text> tooltip = getGaugeTooltip(mouseX - x, mouseY - y);
        if (tooltip != null && !tooltip.isEmpty()) {
            context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = x;
        int top = y;
        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, BORDER);
        context.fill(left + 1, top + 1, left + backgroundWidth - 1, top + backgroundHeight - 1, PANEL_LIGHT);
        context.fill(left + 2, top + 2, left + backgroundWidth - 1, top + backgroundHeight - 1, PANEL_DARK);
        context.fill(left + 2, top + 2, left + backgroundWidth - 2, top + backgroundHeight - 2, PANEL);
        // Полоса-акцент под заголовком и разделитель над инвентарём игрока
        context.fill(left + 7, top + 14, left + backgroundWidth - 7, top + 15, ACCENT_DARK);
        context.fill(left + 7, top + playerInventoryTitleY - 3, left + backgroundWidth - 7, top + playerInventoryTitleY - 2, PANEL_LIGHT);

        for (Slot slot : handler.slots) {
            boolean output = slot instanceof MachineScreenHandler.OutputSlot || slot instanceof CraftingResultSlot;
            drawSlot(context, left + slot.x - 1, top + slot.y - 1, output);
        }
        drawGauges(context, left, top, delta);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, TEXT, false);
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, TEXT_DIM, false);
    }

    /** Приборы машины (температура, энергия, прогресс) поверх фона. */
    protected abstract void drawGauges(DrawContext context, int left, int top, float delta);

    /** Подсказка при наведении на прибор; координаты мыши — относительно окна. */
    protected List<Text> getGaugeTooltip(int mouseX, int mouseY) {
        return null;
    }

    protected void drawSlot(DrawContext context, int sx, int sy, boolean output) {
        if (output) {
            context.fill(sx - 4, sy - 4, sx + 22, sy + 22, ACCENT_DARK);
            context.fill(sx - 3, sy - 3, sx + 21, sy + 21, PANEL_DARK);
        }
        context.fill(sx, sy, sx + 18, sy + 18, PANEL_DARK);
        context.fill(sx + 1, sy + 1, sx + 18, sy + 18, PANEL_LIGHT);
        context.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
    }

    /** Вертикальная шкала, заполняется снизу вверх. */
    protected void drawVerticalBar(DrawContext context, int bx, int by, int width, int height,
                                   int value, int max, int colorTop, int colorBottom) {
        context.fill(bx - 1, by - 1, bx + width + 1, by + height + 1, PANEL_DARK);
        context.fill(bx, by, bx + width, by + height, SLOT);
        if (max > 0 && value > 0) {
            int filled = Math.min(height, (int) ((long) height * value / max));
            context.fillGradient(bx, by + height - filled, bx + width, by + height, colorTop, colorBottom);
        }
    }

    /** Стрелка прогресса слева направо. */
    protected void drawProgressArrow(DrawContext context, int ax, int ay, int progress, int max) {
        int length = 22;
        context.fill(ax, ay + 5, ax + length - 6, ay + 11, SLOT);
        for (int i = 0; i < 8; i++) {
            context.fill(ax + length - 6 + i, ay + i, ax + length - 5 + i, ay + 16 - i, SLOT);
        }
        if (max <= 0 || progress <= 0) {
            return;
        }
        int filled = Math.min(length + 2, (int) ((long) (length + 2) * progress / max));
        context.fill(ax, ay + 6, ax + Math.min(filled, length - 6), ay + 10, ACCENT);
        for (int i = 0; i < 8 && length - 6 + i < filled; i++) {
            context.fill(ax + length - 6 + i, ay + 1 + i, ax + length - 5 + i, ay + 15 - i, ACCENT);
        }
    }

    /** Пламя: показывает, сколько осталось гореть текущему топливу. */
    protected void drawFlame(DrawContext context, int fx, int fy, int burnTime, int fuelTime) {
        context.fill(fx + 3, fy + 2, fx + 11, fy + 14, SLOT);
        if (burnTime <= 0 || fuelTime <= 0) {
            return;
        }
        int height = Math.max(1, (int) (13L * burnTime / fuelTime));
        context.fillGradient(fx + 4, fy + 14 - height, fx + 10, fy + 13, 0xFFFFE08A, 0xFFE8501C);
    }

    protected boolean inside(int mouseX, int mouseY, int bx, int by, int width, int height) {
        return mouseX >= bx - 1 && mouseX < bx + width + 1 && mouseY >= by - 1 && mouseY < by + height + 1;
    }
}
