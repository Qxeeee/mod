package com.orbitcore.client.screen;

import com.orbitcore.screen.ElectrolyzerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

public class ElectrolyzerScreen extends OrbitScreen<ElectrolyzerScreenHandler> {
    private static final int BAR_X = 152;
    private static final int BAR_Y = 17;
    private static final int BAR_W = 10;
    private static final int BAR_H = 52;

    public ElectrolyzerScreen(ElectrolyzerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        drawProgressArrow(context, left + 76, top + 35, handler.progress(), handler.maxProgress());
        drawVerticalBar(context, left + BAR_X, top + BAR_Y, BAR_W, BAR_H, handler.energy(), handler.capacity(), ENERGY_TOP, ENERGY_BOTTOM);
        if (handler.maxProgress() > 0 && handler.energy() == 0) {
            context.drawText(textRenderer, Text.translatable("gui.orbitcore.no_energy"), left + 60, top + 58, 0xFFFF8A6A, false);
        }
    }

    @Override
    protected List<Text> getGaugeTooltip(int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, BAR_X, BAR_Y, BAR_W, BAR_H)) {
            return null;
        }
        return List.of(Text.translatable("gui.orbitcore.energy", handler.energy(), handler.capacity()));
    }
}
