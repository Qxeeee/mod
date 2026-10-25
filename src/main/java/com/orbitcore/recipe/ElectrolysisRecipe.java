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
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/**
 * Электролиз: сырьё превращается в продукт за счёт энергии.
 *
 * <pre>
 * { "type": "orbitcore:electrolysis", "ingredient": {...}, "result": {"item": "..."},
 *   "energy_per_tick": 24, "time": 200 }
 * </pre>
 */
public record ElectrolysisRecipe(Identifier id, Ingredient ingredient, ItemStack result, int energyPerTick, int time)
        implements Recipe<Inventory> {

    @Override
    public boolean matches(Inventory inventory, World world) {
        return ingredient.test(inventory.getStack(0));
    }

    @Override
    public ItemStack craft(Inventory inventory, DynamicRegistryManager registryManager) {
        return result.copy();
    }

    @Override
    public boolean fits(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        return result;
    }

    @Override
    public DefaultedList<Ingredient> getIngredients() {
        return DefaultedList.copyOf(Ingredient.EMPTY, ingredient);
    }

    @Override
    public ItemStack createIcon() {
        return new ItemStack(ModBlocks.ELECTROLYZER);
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
        return ModRecipes.ELECTROLYSIS_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ELECTROLYSIS;
    }

    public static class Serializer implements RecipeSerializer<ElectrolysisRecipe> {
        @Override
        public ElectrolysisRecipe read(Identifier id, JsonObject json) {
            return new ElectrolysisRecipe(id,
                    Ingredient.fromJson(JsonHelper.getElement(json, "ingredient")),
                    ShapedRecipe.outputFromJson(JsonHelper.getObject(json, "result")),
                    JsonHelper.getInt(json, "energy_per_tick"),
                    JsonHelper.getInt(json, "time", 200));
        }

        @Override
        public ElectrolysisRecipe read(Identifier id, PacketByteBuf buf) {
            return new ElectrolysisRecipe(id, Ingredient.fromPacket(buf), buf.readItemStack(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void write(PacketByteBuf buf, ElectrolysisRecipe recipe) {
            recipe.ingredient.write(buf);
            buf.writeItemStack(recipe.result);
            buf.writeVarInt(recipe.energyPerTick);
            buf.writeVarInt(recipe.time);
        }
    }
}
