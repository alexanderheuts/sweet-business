package com.holysweet.sweetbusiness.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RecipeIndex {

    private static volatile RecipeIndex instance = new RecipeIndex(Map.of());

    private final Map<ResourceLocation, Set<ResourceLocation>> recipesByIngredient;

    private RecipeIndex(Map<ResourceLocation, Set<ResourceLocation>> recipesByIngredient) {
        Map<ResourceLocation, Set<ResourceLocation>> snapshot = new HashMap<>();
        recipesByIngredient.forEach((itemId, recipeIds) -> snapshot.put(itemId, Set.copyOf(recipeIds)));
        this.recipesByIngredient = Map.copyOf(snapshot);
    }

    public static RecipeIndex getInstance() {
        return instance;
    }

    public Set<ResourceLocation> getRecipesUsing(ResourceLocation itemId) {
        return recipesByIngredient.getOrDefault(itemId, Set.of());
    }

    public static void rebuild(RecipeManager recipeManager) {
        Map<ResourceLocation, Set<ResourceLocation>> recipesByIngredient = new HashMap<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            for (Ingredient ingredient : holder.value().getIngredients()) {
                if (ingredient.isEmpty() || ingredient.hasNoItems()) continue;

                for (ItemStack stack : ingredient.getItems()) {
                    if (stack.isEmpty()) continue;

                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    recipesByIngredient.computeIfAbsent(itemId, key -> new HashSet<>()).add(holder.id());
                }
            }
        }
        instance = new RecipeIndex(recipesByIngredient);
    }

    public static void clear() {
        instance = new RecipeIndex(Map.of());
    }
}
