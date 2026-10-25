package com.orbitcore.science;

import com.orbitcore.registry.ModItems;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Статья Космопедии. Открывается вместе с небесным телом или при анализе образца.
 * Заголовок и текст — в переводах: {@code cosmopedia.orbitcore.<id>.title} и {@code .text}.
 */
public record CosmopediaEntry(String id, Supplier<ItemConvertible> icon, boolean knownFromStart) {
    public static final CosmopediaEntry EARTH = new CosmopediaEntry("earth", () -> Items.GRASS_BLOCK, true);
    public static final CosmopediaEntry MOON = new CosmopediaEntry("moon", () -> Items.END_STONE, false);
    public static final CosmopediaEntry BAUXITE = new CosmopediaEntry("bauxite", () -> ModItems.RAW_BAUXITE, false);
    public static final CosmopediaEntry RUTILE = new CosmopediaEntry("rutile", () -> ModItems.RAW_RUTILE, false);
    public static final CosmopediaEntry SILICON = new CosmopediaEntry("silicon", () -> ModItems.SILICON, false);

    public static final List<CosmopediaEntry> ALL = List.of(EARTH, MOON, BAUXITE, RUTILE, SILICON);

    public String titleKey() {
        return "cosmopedia.orbitcore." + id + ".title";
    }

    public String textKey() {
        return "cosmopedia.orbitcore." + id + ".text";
    }

    @Nullable
    public static CosmopediaEntry byId(String id) {
        for (CosmopediaEntry entry : ALL) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }
}
