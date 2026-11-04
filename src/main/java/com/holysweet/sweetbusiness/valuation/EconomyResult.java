package com.holysweet.sweetbusiness.valuation;

import net.minecraft.world.item.Item;
import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public record EconomyResult(Map<Item, ItemValuation> itemValues, Map<Item, AnchorSource> anchorSources,
                            Map<Item, SourceStep> sourceSteps, int iterations, int unresolvedRecipes) {

    public EconomyResult {
        itemValues = Map.copyOf(itemValues);
        anchorSources = Map.copyOf(anchorSources);
        sourceSteps = Map.copyOf(sourceSteps);
    }

    public record AnchorSource(String type, String detail) {}
    public record AnchorEntry(ItemValuation valuation, String source, String sourceDetail) {}

    public record IngredientUse(Item item, @Nullable Item remainder, float cost) {}

    public record SourceStep(String source, String detail, int outputCount,
                             double markupPercentage, List<IngredientUse> ingredients) {
        public SourceStep {
            ingredients = List.copyOf(ingredients);
        }
    }

    public ItemValuation getValuation(Item item) {
        return itemValues.getOrDefault(item, ItemValuation.UNKNOWN);
    }

    public Map<Item, AnchorEntry> getAnchors() {
        Map<Item, AnchorEntry> anchors = new HashMap<>();
        anchorSources.forEach((item, source) -> anchors.put(item,
                new AnchorEntry(itemValues.get(item), source.type(), source.detail())));
        return Map.copyOf(anchors);
    }
}
