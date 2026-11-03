package com.holysweet.sweetbusiness.evaluation.entity;

import com.holysweet.sweetbusiness.evaluation.entity.EntityTierEvaluator.EntityTier;
import com.holysweet.sweetbusiness.util.Statistics;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

public final class EntityXpEvaluator {

    private EntityXpEvaluator() {}

    public record EvaluationResult(Map<EntityType<?>, EntityTier> tiers, Map<EntityType<?>, String> failures) {
        public EvaluationResult {
            tiers = Map.copyOf(tiers);
            failures = Map.copyOf(failures);
        }
    }

    public static EvaluationResult resolveXp(Map<EntityType<?>, EntityTier> tiers) {
        Map<Integer, Double> tierMedians = calculateTierMedians(tiers);
        Map<EntityType<?>, EntityTier> resolved = new HashMap<>();
        Map<EntityType<?>, String> failures = new HashMap<>();
        tiers.forEach((type, tier) -> resolved.put(type, resolveXp(type, tier, tierMedians, failures)));
        return new EvaluationResult(resolved, failures);
    }

    private static Map<Integer, Double> calculateTierMedians(Map<EntityType<?>, EntityTier> tiers) {
        Map<Integer, List<Double>> samples = new HashMap<>();
        for (EntityTier tier : tiers.values()) {
            if (tier.hasLootTable() && tier.medianXp() > 0) {
                samples.computeIfAbsent(tier.tier(), key -> new ArrayList<>()).add(tier.medianXp());
            }
        }
        Map<Integer, Double> medians = new HashMap<>();
        samples.forEach((tier, values) -> medians.put(tier,
                Statistics.median(values.stream().mapToDouble(Double::doubleValue).toArray())));
        return medians;
    }

    private static EntityTier resolveXp(EntityType<?> type, EntityTier tier, Map<Integer, Double> tierMedians,
                                        Map<EntityType<?>, String> failures) {
        if (!tier.hasLootTable() || tier.effectiveXp().isPresent()) return tier;

        Double fallback = tierMedians.get(tier.tier());
        if (fallback == null) {
            failures.put(type, "no loot-bearing mobs with positive XP in this tier");
            return tier;
        }
        return new EntityTier(tier.tier(), tier.promotions(), tier.medianXp(), true,
                tier.lootTableId(), tier.lootTable(), OptionalDouble.of(fallback), tier.farmable());
    }
}
