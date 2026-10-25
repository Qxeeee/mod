package com.orbitcore.client.story;

import com.orbitcore.registry.ModSounds;
import com.orbitcore.story.HoloTablets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Запись с голографического планшета: бирюзовая проекция с бегущими строками развёртки,
 * текст печатается по буквам, иногда его разъедают помехи. Звучит голос записи, если он есть.
 */
public class HoloLogScreen extends Screen {
    private static final int CYAN = 0xFF7CF4FF;
    private static final int DIM = 0xFF4A9AA8;
    private static final long CHAR_MS = 22;
    private static final Random RANDOM = Random.create();

    private final String log;
    private long opened;

    public HoloLogScreen(String log) {
        super(Text.translatable(HoloTablets.titleKey(log)));
        this.log = log;
    }

    @Override
    protected void init() {
        if (opened == 0) {
            opened = System.currentTimeMillis();
            if (client != null) {
                client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.ECHO_HOLOGRAM, 1.0f, 0.8f));
                VoiceLines.play(client, log);
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        if (client != null) {
            VoiceLines.stop(client);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Щелчок — показать текст целиком
        opened = 1;
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xB0000608);
        int panelWidth = Math.min(340, width - 40);
        String body = Text.translatable("holo.orbitcore." + log + ".text").getString();
        List<OrderedText> full = textRenderer.wrapLines(StringVisitable.plain(body), panelWidth - 24);
        int panelHeight = Math.min(height - 30, 48 + full.size() * 10 + 16);
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        long now = System.currentTimeMillis();
        float flicker = 0.85f + 0.15f * MathHelper.sin(now / 90f) * MathHelper.sin(now / 37f);
        int glass = (int) (flicker * 0x50) << 24 | 0x0A3A48;
        context.fill(x, y, x + panelWidth, y + panelHeight, glass);
        context.fill(x, y, x + panelWidth, y + 1, CYAN);
        context.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, CYAN);
        context.fill(x, y, x + 1, y + panelHeight, DIM);
        context.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, DIM);
        // Строки развёртки бегут вниз
        int scan = (int) (now / 30 % panelHeight);
        for (int sy = y + 2; sy < y + panelHeight - 1; sy += 3) {
            context.fill(x + 1, sy, x + panelWidth - 1, sy + 1, 0x14_7CF4FF);
        }
        context.fill(x + 1, y + scan, x + panelWidth - 1, y + scan + 2, 0x307CF4FF);

        context.drawText(textRenderer, getTitle(), x + 12, y + 10, CYAN, false);
        Text author = Text.translatable("holo.orbitcore." + log + ".author");
        context.drawText(textRenderer, author, x + panelWidth - 12 - textRenderer.getWidth(author), y + 10, DIM, false);
        context.fill(x + 12, y + 22, x + panelWidth - 12, y + 23, DIM);

        // Текст печатается по буквам и иногда рассыпается помехами
        int shown = opened == 1 ? body.length() : (int) Math.min(body.length(), (now - opened) / CHAR_MS);
        StringBuilder revealed = new StringBuilder(body.substring(0, shown));
        for (int i = 0; i < revealed.length(); i++) {
            if (revealed.charAt(i) != ' ' && revealed.charAt(i) != '\n' && RANDOM.nextFloat() < 0.004f) {
                revealed.setCharAt(i, '▒');
            }
        }
        List<OrderedText> lines = textRenderer.wrapLines(StringVisitable.plain(revealed.toString()), panelWidth - 24);
        int ty = y + 30;
        for (OrderedText line : lines) {
            if (ty > y + panelHeight - 20) {
                break;
            }
            context.drawText(textRenderer, line, x + 12, ty, 0xFFDDFBFF, false);
            ty += 10;
        }
        if (shown < body.length() && (now / 300) % 2 == 0) {
            context.fill(x + 12, ty, x + 17, ty + 8, CYAN);
        }
        Text hint = Text.translatable("holo.orbitcore.close");
        context.drawText(textRenderer, hint, x + panelWidth - 12 - textRenderer.getWidth(hint), y + panelHeight - 13, DIM, false);
    }
}
