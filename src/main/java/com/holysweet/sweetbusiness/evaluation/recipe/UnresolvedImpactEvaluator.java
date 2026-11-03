package com.holysweet.sweetbusiness.evaluation.recipe;

import com.holysweet.sweetbusiness.recipe.RecipeIndex;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UnresolvedImpactEvaluator {

    private UnresolvedImpactEvaluator() {}

    public record BlockedRecipe(ResourceLocation output, Set<ResourceLocation> ingredients) {
        public BlockedRecipe {
            ingredients = Set.copyOf(ingredients);
        }
    }

    public record ItemImpact(ResourceLocation itemId, boolean hasProductionRecipe,
                             int directBlockedRecipes, int downstreamAffectedItems) {}

    public static List<ItemImpact> evaluate(Map<ResourceLocation, BlockedRecipe> blockedRecipes,
                                            Set<ResourceLocation> recipeOutputs, RecipeIndex index) {
        Set<ResourceLocation> candidates = new HashSet<>();
        blockedRecipes.values().forEach(recipe -> candidates.addAll(recipe.ingredients()));
        List<ItemImpact> impacts = new ArrayList<>();
        for (ResourceLocation candidate : candidates) {
            int directCount = 0;
            for (ResourceLocation recipeId : index.getRecipesUsing(candidate)) {
                BlockedRecipe recipe = blockedRecipes.get(recipeId);
                if (recipe != null && recipe.ingredients().contains(candidate)) directCount++;
            }
            impacts.add(new ItemImpact(candidate, recipeOutputs.contains(candidate), directCount,
                    countAffectedItems(candidate, blockedRecipes, index)));
        }
        impacts.sort(Comparator.comparingInt(ItemImpact::downstreamAffectedItems).reversed()
                .thenComparing(impact -> impact.itemId().toString()));
        return List.copyOf(impacts);
    }

    private static int countAffectedItems(ResourceLocation candidate, Map<ResourceLocation, BlockedRecipe> recipes,
                                           RecipeIndex index) {
        Set<ResourceLocation> visitedItems = new HashSet<>();
        Set<ResourceLocation> visitedRecipes = new HashSet<>();
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>();
        visitedItems.add(candidate);
        pending.add(candidate);

        while (!pending.isEmpty()) {
            ResourceLocation item = pending.removeFirst();
            for (ResourceLocation recipeId : index.getRecipesUsing(item)) {
                BlockedRecipe recipe = recipes.get(recipeId);
                if (recipe == null || !recipe.ingredients().contains(item) || !visitedRecipes.add(recipeId)) continue;
                // Each output is counted once, even when several recipes or cyclic paths reach it.
                if (visitedItems.add(recipe.output())) pending.addLast(recipe.output());
            }
        }
        return visitedItems.size() - 1;
    }
}
