package com.orbitcore.client.screen;

import com.orbitcore.block.entity.SealerBlockEntity;
import com.orbitcore.screen.SealerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class SealerScreen extends OrbitScreen<SealerScreenHandler> {
    public SealerScreen(SealerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawGauges(DrawContext context, int left, int top, float delta) {
        drawVerticalBar(context, left + 12, top + 17, 8, 52, handler.energy(), handler.capacity(), ENERGY_TOP, ENERGY_BOTTOM);
        drawVerticalBar(context, left + 112, top + 17, 16, 52, handler.oxygen(), handler.oxygenCapacity(),
                AirSeparatorScreen.OXYGEN_TOP, AirSeparatorScreen.OXYGEN_BOTTOM);

        SealerBlockEntity.Status status = handler.status();
        int color = switch (status) {
            case SEALED -> OrbitStyle.GREEN;
            case LEAK -> OrbitStyle.RED;
            default -> OrbitStyle.GOLD;
        };
        OrbitStyle.inset(context, left + 26, top + 17, 80, 52);
        context.drawTextWrapped(textRenderer, Text.translatable(status.key()), left + 29, top + 20, 76, color);
        if (status == SealerBlockEntity.Status.SEALED) {
            context.drawText(textRenderer, Text.translatable("gui.orbitcore.sealer.volume", handler.volume()), left + 29, top + 44, TEXT, false);
            // Индикатор давления: бегущие точки, пока отсек держит воздух
            long t = System.currentTimeMillis() / 150;
            for (int i = 0; i < 5; i++) {
                int dot = (int) ((t + i) % 5);
                context.fill(left + 29 + i * 6, top + 58, left + 32 + i * 6, top + 61, dot == 0 ? OrbitStyle.GREEN : PANEL_LIGHT);
            }
        }
        BlockPos leak = handler.leakOffset();
        if (status == SealerBlockEntity.Status.LEAK) {
            Text where = leak == null
                    ? Text.translatable("gui.orbitcore.sealer.leak_unknown", SealerBlockEntity.MAX_VOLUME)
                    : Text.translatable("gui.orbitcore.sealer.leak_at", signed(leak.getX()), signed(leak.getY()), signed(leak.getZ()));
            context.drawTextWrapped(textRenderer, where, left + 29, top + 42, 76, TEXT_DIM);
        }
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    @Override
    protected List<Text> getGaugeTooltip(int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, 112, 17, 16, 52)) {
            return List.of(Text.translatable("gui.orbitcore.fluid", Text.translatable("block.orbitcore.liquid_oxygen"),
                    handler.oxygen(), handler.oxygenCapacity()));
        }
        if (inside(mouseX, mouseY, 12, 17, 8, 52)) {
            return List.of(Text.translatable("gui.orbitcore.energy", handler.energy(), handler.capacity()));
        }
        return null;
    }
}
