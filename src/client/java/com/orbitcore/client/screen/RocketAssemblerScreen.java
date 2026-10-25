package com.orbitcore.client.screen;

import com.orbitcore.client.render.RocketModelRenderer;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.screen.RocketAssemblerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Сборочный стенд: ячейки модулей, вращающееся 3D-превью ракеты, расчёт и кнопка «Собрать».
 */
public class RocketAssemblerScreen extends OrbitScreen<RocketAssemblerScreenHandler> {
    private static final int PREVIEW_X = 64;
    private static final int PREVIEW_Y = 16;
    private static final int PREVIEW_W = 58;
    private static final int PREVIEW_H = 94;
    private static final int BUTTON_X = 128;
    private static final int BUTTON_Y = 94;
    private static final int BUTTON_W = 68;
    private static final int BUTTON_H = 16;

    public RocketAssemblerScreen(RocketAssemblerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = RocketAssemblerScreenHandler.WIDTH;
        backgroundHeight = RocketAssemblerScreenHandler.HEIGHT;
        playerInventoryTitleX = (RocketAssemblerScreenHandler.WIDTH - 162) / 2;
        playerInventoryTitleY = 114;
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        RocketDesign design = handler.design();

        // Номера ячеек: порядок модулей снизу вверх
        for (int i = 0; i < RocketDesign.MAX_PARTS; i++) {
            if (!handler.getSlot(i).hasStack()) {
                String number = String.valueOf(i + 1);
                int sx = left + RocketAssemblerScreenHandler.partX(i) + 8 - textRenderer.getWidth(number) / 2;
                context.drawText(textRenderer, number, sx, top + RocketAssemblerScreenHandler.partY(i) + 4, OrbitStyle.TEXT_FAINT, false);
            }
        }

        // Превью: стапель с сеткой и вращающаяся ракета
        OrbitStyle.inset(context, left + PREVIEW_X, top + PREVIEW_Y, PREVIEW_W, PREVIEW_H);
        for (int gy = PREVIEW_Y + 10; gy < PREVIEW_Y + PREVIEW_H; gy += 10) {
            context.fill(left + PREVIEW_X + 1, top + gy, left + PREVIEW_X + PREVIEW_W - 1, top + gy + 1, 0xFF101A28);
        }
        float spin = (System.currentTimeMillis() % 12000L) / 12000f * 360f;
        RocketModelRenderer.drawInGui(context, design, left + PREVIEW_X + 2, top + PREVIEW_Y + 2, PREVIEW_W - 4, PREVIEW_H - 4, spin);

        if (!design.parts().isEmpty()) {
            RocketStats.draw(context, textRenderer, design, 1.0, left + 128, top + 18);
        } else {
            context.drawTextWrapped(textRenderer, Text.translatable("gui.orbitcore.assembler.hint"), left + 128, top + 18, 96, TEXT_DIM);
        }

        boolean valid = design.isValid() && !handler.getSlot(RocketDesign.MAX_PARTS).hasStack();
        int bx = left + BUTTON_X;
        int by = top + BUTTON_Y;
        context.fill(bx, by, bx + BUTTON_W, by + BUTTON_H, valid ? ACCENT : PANEL_DARK);
        context.fill(bx + 1, by + 1, bx + BUTTON_W - 1, by + BUTTON_H - 1, valid ? ACCENT_DARK : SLOT);
        Text label = Text.translatable("gui.orbitcore.assembler.assemble");
        context.drawText(textRenderer, label, bx + BUTTON_W / 2 - textRenderer.getWidth(label) / 2, by + 4, valid ? TEXT : OrbitStyle.TEXT_FAINT, false);
    }

    @Override
    protected List<Text> getGaugeTooltip(int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H)) {
            return null;
        }
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("gui.orbitcore.assembler.assemble"));
        lines.addAll(RocketStats.problems(handler.design()));
        if (handler.getSlot(RocketDesign.MAX_PARTS).hasStack()) {
            lines.add(Text.translatable("gui.orbitcore.assembler.output_full"));
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inside((int) mouseX - x, (int) mouseY - y, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H)
                && handler.design().isValid() && client != null && client.interactionManager != null) {
            client.interactionManager.clickButton(handler.syncId, RocketAssemblerScreenHandler.BUTTON_ASSEMBLE);
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_SMITHING_TABLE_USE, 1.0f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
