package com.holysweet.sweetbusiness.export.report;

import com.holysweet.sweetbusiness.evaluation.recipe.UnresolvedImpactEvaluator;
import com.holysweet.sweetbusiness.evaluation.recipe.UnresolvedImpactEvaluator.BlockedRecipe;
import com.holysweet.sweetbusiness.export.ExportData;
import com.holysweet.sweetbusiness.export.ExportReport;
import com.holysweet.sweetbusiness.recipe.RecipeIndex;
import com.holysweet.sweetbusiness.valuation.ItemValuation;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.IngredientInput;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.RecipeInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
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
        var engine = ValuationEngine.getInstance();
        return engine.getCalculationInputs().flatMap(inputs -> engine.getValuations()
                .map(values -> buildReport(inputs.recipes(), values, RecipeIndex.getInstance())));
    }

    static ExportData buildReport(List<RecipeInput> recipes, Map<Item, ItemValuation> values, RecipeIndex index) {
        Map<ResourceLocation, BlockedRecipe> blockedRecipes = new HashMap<>();
        Set<ResourceLocation> recipeOutputs = new HashSet<>();
        for (RecipeInput recipe : recipes) {
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(recipe.output());
            recipeOutputs.add(outputId);
            if (values.getOrDefault(recipe.output(), ItemValuation.UNKNOWN).isKnown()) continue;

            Set<ResourceLocation> blockedIngredients = findBlockedIngredients(recipe, values);
            if (!blockedIngredients.isEmpty()) {
                blockedRecipes.put(recipe.id(), new BlockedRecipe(outputId, blockedIngredients));
            }
        }

        var impacts = UnresolvedImpactEvaluator.evaluate(blockedRecipes, recipeOutputs, index);
        List<List<?>> rows = new ArrayList<>();
        impacts.forEach(impact -> rows.add(List.of(impact.itemId().toString(), impact.hasProductionRecipe(),
                impact.directBlockedRecipes(), impact.downstreamAffectedItems())));
        return new ExportData(COLUMNS, rows);
    }

    private static Set<ResourceLocation> findBlockedIngredients(RecipeInput recipe, Map<Item, ItemValuation> values) {
        Set<ResourceLocation> blocked = new HashSet<>();
        for (IngredientInput ingredient : recipe.ingredients()) {
            // A usable alternative satisfies the whole slot; its unknown siblings do not block this recipe.
            boolean satisfied = ingredient.alternatives().stream().filter(option -> option.item() != Items.AIR)
                    .anyMatch(option -> isUsable(values.getOrDefault(option.item(), ItemValuation.UNKNOWN)));
            if (satisfied) continue;
            for (var alternative : ingredient.alternatives()) {
                if (alternative.item() == Items.AIR) continue;
                if (!values.getOrDefault(alternative.item(), ItemValuation.UNKNOWN).isKnown()) {
                    blocked.add(BuiltInRegistries.ITEM.getKey(alternative.item()));
                }
            }
        }
        return blocked;
    }

    private static boolean isUsable(ItemValuation value) {
        return value.isKnown() && Float.isFinite(value.value()) && value.value() > 0.00001f;
    }
}
