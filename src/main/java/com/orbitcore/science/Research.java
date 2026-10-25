package com.orbitcore.science;

import com.orbitcore.OrbitCore;
import com.orbitcore.registry.ModBlocks;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Узел дерева исследований. Исследование открывает рецепты верстака инженера,
 * у которых в JSON указано поле {@code "research"}.
 *
 * @param x,y положение узла в окне ЦУП (в клетках)
 */
public record Research(Identifier id, Supplier<ItemConvertible> icon, Map<ScienceType, Integer> cost,
                       List<Identifier> parents, @Nullable CelestialBody requiredDiscovery, int x, int y) {

    public static final Research ASTRONOMY = new Research(OrbitCore.id("astronomy"), () -> ModBlocks.TELESCOPE,
            cost(0, 0, 0), List.of(), null, 0, 1);
    public static final Research SPECTROSCOPY = new Research(OrbitCore.id("spectroscopy"), () -> ModBlocks.SAMPLE_ANALYZER,
            cost(8, 0, 0), List.of(ASTRONOMY.id), null, 2, 0);
    public static final Research ROCKETRY = new Research(OrbitCore.id("rocketry"), () -> Items.FIREWORK_ROCKET,
            cost(20, 12, 0), List.of(SPECTROSCOPY.id), CelestialBody.MOON, 4, 1);

    public static final List<Research> ALL = List.of(ASTRONOMY, SPECTROSCOPY, ROCKETRY);

    /** Исследования, открытые с самого начала. */
    public boolean isRoot() {
        return parents.isEmpty();
    }

    public int cost(ScienceType type) {
        return cost.getOrDefault(type, 0);
    }

    public String translationKey() {
        return "research." + id.getNamespace() + "." + id.getPath();
    }

    @Nullable
    public static Research byId(Identifier id) {
        for (Research research : ALL) {
            if (research.id.equals(id)) {
                return research;
            }
        }
        return null;
    }

    private static Map<ScienceType, Integer> cost(int observations, int samples, int telemetry) {
        Map<ScienceType, Integer> map = new EnumMap<>(ScienceType.class);
        map.put(ScienceType.OBSERVATION, observations);
        map.put(ScienceType.SAMPLE, samples);
        map.put(ScienceType.TELEMETRY, telemetry);
        return map;
    }
}
