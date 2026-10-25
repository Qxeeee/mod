package com.orbitcore.client.screen;

import com.orbitcore.screen.RegolithProcessorScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

public class RegolithProcessorScreen extends OrbitScreen<RegolithProcessorScreenHandler> {
    /** Гелий-3 — бледно-фиолетовым, как свечение газоразрядной трубки. */
    private static final int HELIUM_TOP = 0xFFF0D8FF;
    private static final int HELIUM_BOTTOM = 0xFFA070E0;

    public RegolithProcessorScreen(RegolithProcessorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        drawVerticalBar(context, left + 12, top + 17, 8, 52, handler.energy(), handler.capacity(), ENERGY_TOP, ENERGY_BOTTOM);
        drawProgressArrow(context, left + 52, top + 34, handler.progress(), handler.maxProgress());
        drawVerticalBar(context, left + 104, top + 17, 8, 52, handler.helium(), handler.heliumCapacity(), HELIUM_TOP, HELIUM_BOTTOM);
        drawVerticalBar(context, left + 140, top + 17, 8, 52, handler.oxygen(), handler.oxygenCapacity(),
                AirSeparatorScreen.OXYGEN_TOP, AirSeparatorScreen.OXYGEN_BOTTOM);
    }

    @Override
    protected List<Text> getGaugeTooltip(int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, 104, 17, 8, 52)) {
            return List.of(Text.translatable("gui.orbitcore.helium3", handler.helium(), handler.heliumCapacity()),
                    Text.translatable("gui.orbitcore.helium3.hint").formatted(net.minecraft.util.Formatting.GRAY));
        }
        if (inside(mouseX, mouseY, 140, 17, 8, 52)) {
            return List.of(Text.translatable("gui.orbitcore.fluid", Text.translatable("block.orbitcore.liquid_oxygen"),
                    handler.oxygen(), handler.oxygenCapacity()));
        }
        if (inside(mouseX, mouseY, 12, 17, 8, 52)) {
            return List.of(Text.translatable("gui.orbitcore.energy", handler.energy(), handler.capacity()));
        }
        return null;
    }
}
