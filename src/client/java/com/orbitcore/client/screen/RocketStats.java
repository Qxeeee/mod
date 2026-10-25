package com.orbitcore.client.screen;

import com.orbitcore.rocket.RocketDesign;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Сводка по ракете для экранов стенда и ракеты: ступени, масса, тяга, дельта-V и цели.
 */
final class RocketStats {
    private RocketStats() {
    }

    /** Рисует сводку в столбик и возвращает y под последней строкой. */
    static int draw(DrawContext context, TextRenderer text, RocketDesign design, double fill, int x, int y) {
        y = drawSummary(context, text, design, fill, x, y);
        for (RocketDesign.Destination destination : RocketDesign.Destination.values()) {
            if (destination.fromMoon()) {
                continue;
            }
            boolean ok = design.canReach(destination, fill);
            Text line = Text.literal(ok ? "✔ " : "✘ ").append(Text.translatable(destination.translationKey()));
            context.drawText(text, line, x, y, ok ? OrbitStyle.GREEN : OrbitStyle.TEXT_FAINT, false);
            y += 10;
        }
        return y;
    }

    /** Ступени, масса, тяговооружённость и дельта-V. */
    static int drawSummary(DrawContext context, TextRenderer text, RocketDesign design, double fill, int x, int y) {
        double twr = design.liftoffTwr(fill);
        context.drawText(text, Text.translatable("rocket.orbitcore.stat.stages_mass", design.stages().size(),
                String.format("%.1f", design.mass(fill))), x, y, OrbitStyle.TEXT, false);
        y += 10;
        context.drawText(text, Text.translatable("rocket.orbitcore.stat.twr", String.format("%.2f", twr)), x, y,
                twr > 1.0 ? OrbitStyle.TEXT : OrbitStyle.RED, false);
        y += 10;
        context.drawText(text, Text.translatable("rocket.orbitcore.stat.dv", (int) design.deltaV(fill)), x, y, OrbitStyle.ACCENT, false);
        return y + 11;
    }

    /** Список проблем конструкции: сначала ошибки, потом предупреждения. */
    static List<Text> problems(RocketDesign design) {
        List<Text> lines = new ArrayList<>();
        for (RocketDesign.Problem problem : design.problems()) {
            if (problem.isError()) {
                lines.add(Text.literal("✘ ").append(Text.translatable(problem.translationKey())).styled(s -> s.withColor(0xFF8A6A)));
            }
        }
        for (RocketDesign.Problem problem : design.problems()) {
            if (!problem.isError()) {
                lines.add(Text.literal("⚠ ").append(Text.translatable(problem.translationKey())).styled(s -> s.withColor(0xF2C66D)));
            }
        }
        return lines;
    }
}
