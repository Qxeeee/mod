package com.orbitcore.client.screen;

import com.orbitcore.client.render.RocketModelRenderer;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.rocket.RocketDesign;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

import static com.orbitcore.client.screen.OrbitStyle.*;

/**
 * Экран ракеты на стартовом столе: заправка, расчёт полёта и предстартовые замечания.
 */
public class RocketScreen extends Screen {
    private static final int W = 292;
    private static final int H = 202;
    private static final double MAX_DISTANCE_SQ = 10 * 10;

    private final int entityId;
    private int left;
    private int top;

    public RocketScreen(int entityId) {
        super(Text.translatable("entity.orbitcore.rocket"));
        this.entityId = entityId;
    }

    private RocketEntity rocket() {
        return client != null && client.world != null && client.world.getEntityById(entityId) instanceof RocketEntity r ? r : null;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        RocketEntity rocket = rocket();
        if (rocket == null || rocket.isRemoved() || client.player.squaredDistanceTo(rocket) > MAX_DISTANCE_SQ) {
            close();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        RocketEntity rocket = rocket();
        if (rocket == null) {
            return;
        }
        RocketDesign design = rocket.design();
        OrbitStyle.panel(context, left, top, W, H);
        context.drawText(textRenderer, title, left + 8, top + 7, ACCENT, false);
        context.fill(left + 7, top + 18, left + W - 7, top + 19, ACCENT_DARK);

        OrbitStyle.inset(context, left + 8, top + 24, 60, 128);
        float spin = (System.currentTimeMillis() % 16000L) / 16000f * 360f;
        RocketModelRenderer.drawInGui(context, design, left + 10, top + 26, 56, 124, spin);

        // Заправка
        int capacity = design.propellantCapacity();
        int fx = left + 76;
        fuelBar(context, fx, top + 26, Text.translatable("block.orbitcore.kerosene"), rocket.kerosene(), capacity,
                FuelRefineryScreen.KEROSENE_TOP, FuelRefineryScreen.KEROSENE_BOTTOM);
        fuelBar(context, fx, top + 50, Text.translatable("block.orbitcore.liquid_oxygen"), rocket.oxygen(), capacity,
                AirSeparatorScreen.OXYGEN_TOP, AirSeparatorScreen.OXYGEN_BOTTOM);

        RocketStats.draw(context, textRenderer, design, rocket.fill(), fx, top + 76);

        // Замечания перед стартом
        int y = top + 76;
        int px = left + 190;
        context.drawText(textRenderer, Text.translatable("gui.orbitcore.rocket.checklist"), px, y, TEXT_DIM, false);
        y += 11;
        List<Text> problems = RocketStats.problems(design);
        if (rocket.fill() < 1.0) {
            problems.add(0, Text.literal("⚠ ").append(Text.translatable("gui.orbitcore.rocket.not_fueled")).styled(s -> s.withColor(0xF2C66D)));
        }
        if (problems.isEmpty()) {
            context.drawText(textRenderer, Text.translatable("gui.orbitcore.rocket.all_good"), px, y, GREEN, false);
        }
        for (Text problem : problems) {
            for (OrderedText line : textRenderer.wrapLines(problem, W - 198)) {
                context.drawText(textRenderer, line, px, y, TEXT, false);
                y += 9;
            }
            y += 2;
        }

        // Подсказки и будущий старт
        OrbitStyle.inset(context, left + 8, top + 158, W - 16, 36);
        context.drawTextWrapped(textRenderer, Text.translatable("gui.orbitcore.rocket.help"), left + 12, top + 161, W - 24, TEXT_DIM);
        super.render(context, mouseX, mouseY, delta);
    }

    private void fuelBar(DrawContext context, int x, int y, Text name, int amount, int capacity, int colorTop, int colorBottom) {
        context.drawText(textRenderer, Text.translatable("gui.orbitcore.fluid", name, amount, capacity), x, y, TEXT, false);
        int width = W - (x - left) - 10;
        context.fill(x, y + 11, x + width, y + 18, PANEL_DARK);
        context.fill(x + 1, y + 12, x + width - 1, y + 17, SLOT);
        if (capacity > 0 && amount > 0) {
            int filled = (int) ((long) (width - 2) * amount / capacity);
            context.fillGradient(x + 1, y + 12, x + 1 + filled, y + 17, colorTop, colorBottom);
        }
    }
}
