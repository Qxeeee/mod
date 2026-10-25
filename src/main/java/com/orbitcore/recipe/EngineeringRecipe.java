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
import net.minecraft.util.JsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Рецепт верстака инженера. Формат JSON как у обычного рецепта с формой
 * ("pattern", "key", "result"), но тип — {@code orbitcore:engineering}.
 * Необязательное поле {@code "research"} — исследование, без которого рецепт не работает.
 */
public class EngineeringRecipe extends ShapedRecipe {
    @Nullable
    private final Identifier research;

    public EngineeringRecipe(ShapedRecipe shape, @Nullable Identifier research) {
        super(shape.getId(), shape.getGroup(), shape.getCategory(), shape.getWidth(), shape.getHeight(),
                shape.getIngredients(), shape.getOutput(null), shape.showNotification());
        this.research = research;
    }

    @Nullable
    public Identifier research() {
        return research;
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
            String research = JsonHelper.getString(json, "research", null);
            return new EngineeringRecipe(RecipeSerializer.SHAPED.read(id, json), research == null ? null : new Identifier(research));
        }

        @Override
        public EngineeringRecipe read(Identifier id, PacketByteBuf buf) {
            ShapedRecipe shape = RecipeSerializer.SHAPED.read(id, buf);
            return new EngineeringRecipe(shape, buf.readNullable(PacketByteBuf::readIdentifier));
        }

        @Override
        public void write(PacketByteBuf buf, EngineeringRecipe recipe) {
            RecipeSerializer.SHAPED.write(buf, recipe);
            buf.writeNullable(recipe.research, PacketByteBuf::writeIdentifier);
        }
    }
}
