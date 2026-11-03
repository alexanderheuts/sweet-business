package com.holysweet.sweetbusiness.valuation.renewable;

import com.holysweet.sweetbusiness.evaluation.renewable.TreeEvaluator.ProductType;
import com.holysweet.sweetbusiness.evaluation.renewable.TreeEvaluator.TreeEvaluation;
import com.holysweet.sweetbusiness.valuation.ItemValuation;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

public final class TreeValuator {

    private TreeValuator() {}

    public record TreeValue(ItemValuation valuation, Item plantingItem) {}

    public record Settings(double saplingBaseline, double woodBaseline) {
        public Settings {
            if (!Double.isFinite(saplingBaseline) || saplingBaseline <= 0
                    || !Double.isFinite(woodBaseline) || woodBaseline <= 0) {
                throw new IllegalArgumentException("Tree baselines must be finite and positive");
            }
        }

        public double value(ProductType type) {
            return type == ProductType.WOOD ? woodBaseline : saplingBaseline;
        }
    }

    public static Map<Item, TreeValue> valueNaturalTrees(Map<Item, TreeEvaluation> trees, Settings settings) {
        Map<Item, TreeValue> values = new HashMap<>();
        trees.forEach((plantingItem, tree) -> {
            if (!tree.hasWorldgenSource()) return;
            tree.outputs().forEach((item, type) -> addValue(values, item, plantingItem,
                    settings.value(type), 0, tree.techTier()));
        });
        return Map.copyOf(values);
    }

    public static Map<Item, TreeValue> valueHarvests(Map<Item, TreeEvaluation> trees, Settings settings,
                                                    Map<Item, ItemValuation> knownValues) {
        Map<Item, TreeValue> values = new HashMap<>();
        trees.forEach((plantingItem, tree) -> {
            if (tree.hasWorldgenSource()) return;
            ItemValuation seed = knownValues.get(plantingItem);
            if (seed == null || !Float.isFinite(seed.value()) || seed.value() <= 0
                    || seed.techLevel() < 1 || seed.techLevel() > 5) return;
            tree.outputs().forEach((item, type) -> {
                double value = seed.value() * (settings.value(type) / settings.saplingBaseline());
                addValue(values, item, plantingItem, value, seed.depth() + (item == plantingItem ? 0 : 1), seed.techLevel());
            });
        });
        return Map.copyOf(values);
    }

    private static void addValue(Map<Item, TreeValue> values, Item item, Item plantingItem,
                                  double value, int depth, int tier) {
        float unitValue = (float) value;
        if (!Float.isFinite(unitValue) || unitValue <= 0) return;
        TreeValue candidate = new TreeValue(new ItemValuation(unitValue, depth, tier, true), plantingItem);
        values.merge(item, candidate, (first, second) -> {
            ItemValuation a = first.valuation();
            ItemValuation b = second.valuation();
            return b.value() < a.value() || (b.value() == a.value() && b.techLevel() < a.techLevel()) ? second : first;
        });
    }
}
