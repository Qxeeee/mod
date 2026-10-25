package com.orbitcore.registry;

import com.orbitcore.OrbitCore;
import com.orbitcore.recipe.ElectrolysisRecipe;
import com.orbitcore.recipe.EngineeringRecipe;
import com.orbitcore.recipe.MetallurgyRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModRecipes {
    public static final RecipeType<MetallurgyRecipe> METALLURGY = type("metallurgy");
    public static final RecipeType<ElectrolysisRecipe> ELECTROLYSIS = type("electrolysis");
    public static final RecipeType<EngineeringRecipe> ENGINEERING = type("engineering");

    public static final RecipeSerializer<MetallurgyRecipe> METALLURGY_SERIALIZER =
            serializer("metallurgy", new MetallurgyRecipe.Serializer());
    public static final RecipeSerializer<ElectrolysisRecipe> ELECTROLYSIS_SERIALIZER =
            serializer("electrolysis", new ElectrolysisRecipe.Serializer());
    public static final RecipeSerializer<EngineeringRecipe> ENGINEERING_SERIALIZER =
            serializer("engineering", new EngineeringRecipe.Serializer());

    private ModRecipes() {
    }

    private static <T extends Recipe<?>> RecipeType<T> type(String name) {
        Identifier id = OrbitCore.id(name);
        return Registry.register(Registries.RECIPE_TYPE, id, new RecipeType<T>() {
            @Override
            public String toString() {
                return id.toString();
            }
        });
    }

    private static <S extends RecipeSerializer<?>> S serializer(String name, S serializer) {
        return Registry.register(Registries.RECIPE_SERIALIZER, OrbitCore.id(name), serializer);
    }

    public static void register() {
    }
}
