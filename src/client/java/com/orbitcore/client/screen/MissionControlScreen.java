package com.orbitcore.client.screen;

import com.orbitcore.OrbitCore;
import com.orbitcore.client.network.OrbitClientNetworking;
import com.orbitcore.client.science.ClientSpaceProgram;
import com.orbitcore.science.CelestialBody;
import com.orbitcore.science.CosmopediaEntry;
import com.orbitcore.science.Research;
import com.orbitcore.science.ScienceType;
import com.orbitcore.science.SpaceProgram;
import com.orbitcore.science.SpaceProgram.ResearchStatus;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.orbitcore.client.screen.OrbitStyle.*;

/**
 * Центр управления полётами: очки программы, дерево исследований, открытые тела, Космопедия.
 */
public class MissionControlScreen extends Screen {
    private static final Identifier MOON = OrbitCore.id("textures/gui/telescope/moon.png");
    private static final int W = 276;
    private static final int H = 206;
    private static final int CELL = 62;
    private static final int NODE = 24;
    private static final double MAX_DISTANCE_SQ = 8 * 8;

    private enum Tab {
        RESEARCH("research"), BODIES("bodies"), COSMOPEDIA("cosmopedia");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private final BlockPos pos;
    private Tab tab = Tab.RESEARCH;
    private int left;
    private int top;
    @Nullable
    private Research selected;
    private String entry = CosmopediaEntry.EARTH.id();
    private int textScroll;

