package com.orbitcore.client.screen;

import com.orbitcore.block.entity.CoalGeneratorBlockEntity;
import com.orbitcore.screen.CoalGeneratorScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

public class CoalGeneratorScreen extends OrbitScreen<CoalGeneratorScreenHandler> {
    private static final int BAR_X = 110;
    private static final int BAR_Y = 17;
    private static final int BAR_W = 14;
    private static final int BAR_H = 52;

    public CoalGeneratorScreen(CoalGeneratorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        drawFlame(context, left + 56, top + 36, handler.burnTime(), handler.fuelTime());
        drawVerticalBar(context, left + BAR_X, top + BAR_Y, BAR_W, BAR_H, handler.energy(), handler.capacity(), ENERGY_TOP, ENERGY_BOTTOM);
        Text rate = handler.burnTime() > 0
                ? Text.translatable("gui.orbitcore.generating", CoalGeneratorBlockEntity.GENERATION)
                : Text.translatable("gui.orbitcore.idle");
        context.drawText(textRenderer, rate, left + 132, top + 39, handler.burnTime() > 0 ? ENERGY_TOP : TEXT_DIM, false);
    }

    @Override
    protected List<Text> getGaugeTooltip(int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, BAR_X, BAR_Y, BAR_W, BAR_H)) {
            return null;
        }
        return List.of(Text.translatable("gui.orbitcore.energy", handler.energy(), handler.capacity()));
    }
}
