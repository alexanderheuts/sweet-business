package com.holysweet.sweetbusiness.contract.generator;

import com.holysweet.sweetbusiness.contract.DeliveryLine;
import com.holysweet.sweetbusiness.contract.ContractRules;
import com.holysweet.sweetbusiness.valuation.ItemValuation;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

public final class DeliveryGenerator {

    private DeliveryGenerator() {}

    public static List<DeliveryLine> generate(ContractPlan plan,
            Map<ResourceLocation, ItemValuation> craftableItems, long seed) {
        RandomGenerator random = new Random(seed);
        var remainingItems = new ArrayList<>(Map.copyOf(craftableItems).entrySet().stream()
                .filter(entry -> isEligible(entry.getValue(), plan.tier()))
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .toList());
        List<DeliveryLine> lines = new ArrayList<>();

        for (double budget : plan.lineBudgets()) {
            var candidates = remainingItems.stream()
                    .filter(entry -> entry.getValue().value() <= budget)
                    .toList();
            if (candidates.isEmpty()) continue;

            var selected = candidates.get(random.nextInt(candidates.size()));
            double unitValue = selected.getValue().value();
            int quantity = (int) Math.min(plan.maxItemsPerLine(), Math.floor(budget / unitValue));
            lines.add(new DeliveryLine(selected.getKey(), quantity, unitValue));
            remainingItems.remove(selected);
        }
        return List.copyOf(lines);
    }

    private static boolean isEligible(ItemValuation valuation, int contractTier) {
        return valuation.isKnown() && Float.isFinite(valuation.value()) && valuation.value() > 0
                && ContractRules.isValidTier(valuation.techLevel()) && valuation.techLevel() <= contractTier;
    }
}
