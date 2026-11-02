package com.holysweet.sweetbusiness.contract.generator;

import com.holysweet.sweetbusiness.contract.Contract;
import com.holysweet.sweetbusiness.contract.ContractRules;
import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.valuation.ItemValuation;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class ContractGenerator {

    private ContractGenerator() {}

    public static ContractPlan plan(int tier, double extraLineChance, long seed) {
        ContractRules.validateTier(tier);
        if (!Double.isFinite(extraLineChance) || extraLineChance < 0 || extraLineChance > 100) {
            throw new IllegalArgumentException("Extra line chance must be between 0 and 100 percent");
        }

        RandomGenerator random = new Random(seed);
        int lineCount = Constants.BASE_CONTRACT_LINES;
        for (int roll = 0; roll < tier; roll++) {
            if (random.nextDouble() < extraLineChance / 100.0) {
                lineCount++;
            }
        }

        double targetValue = Math.pow(Constants.CONTRACT_TARGET_VALUE_BASE, tier);
        int maxItems = (int) Math.min(Integer.MAX_VALUE,
                Math.floor(targetValue * Constants.CONTRACT_ITEMS_PER_TARGET_VALUE));
        return new ContractPlan(tier, targetValue, distributeValue(targetValue, lineCount, random), maxItems);
    }

    // The supplied item pool contains only items in the owner's CraftableSet.
    public static Optional<Contract> generate(UUID contractId, int tier, Map<ResourceLocation, ItemValuation> craftableItems,
            double extraLineChance, long seed) {
        Objects.requireNonNull(contractId);
        RandomGenerator random = new Random(seed);
        ContractPlan plan = plan(tier, extraLineChance, random.nextLong());
        var lines = DeliveryGenerator.generate(plan, craftableItems, random.nextLong());
        if (lines.isEmpty()) return Optional.empty();

        return Optional.of(new Contract(contractId, tier, lines));
    }

    private static List<Double> distributeValue(double targetValue, int lineCount, RandomGenerator random) {
        List<Double> weights = new ArrayList<>();
        double totalWeight = 0.0;
        for (int line = 0; line < lineCount; line++) {
            double weight = random.nextDouble(Constants.CONTRACT_MIN_LINE_WEIGHT, Constants.CONTRACT_MAX_LINE_WEIGHT);
            weights.add(weight);
            totalWeight += weight;
        }

        List<Double> budgets = new ArrayList<>();
        double remainingValue = targetValue;
        for (int line = 0; line < lineCount; line++) {
            double budget = line == lineCount - 1 ? remainingValue : targetValue * weights.get(line) / totalWeight;
            budgets.add(budget);
            remainingValue -= budget;
        }
        return budgets;
    }
}
