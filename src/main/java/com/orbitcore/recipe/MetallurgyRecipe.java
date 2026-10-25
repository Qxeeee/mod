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
 * Плавка в металлургической печи: сырьё + восстановитель (уголь) при нужной температуре.
 *
 * <pre>
 * { "type": "orbitcore:metallurgy", "ingredient": {...}, "reductant": {...},
 *   "result": {"item": "..."}, "heat": 1200, "time": 200 }
 * </pre>
 */
public record MetallurgyRecipe(Identifier id, Ingredient ingredient, Ingredient reductant, ItemStack result, int heat, int time)
        implements Recipe<Inventory> {

    @Override
    public boolean matches(Inventory inventory, World world) {
        return ingredient.test(inventory.getStack(0)) && reductant.test(inventory.getStack(1));
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
        return DefaultedList.copyOf(Ingredient.EMPTY, ingredient, reductant);
    }

    @Override
    public ItemStack createIcon() {
        return new ItemStack(ModBlocks.METALLURGICAL_FURNACE);
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
        return ModRecipes.METALLURGY_SERIALIZER;
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.METALLURGY;
    }

    public static class Serializer implements RecipeSerializer<MetallurgyRecipe> {
        @Override
        public MetallurgyRecipe read(Identifier id, JsonObject json) {
            return new MetallurgyRecipe(id,
                    Ingredient.fromJson(JsonHelper.getElement(json, "ingredient")),
                    Ingredient.fromJson(JsonHelper.getElement(json, "reductant")),
                    ShapedRecipe.outputFromJson(JsonHelper.getObject(json, "result")),
                    JsonHelper.getInt(json, "heat"),
                    JsonHelper.getInt(json, "time", 200));
        }

        @Override
        public MetallurgyRecipe read(Identifier id, PacketByteBuf buf) {
            return new MetallurgyRecipe(id, Ingredient.fromPacket(buf), Ingredient.fromPacket(buf),
                    buf.readItemStack(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void write(PacketByteBuf buf, MetallurgyRecipe recipe) {
            recipe.ingredient.write(buf);
            recipe.reductant.write(buf);
            buf.writeItemStack(recipe.result);
            buf.writeVarInt(recipe.heat);
            buf.writeVarInt(recipe.time);
        }
    }
}
