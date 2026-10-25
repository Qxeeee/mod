package com.orbitcore.client.screen;

import com.orbitcore.block.entity.SampleAnalyzerBlockEntity;
import com.orbitcore.science.SpaceProgram;
import com.orbitcore.screen.SampleAnalyzerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.List;

public class SampleAnalyzerScreen extends OrbitScreen<SampleAnalyzerScreenHandler> {
    private static final int BAR_X = 152;
    private static final int BAR_Y = 17;
    private static final int BAR_W = 10;
    private static final int BAR_H = 52;

    public SampleAnalyzerScreen(SampleAnalyzerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        drawProgressArrow(context, left + 68, top + 35, handler.progress(), handler.maxProgress());
        drawVerticalBar(context, left + BAR_X, top + BAR_Y, BAR_W, BAR_H, handler.energy(), handler.capacity(), ENERGY_TOP, ENERGY_BOTTOM);

        // Экран результата: спектр во время анализа и счётчик изученности образца
        int sx = left + 96;
        int sy = top + 20;
        context.fill(sx, sy, sx + 48, sy + 46, PANEL_DARK);
        context.fill(sx + 1, sy + 1, sx + 47, sy + 45, 0xFF07120D);
        int status = handler.status();
        if (status == SampleAnalyzerBlockEntity.STATUS_WORKING) {
            long t = System.currentTimeMillis() / 90;
            for (int i = 0; i < 11; i++) {
                int h = (int) (6 + 14 * Math.abs(Math.sin(i * 1.7 + t * 0.35) * Math.cos(i * 0.6 - t * 0.2)));
                context.fill(sx + 3 + i * 4, sy + 30 - h, sx + 6 + i * 4, sy + 30, 0xFF46C882);
            }
        }
        if (status == SampleAnalyzerBlockEntity.STATUS_IDLE) {
            context.drawTextWrapped(textRenderer, Text.translatable("gui.orbitcore.analyzer.insert"), sx + 4, sy + 15, 42, 0xFF46C882);
        } else {
            Text count = Text.translatable("gui.orbitcore.analyzer.studied", handler.timesAnalyzed(), SpaceProgram.ANALYSIS_LIMIT);
            context.drawText(textRenderer, count, sx + 24 - textRenderer.getWidth(count) / 2, sy + 35, 0xFF9FE6BE, false);
        }
        Text message = switch (status) {
            case SampleAnalyzerBlockEntity.STATUS_STUDIED -> Text.translatable("gui.orbitcore.analyzer.fully_studied");
            case SampleAnalyzerBlockEntity.STATUS_NO_ENERGY -> Text.translatable("gui.orbitcore.no_energy");
            default -> null;
        };
        if (message != null) {
            context.drawText(textRenderer, message, left + 8, top + 62, 0xFFFF8A6A, false);
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
