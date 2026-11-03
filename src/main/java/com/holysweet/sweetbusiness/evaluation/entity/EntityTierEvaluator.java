package com.holysweet.sweetbusiness.evaluation.entity;

import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.util.Statistics;
import com.holysweet.sweetbusiness.discovery.entity.EntityDiscoverer.EntityProperties;
import com.holysweet.sweetbusiness.discovery.entity.EntityDiscoverer.DiscoveryResult;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootTable;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.OptionalDouble;

public final class EntityTierEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EntityTierEvaluator() {}

    public enum Promotion {
        HOSTILE,
        NO_OVERWORLD_SPAWNS,
        FLIGHT_OR_SWIMMING,
        HIGH_HEALTH,
        BOSS,
        MODERATE_HEALTH
    }

    public record EntityTier(int tier, Set<Promotion> promotions, double medianXp,
                             boolean hasLootTable, ResourceKey<LootTable> lootTableId, LootTable lootTable,
                             OptionalDouble effectiveXp, boolean farmable) {
        public EntityTier {
            promotions = Set.copyOf(promotions);
        }
    }

    public static Map<EntityType<?>, EntityTier> calculateTiers(DiscoveryResult discovery, boolean spawnEggsEnableFarming) {
        Map<EntityType<?>, EntityTier> tiers = new HashMap<>();
        discovery.properties().forEach((type, data) -> tiers.put(type, evaluate(data, spawnEggsEnableFarming)));
        Map<Integer, Double> tierMedians = calculateTierMedians(tiers);
        tiers.replaceAll((type, tier) -> resolveXp(type, tier, tierMedians));
        discovery.failures().forEach((type, reason) -> LOGGER.warn("Could not determine mob tier for {}: {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(type), reason));
        LOGGER.info("Mapped mob tiers: {} | Unidentified entity types: {}", tiers.size(), discovery.failures().size());
        logDebugTiers(tiers);
        return Map.copyOf(tiers);
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

    private static EntityTier resolveXp(EntityType<?> type, EntityTier tier, Map<Integer, Double> tierMedians) {
        if (!tier.hasLootTable() || tier.effectiveXp().isPresent()) return tier;

        Double fallback = tierMedians.get(tier.tier());
        if (fallback == null) {
            LOGGER.error("Cannot resolve loot valuation XP for {} in tier {}: no loot-bearing mobs with positive XP in this tier",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), tier.tier());
            return tier;
        }
        return new EntityTier(tier.tier(), tier.promotions(), tier.medianXp(), true,
                tier.lootTableId(), tier.lootTable(), OptionalDouble.of(fallback), tier.farmable());
    }

    private static void logDebugTiers(Map<EntityType<?>, EntityTier> tiers) {
        if (!LOGGER.isDebugEnabled()) return;

        tiers.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey()).toString()))
                .forEach(entry -> LOGGER.debug("Entity: {} | Tier: {} | Median XP: {} | Has loot: {} | Effective XP: {} | Farmable: {} | Promotions: {}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey()),
                        entry.getValue().tier(), entry.getValue().medianXp(), entry.getValue().hasLootTable(),
                        entry.getValue().effectiveXp(), entry.getValue().farmable(), entry.getValue().promotions()));
    }

    public static EntityTier evaluate(EntityProperties properties, boolean spawnEggsEnableFarming) {
        Set<Promotion> promotions = EnumSet.noneOf(Promotion.class);
        if (properties.hostile()) {
            promotions.add(Promotion.HOSTILE);
            if (!properties.spawnsInOverworld()) {
                promotions.add(Promotion.NO_OVERWORLD_SPAWNS);
            }
            if (properties.flying() || properties.swimming()) {
                promotions.add(Promotion.FLIGHT_OR_SWIMMING);
            }
            if (properties.maxHealth() >= Constants.MOB_HIGH_HEALTH_THRESHOLD) {
                promotions.add(Promotion.HIGH_HEALTH);
            }
        }
        if (properties.boss()) {
            promotions.add(Promotion.BOSS);
        }

        int tier = Constants.BASE_MOB_TIER + promotions.size();
        if (tier == Constants.BASE_MOB_TIER + 1 && properties.hostile()
                && properties.maxHealth() >= Constants.MOB_MODERATE_HEALTH_THRESHOLD) {
            promotions.add(Promotion.MODERATE_HEALTH);
            tier++;
        }
        OptionalDouble effectiveXp = properties.hasLootTable() && properties.medianXp() > 0
                ? OptionalDouble.of(properties.medianXp()) : OptionalDouble.empty();
        return new EntityTier(Math.min(tier, Constants.MAX_MOB_TIER), promotions, properties.medianXp(),
                properties.hasLootTable(), properties.lootTableId(), properties.lootTable(), effectiveXp,
                spawnEggsEnableFarming && properties.hasSpawnEgg());
    }
}
