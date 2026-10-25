package com.orbitcore.client.screen;

import com.orbitcore.block.entity.MetallurgicalFurnaceBlockEntity;
import com.orbitcore.screen.MetallurgicalFurnaceScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class MetallurgicalFurnaceScreen extends OrbitScreen<MetallurgicalFurnaceScreenHandler> {
    private static final int THERMO_X = 18;
    private static final int THERMO_Y = 17;
    private static final int THERMO_W = 8;
    private static final int THERMO_H = 52;

    public MetallurgicalFurnaceScreen(MetallurgicalFurnaceScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        int heat = handler.heat();
        int max = MetallurgicalFurnaceBlockEntity.MAX_HEAT;
        // Термометр: от тёмно-красного к раскалённо-жёлтому
        drawVerticalBar(context, left + THERMO_X, top + THERMO_Y, THERMO_W, THERMO_H, heat, max, 0xFFFFE070, 0xFFB01E10);
        int required = handler.requiredHeat();
        if (required > 0) {
            int markY = top + THERMO_Y + THERMO_H - (int) ((long) THERMO_H * required / max);
            int color = heat >= required ? ACCENT : 0xFFFF6A4A;
            context.fill(left + THERMO_X - 3, markY, left + THERMO_X + THERMO_W + 3, markY + 1, color);
        }
        drawFlame(context, left + 55, top + 36, handler.burnTime(), handler.fuelTime());
        drawProgressArrow(context, left + 88, top + 35, handler.progress(), handler.maxProgress());
    }

    @Override
    protected List<Text> getGaugeTooltip(int mouseX, int mouseY) {
        if (!inside(mouseX, mouseY, THERMO_X, THERMO_Y, THERMO_W, THERMO_H)) {
            return null;
        }
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("gui.orbitcore.heat", handler.heat()));
        if (handler.requiredHeat() > 0) {
            lines.add(Text.translatable("gui.orbitcore.heat_required", handler.requiredHeat())
                    .styled(style -> style.withColor(handler.heat() >= handler.requiredHeat() ? 0x7CF0B0 : 0xFF8A6A)));
        }
        return lines;
    }
}
