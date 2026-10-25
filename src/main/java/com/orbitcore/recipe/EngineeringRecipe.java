package com.orbitcore.recipe;

import com.google.gson.JsonObject;
import com.orbitcore.registry.ModBlocks;
import com.orbitcore.registry.ModRecipes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.util.Identifier;

/**
 * Рецепт верстака инженера. Формат JSON как у обычного рецепта с формой
 * ("pattern", "key", "result"), но тип — {@code orbitcore:engineering}.
 */
public class EngineeringRecipe extends ShapedRecipe {
    public EngineeringRecipe(ShapedRecipe shape) {
        super(shape.getId(), shape.getGroup(), shape.getCategory(), shape.getWidth(), shape.getHeight(),
                shape.getIngredients(), shape.getOutput(null), shape.showNotification());
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ENGINEERING;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ENGINEERING_SERIALIZER;
    }

    @Override
    public ItemStack createIcon() {
        return new ItemStack(ModBlocks.ENGINEERING_TABLE);
    }

    @Override
    public boolean isIgnoredInRecipeBook() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<EngineeringRecipe> {
        @Override
        public EngineeringRecipe read(Identifier id, JsonObject json) {
            return new EngineeringRecipe(RecipeSerializer.SHAPED.read(id, json));
        }

        @Override
        public EngineeringRecipe read(Identifier id, PacketByteBuf buf) {
            return new EngineeringRecipe(RecipeSerializer.SHAPED.read(id, buf));
        }

        @Override
        public void write(PacketByteBuf buf, EngineeringRecipe recipe) {
            RecipeSerializer.SHAPED.write(buf, recipe);
        }
    }
}
