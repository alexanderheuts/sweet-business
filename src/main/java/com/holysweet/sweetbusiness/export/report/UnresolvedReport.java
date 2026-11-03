package com.holysweet.sweetbusiness.export.report;

import com.holysweet.sweetbusiness.evaluation.recipe.UnresolvedImpactEvaluator;
import com.holysweet.sweetbusiness.evaluation.recipe.UnresolvedImpactEvaluator.BlockedRecipe;
import com.holysweet.sweetbusiness.export.ExportData;
import com.holysweet.sweetbusiness.export.ExportReport;
import com.holysweet.sweetbusiness.recipe.RecipeIndex;
import com.holysweet.sweetbusiness.valuation.ItemValuation;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class UnresolvedReport implements ExportReport {

    private static final List<String> COLUMNS = List.of("item_id", "has_production_recipe",
            "direct_blocked_recipes", "downstream_affected_items");

    @Override
    public String name() {
        return "unresolved";
    }

    @Override
    public Optional<ExportData> snapshot(MinecraftServer server) {
        return ValuationEngine.getInstance().getValuations().map(values -> buildReport(server, values));
    }

    private static ExportData buildReport(MinecraftServer server, Map<Item, ItemValuation> values) {
        Map<ResourceLocation, BlockedRecipe> blockedRecipes = new HashMap<>();
        Set<ResourceLocation> recipeOutputs = new HashSet<>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack output = recipe.getResultItem(server.registryAccess());
            if (output.isEmpty()) continue;
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(output.getItem());
            recipeOutputs.add(outputId);
            if (values.getOrDefault(output.getItem(), ItemValuation.UNKNOWN).isKnown()) continue;

            Set<ResourceLocation> blockedIngredients = findBlockedIngredients(recipe, values);
            if (!blockedIngredients.isEmpty()) {
                blockedRecipes.put(holder.id(), new BlockedRecipe(outputId, blockedIngredients));
            }
        }

        var impacts = UnresolvedImpactEvaluator.evaluate(blockedRecipes, recipeOutputs, RecipeIndex.getInstance());
        List<List<?>> rows = new ArrayList<>();
        impacts.forEach(impact -> rows.add(List.of(impact.itemId().toString(), impact.hasProductionRecipe(),
                impact.directBlockedRecipes(), impact.downstreamAffectedItems())));
        return new ExportData(COLUMNS, rows);
    }

    private static Set<ResourceLocation> findBlockedIngredients(Recipe<?> recipe, Map<Item, ItemValuation> values) {
        Set<ResourceLocation> blocked = new HashSet<>();
        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] alternatives = ingredient.getItems();
            // A usable alternative satisfies the whole slot; its unknown siblings do not block this recipe.
            boolean satisfied = Arrays.stream(alternatives).filter(stack -> !stack.isEmpty())
                    .anyMatch(stack -> isUsable(values.getOrDefault(stack.getItem(), ItemValuation.UNKNOWN)));
            if (satisfied) continue;
            for (ItemStack alternative : alternatives) {
                if (alternative.isEmpty()) continue;
                if (!values.getOrDefault(alternative.getItem(), ItemValuation.UNKNOWN).isKnown()) {
                    blocked.add(BuiltInRegistries.ITEM.getKey(alternative.getItem()));
                }
            }
        }
        return blocked;
    }

    private static boolean isUsable(ItemValuation value) {
        return value.isKnown() && Float.isFinite(value.value()) && value.value() > 0.00001f;
    }
}
