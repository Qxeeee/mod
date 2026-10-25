package com.orbitcore.client.screen;

import com.orbitcore.client.network.OrbitClientNetworking;
import com.orbitcore.client.render.RocketModelRenderer;
import com.orbitcore.entity.RocketEntity;
import com.orbitcore.rocket.FlightController;
import com.orbitcore.rocket.FlightPhase;
import com.orbitcore.rocket.RocketDesign;
import com.orbitcore.rocket.RocketPart;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
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

        int dy = RocketStats.drawSummary(context, textRenderer, design, rocket.fill(), fx, top + 76);
        drawDestinations(context, rocket, fx, dy, mouseX, mouseY);

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

        // Подсказки и кнопка старта
        OrbitStyle.inset(context, left + 8, top + 158, W - 112, 36);
        context.drawTextWrapped(textRenderer, Text.translatable("gui.orbitcore.rocket.help"), left + 12, top + 161, W - 120, TEXT_DIM);
        boolean ready = canLaunch(rocket);
        int bx = left + W - 100;
        int by = top + 162;
        boolean hover = inside(mouseX, mouseY, bx, by, 92, 28);
        context.fill(bx, by, bx + 92, by + 28, ready ? (hover ? GOLD : ACCENT) : PANEL_DARK);
        context.fill(bx + 1, by + 1, bx + 91, by + 27, ready ? 0xFF14301F : SLOT);
        Text launch = Text.translatable("gui.orbitcore.rocket.launch");
        context.drawText(textRenderer, launch, bx + 46 - textRenderer.getWidth(launch) / 2, by + 5, ready ? GREEN : TEXT_FAINT, false);
        Text target = Text.translatable(selected.translationKey());
        context.drawText(textRenderer, target, bx + 46 - textRenderer.getWidth(target) / 2, by + 16, ready ? TEXT : TEXT_FAINT, false);
        super.render(context, mouseX, mouseY, delta);
    }

    private RocketDesign.Destination selected = RocketDesign.Destination.EARTH_ORBIT;

    /** Цели полёта: щелчок выбирает, куда лететь. Недоступные пока помечены «скоро». */
    private void drawDestinations(DrawContext context, RocketEntity rocket, int x, int y, int mouseX, int mouseY) {
        for (RocketDesign.Destination destination : RocketDesign.Destination.values()) {
            boolean ok = rocket.design().canReach(destination, rocket.fill());
            boolean available = FlightController.isAvailable(destination, rocket.getWorld());
            if (destination == selected) {
                context.fill(x - 2, y - 1, x + 104, y + 9, ACCENT_DARK);
            } else if (inside(mouseX, mouseY, x - 2, y - 1, 106, 10)) {
                context.fill(x - 2, y - 1, x + 104, y + 9, PANEL_LIGHT);
            }
            Text line = Text.literal(ok ? "✔ " : "✘ ").append(Text.translatable(destination.translationKey()));
            if (!available) {
                line = line.copy().append(Text.translatable("gui.orbitcore.rocket.soon"));
            }
            context.drawText(textRenderer, line, x, y, ok && available ? GREEN : TEXT_FAINT, false);
            y += 10;
        }
    }

    private boolean canLaunch(RocketEntity rocket) {
        RocketDesign design = rocket.design();
        return rocket.phase() == FlightPhase.IDLE && FlightController.isAvailable(selected, rocket.getWorld()) && design.isValid()
                && design.has(RocketPart.Kind.CAPSULE) && design.canReach(selected, rocket.fill());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        RocketEntity rocket = rocket();
        if (rocket == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int x = left + 76;
        int y = top + 76 + 31;
        for (RocketDesign.Destination destination : RocketDesign.Destination.values()) {
            if (inside(mouseX, mouseY, x - 2, y - 1, 106, 10)) {
                selected = destination;
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
            y += 10;
        }
        if (inside(mouseX, mouseY, left + W - 100, top + 162, 92, 28) && canLaunch(rocket)) {
            OrbitClientNetworking.sendLaunch(rocket.getId(), selected.ordinal());
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.4f));
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
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
