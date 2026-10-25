package com.orbitcore.client.screen;

import com.orbitcore.screen.OxygenStationScreenHandler;
import com.orbitcore.survival.Oxygen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

public class OxygenStationScreen extends OrbitScreen<OxygenStationScreenHandler> {
    public OxygenStationScreen(OxygenStationScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        drawVerticalBar(context, left + 12, top + 17, 8, 52, handler.energy(), handler.capacity(), ENERGY_TOP, ENERGY_BOTTOM);
        drawVerticalBar(context, left + 26, top + 17, 12, 52, handler.oxygen(), handler.oxygenCapacity(),
                AirSeparatorScreen.OXYGEN_TOP, AirSeparatorScreen.OXYGEN_BOTTOM);
        drawProgressArrow(context, left + 80, top + 34, handler.itemOxygen(), handler.itemCapacity());
        drawVerticalBar(context, left + 142, top + 17, 8, 52, handler.itemOxygen(), handler.itemCapacity(),
                AirSeparatorScreen.OXYGEN_TOP, AirSeparatorScreen.OXYGEN_BOTTOM);
        if (handler.itemCapacity() > 0) {
            Text time = Text.literal(Oxygen.time(handler.itemOxygen()));
            context.drawText(textRenderer, time, left + 125 - textRenderer.getWidth(time) / 2, top + 58, TEXT_DIM, false);
        }
    }

    @Override
    protected List<Text> getGaugeTooltip(int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, 26, 17, 12, 52)) {
            return List.of(Text.translatable("gui.orbitcore.fluid", Text.translatable("block.orbitcore.liquid_oxygen"),
                    handler.oxygen(), handler.oxygenCapacity()));
        }
        if (inside(mouseX, mouseY, 142, 17, 8, 52) && handler.itemCapacity() > 0) {
            return List.of(Text.translatable("tooltip.orbitcore.oxygen", handler.itemOxygen(), handler.itemCapacity(),
                    Oxygen.time(handler.itemOxygen())));
        }
        if (inside(mouseX, mouseY, 12, 17, 8, 52)) {
            return List.of(Text.translatable("gui.orbitcore.energy", handler.energy(), handler.capacity()));
        }
        return null;
    }
}
