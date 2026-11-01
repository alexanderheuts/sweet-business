package com.holysweet.sweetbusiness.progression;

import com.holysweet.sweetbusiness.recipe.RecipeIndex;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class CraftableSetCalculator {

    private CraftableSetCalculator() {}

    public record RecipeRequirements(ResourceLocation output, List<Set<ResourceLocation>> ingredients) {
        public RecipeRequirements {
            ingredients = ingredients.stream().map(Set::copyOf).toList();
        }

        boolean isSatisfiedBy(Set<ResourceLocation> availableItems) {
            return !ingredients.isEmpty() && ingredients.stream()
                    .allMatch(alternatives -> alternatives.stream().anyMatch(availableItems::contains));
        }
    }

    public static Set<ResourceLocation> calculate(Set<ResourceLocation> discoveredItems, RecipeIndex index,
            Function<ResourceLocation, Optional<RecipeRequirements>> recipeLookup) {
        Set<ResourceLocation> availableItems = new HashSet<>(discoveredItems);
        var pendingItems = new ArrayDeque<>(discoveredItems);
        Set<ResourceLocation> resolvedRecipes = new HashSet<>();
        Map<ResourceLocation, Optional<RecipeRequirements>> requirements = new HashMap<>();

        while (!pendingItems.isEmpty()) {
            ResourceLocation itemId = pendingItems.removeFirst();
            for (ResourceLocation recipeId : index.getRecipesUsing(itemId)) {
                if (resolvedRecipes.contains(recipeId)) continue;

                Optional<RecipeRequirements> recipe = requirements.computeIfAbsent(recipeId, recipeLookup);
                if (recipe.isEmpty()) {
                    resolvedRecipes.add(recipeId);
                    continue;
                }

                RecipeRequirements rule = recipe.get();
                if (availableItems.contains(rule.output())) {
                    resolvedRecipes.add(recipeId);
                } else if (rule.isSatisfiedBy(availableItems)) {
                    resolvedRecipes.add(recipeId);
                    availableItems.add(rule.output());
                    pendingItems.addLast(rule.output());
                }
            }
        }
        return Set.copyOf(availableItems);
    }
}
