package com.holysweet.sweetbusiness.valuation;

import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

public record EconomyResult(Map<Item, ItemValuation> itemValues, Map<Item, AnchorSource> anchorSources,
                            int iterations, int unresolvedRecipes) {

    public EconomyResult {
        itemValues = Map.copyOf(itemValues);
        anchorSources = Map.copyOf(anchorSources);
    }

    public record AnchorSource(String type, String detail) {}
    public record AnchorEntry(ItemValuation valuation, String source, String sourceDetail) {}

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