    public MissionControlScreen(BlockPos pos) {
        super(Text.translatable("block.orbitcore.mission_control"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    /** Переключить вкладку (используется фототуром). */
    public void showTab(int index) {
        tab = Tab.values()[index];
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        if (client == null || client.player == null || client.player.squaredDistanceTo(pos.toCenterPos()) > MAX_DISTANCE_SQ) {
            close();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        SpaceProgram program = ClientSpaceProgram.get();
        OrbitStyle.panel(context, left, top, W, H);
        context.drawText(textRenderer, title, left + 8, top + 7, ACCENT, false);
        drawTabs(context, mouseX, mouseY);
        drawPoints(context, program);
        context.fill(left + 7, top + 50, left + W - 7, top + 51, ACCENT_DARK);

        switch (tab) {
            case RESEARCH -> drawResearch(context, program, mouseX, mouseY);
            case BODIES -> drawBodies(context, program);
            case COSMOPEDIA -> drawCosmopedia(context, program, mouseX, mouseY);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------------ шапка

    private void drawTabs(DrawContext context, int mouseX, int mouseY) {
        for (Tab t : Tab.values()) {
            int x = tabX(t);
            int y = top + 18;
            int w = tabWidth(t);
            boolean active = t == tab;
            boolean hover = inside(mouseX, mouseY, x, y, w, 14);
            context.fill(x, y, x + w, y + 14, active ? ACCENT_DARK : hover ? PANEL_LIGHT : PANEL_DARK);
            context.drawText(textRenderer, tabLabel(t), x + 7, y + 3, active ? TEXT : TEXT_DIM, false);
        }
    }

    private Text tabLabel(Tab t) {
        return Text.translatable("gui.orbitcore.mission_control.tab." + t.key);
    }

    /** Ширина вкладки по длине подписи — переводы бывают разной длины. */
    private int tabWidth(Tab t) {
        return textRenderer.getWidth(tabLabel(t)) + 14;
    }

    private int tabX(Tab t) {
        int x = left + 8;
        for (Tab other : Tab.values()) {
            if (other == t) {
                break;
            }
            x += tabWidth(other) + 2;
        }
        return x;
    }

    private void drawPoints(DrawContext context, SpaceProgram program) {
        int x = left + 8;
        for (ScienceType type : ScienceType.values()) {
            Text text = Text.translatable(type.translationKey()).append(": ").append(String.valueOf(program.points(type)));
            context.fill(x, top + 39, x + 5, top + 44, type.color());
            context.drawText(textRenderer, text, x + 8, top + 38, TEXT, false);
            x += textRenderer.getWidth(text) + 18;
        }
    }

    // ------------------------------------------------------------------ исследования

    private int nodeX(Research node) {
        return left + 20 + node.x() * CELL;
    }

    private int nodeY(Research node) {
        return top + 62 + node.y() * 34;
    }

    private void drawResearch(DrawContext context, SpaceProgram program, int mouseX, int mouseY) {
        OrbitStyle.inset(context, left + 8, top + 55, W - 16, 78);
        // Связи между узлами: от родителя вправо, затем к ребёнку
        for (Research node : Research.ALL) {
            for (Identifier parentId : node.parents()) {
                Research parent = Research.byId(parentId);
                if (parent == null) {
                    continue;
                }
                int color = program.hasResearch(node.id()) ? ACCENT : TEXT_FAINT;
                int x1 = nodeX(parent) + NODE;
                int y1 = nodeY(parent) + NODE / 2;
                int x2 = nodeX(node);
                int y2 = nodeY(node) + NODE / 2;
                int mid = (x1 + x2) / 2;
                context.fill(x1, y1, mid + 1, y1 + 1, color);
                context.fill(mid, Math.min(y1, y2), mid + 1, Math.max(y1, y2) + 1, color);
                context.fill(mid, y2, x2, y2 + 1, color);
            }
        }

        Research hovered = null;
        for (Research node : Research.ALL) {
            int x = nodeX(node);
            int y = nodeY(node);
            ResearchStatus status = program.status(node);
            int border = switch (status) {
                case DONE -> ACCENT;
                case AVAILABLE -> pulse(GOLD);
                case NOT_ENOUGH_POINTS, NEEDS_DISCOVERY -> TEXT_DIM;
                case LOCKED -> TEXT_FAINT;
            };
            if (node == selected) {
                context.fill(x - 2, y - 2, x + NODE + 2, y + NODE + 2, TEXT);
            }
            context.fill(x, y, x + NODE, y + NODE, border);
            context.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, status == ResearchStatus.DONE ? ACCENT_DARK : SLOT);
            context.drawItem(new ItemStack(node.icon().get()), x + 4, y + 4);
            if (status == ResearchStatus.LOCKED) {
                context.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, 0xA0000000);
            }
            if (inside(mouseX, mouseY, x, y, NODE, NODE)) {
                hovered = node;
            }
        }

        Research shown = hovered != null ? hovered : selected;
        drawResearchDetails(context, program, shown);
    }

    private void drawResearchDetails(DrawContext context, SpaceProgram program, @Nullable Research node) {
        int x = left + 10;
        int y = top + 139;
        OrbitStyle.inset(context, left + 8, top + 137, W - 16, H - 145);
        if (node == null) {
            context.drawTextWrapped(textRenderer, Text.translatable("gui.orbitcore.mission_control.research_hint"), x + 2, y + 4, W - 24, TEXT_DIM);
            return;
        }
        ResearchStatus status = program.status(node);
        context.drawText(textRenderer, Text.translatable(node.translationKey()), x + 2, y + 3, status == ResearchStatus.DONE ? ACCENT : TEXT, false);
        Text statusText = Text.translatable("gui.orbitcore.research.status." + status.name().toLowerCase());
        context.drawText(textRenderer, statusText, left + W - 12 - textRenderer.getWidth(statusText), y + 3,
                status == ResearchStatus.DONE ? ACCENT : status == ResearchStatus.AVAILABLE ? GOLD : TEXT_DIM, false);
        int lineY = y + 15;
        for (OrderedText line : textRenderer.wrapLines(Text.translatable(node.translationKey() + ".desc"), W - 26)) {
            context.drawText(textRenderer, line, x + 2, lineY, TEXT_DIM, false);
            lineY += 10;
        }
        if (status != ResearchStatus.DONE) {
            int costX = x + 2;
            for (ScienceType type : ScienceType.values()) {
                int cost = node.cost(type);
                if (cost <= 0) {
                    continue;
                }
                Text text = Text.translatable(type.translationKey()).append(" " + cost);
                context.fill(costX, lineY + 3, costX + 5, lineY + 8, type.color());
                context.drawText(textRenderer, text, costX + 8, lineY + 2, program.points(type) >= cost ? GREEN : RED, false);
                costX += 16 + textRenderer.getWidth(text);
            }
            CelestialBody body = node.requiredDiscovery();
            if (body != null) {
                Text need = Text.translatable("gui.orbitcore.research.requires_discovery", Text.translatable(body.translationKey()));
                context.drawText(textRenderer, need, costX, lineY + 2, program.isDiscovered(body) ? GREEN : RED, false);
            }
        }
    }

    // ------------------------------------------------------------------ небесные тела

    private void drawBodies(DrawContext context, SpaceProgram program) {
        OrbitStyle.inset(context, left + 8, top + 55, W - 16, H - 63);
        int y = top + 61;
        for (CelestialBody body : CelestialBody.values()) {
            boolean known = program.isDiscovered(body);
            int x = left + 14;
            if (!known) {
                context.fill(x, y, x + 24, y + 24, PANEL_DARK);
                context.drawText(textRenderer, "?", x + 10, y + 8, TEXT_FAINT, false);
            } else if (body == CelestialBody.MOON) {
                context.drawTexture(MOON, x, y, 24, 24, 0, 0, 64, 64, 512, 64);
            } else {
                context.drawItem(new ItemStack(Items.GRASS_BLOCK), x + 4, y + 4);
            }
            Text name = known ? Text.translatable(body.translationKey()) : Text.translatable("gui.orbitcore.unknown");
            context.drawText(textRenderer, name, x + 32, y + 3, known ? TEXT : TEXT_FAINT, false);
            Text facts = known ? Text.translatable(body.translationKey() + ".facts") : Text.translatable("gui.orbitcore.body.undiscovered");
            context.drawTextWrapped(textRenderer, facts, x + 32, y + 14, W - 60, TEXT_DIM);
            y += 38;
        }
    }

    // ------------------------------------------------------------------ Космопедия

    private void drawCosmopedia(DrawContext context, SpaceProgram program, int mouseX, int mouseY) {
        int listW = 92;
        OrbitStyle.inset(context, left + 8, top + 55, listW, H - 63);
        OrbitStyle.inset(context, left + 12 + listW, top + 55, W - 20 - listW, H - 63);
        int y = top + 59;
        for (CosmopediaEntry e : CosmopediaEntry.ALL) {
            boolean open = program.hasEntry(e.id());
            boolean active = e.id().equals(entry);
            if (active) {
                context.fill(left + 9, y - 2, left + 8 + listW - 1, y + 14, ACCENT_DARK);
            } else if (open && inside(mouseX, mouseY, left + 9, y - 2, listW - 2, 16)) {
                context.fill(left + 9, y - 2, left + 8 + listW - 1, y + 14, PANEL_LIGHT);
            }
            if (open) {
                context.drawItem(new ItemStack(e.icon().get()), left + 11, y - 1);
            }
            Text name = open ? Text.translatable(e.titleKey()) : Text.translatable("gui.orbitcore.unknown");
            context.drawText(textRenderer, trim(name, listW - 26), left + 29, y + 3, open ? TEXT : TEXT_FAINT, false);
            y += 16;
        }

        CosmopediaEntry current = CosmopediaEntry.byId(entry);
        if (current == null || !program.hasEntry(current.id())) {
            return;
        }
        int textX = left + 17 + listW;
        int textW = W - 30 - listW;
        context.drawText(textRenderer, Text.translatable(current.titleKey()), textX, top + 60, ACCENT, false);
        List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(current.textKey()), textW);
        int visible = (H - 86) / 10;
        textScroll = MathHelper.clamp(textScroll, 0, Math.max(0, lines.size() - visible));
        int lineY = top + 74;
        for (int i = textScroll; i < Math.min(lines.size(), textScroll + visible); i++) {
            context.drawText(textRenderer, lines.get(i), textX, lineY, TEXT_DIM, false);
            lineY += 10;
        }
    }

    private Text trim(Text text, int width) {
        String s = text.getString();
        if (textRenderer.getWidth(s) <= width) {
            return text;
        }
        return Text.literal(textRenderer.trimToWidth(s, width - 6) + "…");
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Tab t : Tab.values()) {
            if (inside(mouseX, mouseY, tabX(t), top + 18, tabWidth(t), 14)) {
                tab = t;
                click();
                return true;
            }
        }
        if (tab == Tab.RESEARCH) {
            for (Research node : Research.ALL) {
                if (inside(mouseX, mouseY, nodeX(node), nodeY(node), NODE, NODE)) {
                    if (selected == node && ClientSpaceProgram.get().status(node) == ResearchStatus.AVAILABLE) {
                        OrbitClientNetworking.sendUnlockResearch(pos, node.id());
                    }
                    selected = node;
                    click();
                    return true;
                }
            }
        } else if (tab == Tab.COSMOPEDIA) {
            int y = top + 59;
            for (CosmopediaEntry e : CosmopediaEntry.ALL) {
                if (inside(mouseX, mouseY, left + 9, y - 2, 90, 16) && ClientSpaceProgram.get().hasEntry(e.id())) {
                    entry = e.id();
                    textScroll = 0;
                    click();
                    return true;
                }
                y += 16;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (tab == Tab.COSMOPEDIA) {
            textScroll -= (int) Math.signum(amount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void click() {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int pulse(int color) {
        float t = (System.currentTimeMillis() % 1200) / 1200f;
        float k = 0.65f + 0.35f * MathHelper.sin(t * MathHelper.TAU);
        int r = (int) (((color >> 16) & 0xFF) * k);
        int g = (int) (((color >> 8) & 0xFF) * k);
        int b = (int) ((color & 0xFF) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
