package com.orbitcore.client.screen;

import com.orbitcore.screen.EngineeringTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public class EngineeringTableScreen extends OrbitScreen<EngineeringTableScreenHandler> {
    public EngineeringTableScreen(EngineeringTableScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        boolean ready = handler.getSlot(EngineeringTableScreenHandler.RESULT).hasStack();
        drawProgressArrow(context, left + 90, top + 35, ready ? 1 : 0, 1);
    }
}
