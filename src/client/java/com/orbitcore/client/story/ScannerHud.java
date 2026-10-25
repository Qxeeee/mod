package com.orbitcore.client.story;

import com.orbitcore.item.SignalScannerItem;
import com.orbitcore.registry.ModItems;
import com.orbitcore.registry.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Экран сканера сигналов у панели быстрого доступа: сила сигнала полосками,
 * писк тем чаще, чем ближе планшет, и стрелка на источник после расшифровки.
 */
public final class ScannerHud {
    private static final int CYAN = 0xFF7CF4FF;
    private static final int DIM = 0xFF4A9AA8;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static float distance = -1;
    @Nullable
    private static BlockPos fixed;
    private static long updated;
    private static int pingTimer;

    private ScannerHud() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ScannerHud::tick);
        HudRenderCallback.EVENT.register(ScannerHud::render);
    }

    public static void update(float newDistance, @Nullable BlockPos newFixed) {
        distance = newDistance;
        fixed = newFixed;
        updated = System.currentTimeMillis();
    }

    private static boolean holding(ClientPlayerEntity player) {
        return player.getMainHandStack().isOf(ModItems.SIGNAL_SCANNER) || player.getOffHandStack().isOf(ModItems.SIGNAL_SCANNER);
    }

    /** Сила сигнала 0–1: рядом — полная, на краю дальности — почти ноль. */
    private static float strength() {
        return distance < 0 ? 0 : MathHelper.clamp(1 - distance / 600f, 0.04f, 1);
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.isPaused() || !holding(player) || System.currentTimeMillis() - updated > 1500 || distance < 0) {
            return;
        }
        float s = strength();
        int period = (int) MathHelper.lerp(s, 40, 4);
        if (++pingTimer >= period) {
            pingTimer = 0;
            client.getSoundManager().play(new PositionedSoundInstance(ModSounds.ECHO_SCANNER_PING.getId(), SoundCategory.PLAYERS,
                    0.25f + 0.25f * s, 0.8f + 0.6f * s, SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true));
        }
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden || !holding(player)) {
            return;
        }
        boolean live = System.currentTimeMillis() - updated < 1500;
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        int x = w / 2 + 96;
        int y = h - 40;
        int width = Math.min(150, w - x - 4);
        if (width < 60) {
            return;
        }
        context.fill(x, y, x + width, y + 30, 0xA0061418);
        context.drawText(client.textRenderer, Text.translatable("hud.orbitcore.scanner"), x + 4, y + 3, DIM, false);
        // Полоски силы сигнала
        float s = live ? strength() : 0;
        for (int i = 0; i < 5; i++) {
            int bh = 2 + i * 2;
            int bx = x + width - 30 + i * 5;
            boolean on = live && distance >= 0 && s > i / 5f;
            context.fill(bx, y + 11 - bh, bx + 3, y + 11, on ? CYAN : 0xFF1A3036);
        }
        Text line;
        if (player.isUsingItem() && player.getActiveItem().isOf(ModItems.SIGNAL_SCANNER)) {
            int progress = 100 * (SignalScannerItem.DECODE_TICKS - player.getItemUseTimeLeft()) / SignalScannerItem.DECODE_TICKS;
            line = Text.translatable("hud.orbitcore.scanner.decoding", MathHelper.clamp(progress, 0, 100));
            context.fill(x + 4, y + 25, x + 4 + (width - 8) * MathHelper.clamp(progress, 0, 100) / 100, y + 27, CYAN);
        } else if (!live || distance < 0) {
            line = Text.translatable("hud.orbitcore.scanner.silence");
        } else if (fixed != null) {
            double dx = fixed.getX() + 0.5 - player.getX();
            double dz = fixed.getZ() + 0.5 - player.getZ();
            float target = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            int sector = Math.floorMod(Math.round(MathHelper.wrapDegrees(target - player.getYaw()) / 45.0f), 8);
            line = Text.translatable("hud.orbitcore.scanner.fixed", ARROWS[sector], Math.round(Math.sqrt(dx * dx + dz * dz)));
        } else {
            line = Text.translatable("hud.orbitcore.scanner.hold");
        }
        context.drawText(client.textRenderer, line, x + 4, y + 15, CYAN, false);
    }
}
