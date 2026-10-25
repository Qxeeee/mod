package com.orbitcore.recipe;

import com.google.gson.JsonObject;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModRecipes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Анализ образца: предмет расходуется, программа получает очки образцов
 * и, если указано, статью Космопедии.
 *
 * <pre>
 * { "type": "orbitcore:analysis", "ingredient": {...}, "samples": 3, "entry": "rutile" }
 * </pre>
 */
public record AnalysisRecipe(Identifier id, Ingredient ingredient, int samples, @Nullable String entry)
        implements Recipe<Inventory> {

    @Override
    public boolean matches(Inventory inventory, World world) {
        return ingredient.test(inventory.getStack(0));
    }

    @Override
    public ItemStack craft(Inventory inventory, DynamicRegistryManager registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean fits(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public DefaultedList<Ingredient> getIngredients() {
        return DefaultedList.copyOf(Ingredient.EMPTY, ingredient);
    }

    @Override
    public ItemStack createIcon() {
        return new ItemStack(ModBlocks.SAMPLE_ANALYZER);
    }

    @Override
    public boolean isIgnoredInRecipeBook() {
        return true;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ANALYSIS_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ANALYSIS;
    }

    public static class Serializer implements RecipeSerializer<AnalysisRecipe> {
        @Override
        public AnalysisRecipe read(Identifier id, JsonObject json) {
            return new AnalysisRecipe(id,
                    Ingredient.fromJson(JsonHelper.getElement(json, "ingredient")),
                    JsonHelper.getInt(json, "samples"),
                    JsonHelper.getString(json, "entry", null));
        }

        @Override
        public AnalysisRecipe read(Identifier id, PacketByteBuf buf) {
            return new AnalysisRecipe(id, Ingredient.fromPacket(buf), buf.readVarInt(), buf.readNullable(PacketByteBuf::readString));
        }

        @Override
        public void write(PacketByteBuf buf, AnalysisRecipe recipe) {
            recipe.ingredient.write(buf);
            buf.writeVarInt(recipe.samples);
            buf.writeNullable(recipe.entry, PacketByteBuf::writeString);
        }
    }
}
