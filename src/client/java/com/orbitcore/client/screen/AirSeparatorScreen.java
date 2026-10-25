package com.orbitcore.client.screen;

import com.orbitcore.screen.AirSeparatorScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

public class AirSeparatorScreen extends OrbitScreen<AirSeparatorScreenHandler> {
    static final int OXYGEN_TOP = 0xFFD8F4FF;
    static final int OXYGEN_BOTTOM = 0xFF5FB4E6;

    public AirSeparatorScreen(AirSeparatorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        drawVerticalBar(context, left + 12, top + 17, 8, 52, handler.energy(), handler.capacity(), ENERGY_TOP, ENERGY_BOTTOM);
        drawVerticalBar(context, left + 86, top + 17, 16, 52, handler.oxygen(), handler.oxygenCapacity(), OXYGEN_TOP, OXYGEN_BOTTOM);
        boolean working = handler.energy() > 0 && handler.oxygen() < handler.oxygenCapacity();
        Text status = working ? Text.translatable("gui.orbitcore.separator.working") : Text.translatable("gui.orbitcore.no_energy");
        context.drawTextWrapped(textRenderer, status, left + 28, top + 30, 52, working ? OXYGEN_TOP : OrbitStyle.RED);
        if (working) {
            // Иней на стенке бака
            long t = System.currentTimeMillis() / 120;
            for (int i = 0; i < 6; i++) {
                int fx = left + 86 + (int) ((i * 7 + t) % 16);
                int fy = top + 17 + (int) ((i * 13 + t * 2) % 52);
                context.fill(fx, fy, fx + 1, fy + 1, 0xC0FFFFFF);
            }
        }
    }

    @Override
    protected List<Text> getGaugeTooltip(int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, 86, 17, 16, 52)) {
            return List.of(Text.translatable("gui.orbitcore.fluid", Text.translatable("block.orbitcore.liquid_oxygen"), handler.oxygen(), handler.oxygenCapacity()));
        }
        if (inside(mouseX, mouseY, 12, 17, 8, 52)) {
            return List.of(Text.translatable("gui.orbitcore.energy", handler.energy(), handler.capacity()));
        }
        return null;
    }
}
