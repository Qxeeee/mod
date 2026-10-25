package com.orbitcore.client.screen;

import com.orbitcore.screen.FuelRefineryScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

public class FuelRefineryScreen extends OrbitScreen<FuelRefineryScreenHandler> {
    static final int KEROSENE_TOP = 0xFFF5C76A;
    static final int KEROSENE_BOTTOM = 0xFFB0661E;
    private static final int WATER_TOP = 0xFF6FA8F0;
    private static final int WATER_BOTTOM = 0xFF24508F;

    public FuelRefineryScreen(FuelRefineryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        drawVerticalBar(context, left + 12, top + 17, 10, 52, handler.water(), handler.waterCapacity(), WATER_TOP, WATER_BOTTOM);
        drawProgressArrow(context, left + 78, top + 35, handler.progress(), handler.maxProgress());
        drawVerticalBar(context, left + 108, top + 17, 14, 52, handler.kerosene(), handler.keroseneCapacity(), KEROSENE_TOP, KEROSENE_BOTTOM);
        drawVerticalBar(context, left + 158, top + 17, 8, 52, handler.energy(), handler.capacity(), ENERGY_TOP, ENERGY_BOTTOM);
    }

    @Override
    protected List<Text> getGaugeTooltip(int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, 12, 17, 10, 52)) {
            return List.of(Text.translatable("gui.orbitcore.fluid", Text.translatable("block.minecraft.water"), handler.water(), handler.waterCapacity()));
        }
        if (inside(mouseX, mouseY, 108, 17, 14, 52)) {
            return List.of(Text.translatable("gui.orbitcore.fluid", Text.translatable("block.orbitcore.kerosene"), handler.kerosene(), handler.keroseneCapacity()));
        }
        if (inside(mouseX, mouseY, 158, 17, 8, 52)) {
            return List.of(Text.translatable("gui.orbitcore.energy", handler.energy(), handler.capacity()));
        }
        return null;
    }
}
