package com.holysweet.sweetbusiness.valuation.renewable;

import com.holysweet.sweetbusiness.evaluation.renewable.CropEvaluator.CropEvaluation;
import com.holysweet.sweetbusiness.valuation.ItemValuation;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

public final class CropValuator {

    private CropValuator() {}

    public static Map<Item, ItemValuation> valueNaturalCrops(Map<Item, CropEvaluation> crops, float baseline) {
        Map<Item, ItemValuation> values = new HashMap<>();
        crops.forEach((plantingItem, crop) -> {
            if (!crop.hasWorldgenSource()) return;
            ItemValuation value = new ItemValuation(baseline, 0, crop.techTier(), crop.bulkFarmable());
            values.put(plantingItem, value);
            crop.harvestItems().forEach(item -> values.put(item, value));
        });
        return Map.copyOf(values);
    }

    public static Map<Item, ItemValuation> valueHarvests(Map<Item, CropEvaluation> crops,
                                                       Map<Item, ItemValuation> knownValues) {
        Map<Item, ItemValuation> values = new HashMap<>();
        crops.forEach((plantingItem, crop) -> {
            if (crop.hasWorldgenSource()) return;
            ItemValuation seed = knownValues.get(plantingItem);
            if (seed == null || !Float.isFinite(seed.value()) || seed.value() <= 0 || seed.techLevel() < 1) return;

            values.merge(plantingItem, seed.withBulkFarmable(crop.bulkFarmable()), CropValuator::cheapest);
            ItemValuation harvest = new ItemValuation(seed.value(), seed.depth() + 1,
                    seed.techLevel(), crop.bulkFarmable());
            crop.harvestItems().forEach(item -> {
                if (item != plantingItem) values.merge(item, harvest, CropValuator::cheapest);
            });
        });
        return Map.copyOf(values);
    }

    private static ItemValuation cheapest(ItemValuation first, ItemValuation second) {
        if (second.value() < first.value()
                || (second.value() == first.value() && second.techLevel() < first.techLevel())) return second;
        return first;
    }
}
