package com.orbitcore.client.hud;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import com.orbitcore.world.ModDimensions;
import com.orbitcore.client.sound.SpaceDread;
import com.orbitcore.item.SpacesuitItem;
import com.orbitcore.registry.ModItems;
import com.orbitcore.survival.LifeSupport;
import com.orbitcore.survival.LifeSupportComponent;
import com.orbitcore.survival.Oxygen;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Приборы скафандра: рамка визора и панель с кислородом, радиацией,
 * температурой и давлением. Без шлема — только предупреждение о вакууме
 * и показания дозиметра, если он на панели быстрого доступа.
 */
public final class SuitHud {
    private static final int PANEL = 0xA0101826;
    private static final int TEXT = 0xFFDCE8F5;
    private static final int DIM = 0xFF8EA2BE;
    private static final int OXYGEN = 0xFF5FB4E6;
    private static final int GREEN = 0xFF7CF0B0;
    private static final int GOLD = 0xFFF2C66D;
    private static final int RED = 0xFFFF6A50;
    private static final int SIGNAL = 0xFFC9A6FF;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private SuitHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(SuitHud::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || player.isSpectator()) {
            return;
        }
        LifeSupportComponent data = LifeSupport.get(player);
        boolean helmet = SpacesuitItem.isWearingHelmet(player);
        if (helmet) {
            drawVisor(context);
            drawPanel(context, client.textRenderer, player, data);
        } else if (hasDosimeter(player.getInventory())) {
            drawDosimeter(context, client.textRenderer, data);
        }
        Text warning = warning(player, data, helmet);
        if (warning != null) {
            int width = client.textRenderer.getWidth(warning);
            int x = (context.getScaledWindowWidth() - width) / 2;
            int y = context.getScaledWindowHeight() / 4;
            // Тревога пульсирует, но не гаснет совсем
            int pulse = (int) (0x60 + 0x50 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0)));
            context.fill(x - 4, y - 3, x + width + 4, y + 11, pulse << 24 | 0x400808);
            context.drawText(client.textRenderer, warning, x, y, RED, false);
        }
    }

    /** Края визора: стекло шлема темнее у кромки, сверху — блик. */
    private static void drawVisor(DrawContext context) {
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        int band = Math.max(16, h / 8);
        context.fillGradient(0, 0, w, band, 0xC0000000, 0x00000000);
        context.fillGradient(0, h - band, w, h, 0x00000000, 0xC0000000);
        for (int i = 0; i < band; i++) {
            int alpha = (int) (0xC0 * (1 - i / (float) band));
            context.fill(i, 0, i + 1, h, alpha << 24);
            context.fill(w - i - 1, 0, w - i, h, alpha << 24);
        }
        // Блик на стекле
        for (int i = 0; i < w / 5; i++) {
            int x = w / 8 + i;
            int y = band / 2 + i / 6;
            context.fill(x, y, x + 1, y + 1, 0x18FFFFFF);
        }
    }

    private static void drawPanel(DrawContext context, TextRenderer font, ClientPlayerEntity player, LifeSupportComponent data) {
        int x = 6;
        int y = 6;
        Text signal = signal(player);
        context.fill(x, y, x + 150, y + (signal != null ? 62 : 52), PANEL);
        if (signal != null) {
            line(context, font, x, y + 49, Text.translatable("hud.orbitcore.signal", arrow(player)), signal, SIGNAL);
        }

        int oxygen = Oxygen.total(player);
        int capacity = Math.max(1, Oxygen.totalCapacity(player));
        int oxygenColor = oxygen < LifeSupport.LOW_OXYGEN ? RED : OXYGEN;
        context.drawText(font, Text.translatable("hud.orbitcore.oxygen"), x + 4, y + 4, DIM, false);
        Text time = Text.literal(data.breathable ? "—" : Oxygen.time(oxygen));
        context.drawText(font, time, x + 146 - font.getWidth(time), y + 4, oxygenColor, false);
        context.fill(x + 4, y + 14, x + 146, y + 16, 0xFF0A0F18);
        context.fill(x + 4, y + 14, x + 4 + (int) (142L * Math.min(oxygen, capacity) / capacity), y + 16, oxygenColor);

        int doseColor = data.dose >= LifeSupport.DOSE_WEAKNESS ? RED : data.dose >= LifeSupport.DOSE_HUNGER ? GOLD : GREEN;
        line(context, font, x, y + 19, Text.translatable("hud.orbitcore.radiation"),
                Text.translatable("hud.orbitcore.dose", Math.round(data.dose), String.format("%.1f", data.doseRate)), doseColor);

        boolean suit = SpacesuitItem.isWearingFullSuit(player);
        int min = suit ? SpacesuitItem.MIN_TEMPERATURE : LifeSupport.BARE_MIN_TEMPERATURE;
        int max = suit ? SpacesuitItem.MAX_TEMPERATURE : LifeSupport.BARE_MAX_TEMPERATURE;
        int temperatureColor = data.temperature > max || data.temperature < min ? RED
                : data.temperature > max - 30 || data.temperature < min + 30 ? GOLD : TEXT;
        line(context, font, x, y + 29, Text.translatable("hud.orbitcore.temperature"),
                Text.translatable("hud.orbitcore.celsius", (data.temperature > 0 ? "+" : "") + data.temperature), temperatureColor);

        line(context, font, x, y + 39, Text.translatable("hud.orbitcore.pressure"),
                Text.translatable("hud.orbitcore.kpa", data.breathable ? 101 : 0), data.breathable ? TEXT : GOLD);
    }

    /** Расстояние до источника сигнала, если он уже известен и игрок на Луне. */
    @Nullable
    private static Text signal(ClientPlayerEntity player) {
        BlockPos echo = SpaceDread.echo();
        if (echo == null || player.getWorld().getRegistryKey() != ModDimensions.MOON) {
            return null;
        }
        double dx = echo.getX() + 0.5 - player.getX();
        double dz = echo.getZ() + 0.5 - player.getZ();
        return Text.translatable("hud.orbitcore.signal_distance", Math.round(Math.sqrt(dx * dx + dz * dz)));
    }

    /** Стрелка на источник сигнала относительно того, куда смотрит игрок. */
    private static String arrow(ClientPlayerEntity player) {
        BlockPos echo = SpaceDread.echo();
        if (echo == null) {
            return "";
        }
        double dx = echo.getX() + 0.5 - player.getX();
        double dz = echo.getZ() + 0.5 - player.getZ();
        float target = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float relative = MathHelper.wrapDegrees(target - player.getYaw());
        int sector = Math.floorMod(Math.round(relative / 45.0f), 8);
        return ARROWS[sector];
    }

    private static void drawDosimeter(DrawContext context, TextRenderer font, LifeSupportComponent data) {
        int x = 6;
        int y = 6;
        context.fill(x, y, x + 150, y + 13, PANEL);
        int doseColor = data.dose >= LifeSupport.DOSE_WEAKNESS ? RED : data.dose >= LifeSupport.DOSE_HUNGER ? GOLD : GREEN;
        line(context, font, x, y + 2, Text.translatable("hud.orbitcore.radiation"),
                Text.translatable("hud.orbitcore.dose", Math.round(data.dose), String.format("%.1f", data.doseRate)), doseColor);
    }

    private static void line(DrawContext context, TextRenderer font, int x, int y, Text label, Text value, int color) {
        context.drawText(font, label, x + 4, y, DIM, false);
        context.drawText(font, value, x + 146 - font.getWidth(value), y, color, false);
    }

    @Nullable
    private static Text warning(ClientPlayerEntity player, LifeSupportComponent data, boolean helmet) {
        if (player.isCreative()) {
            return null;
        }
        if (data.suffocating) {
            return Text.translatable(helmet ? "hud.orbitcore.warning.no_oxygen" : "hud.orbitcore.warning.vacuum");
        }
        boolean suit = SpacesuitItem.isWearingFullSuit(player);
        if (!data.breathable && suit && Oxygen.total(player) < LifeSupport.LOW_OXYGEN) {
            return Text.translatable("hud.orbitcore.warning.low_oxygen");
        }
        if (!data.breathable) {
            int min = suit ? SpacesuitItem.MIN_TEMPERATURE : LifeSupport.BARE_MIN_TEMPERATURE;
            int max = suit ? SpacesuitItem.MAX_TEMPERATURE : LifeSupport.BARE_MAX_TEMPERATURE;
            if (data.temperature > max) {
                return Text.translatable("hud.orbitcore.warning.heat");
            }
            if (data.temperature < min) {
                return Text.translatable("hud.orbitcore.warning.cold");
            }
        }
        if (data.dose >= LifeSupport.DOSE_WEAKNESS && data.doseRate > 0) {
            return Text.translatable("hud.orbitcore.warning.radiation");
        }
        return null;
    }

    private static boolean hasDosimeter(PlayerInventory inventory) {
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (inventory.getStack(slot).isOf(ModItems.DOSIMETER)) {
                return true;
            }
        }
        return inventory.offHand.get(0).isOf(ModItems.DOSIMETER);
    }

}
