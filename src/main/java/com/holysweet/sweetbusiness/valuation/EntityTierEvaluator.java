package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.valuation.discovery.EntityDiscoverer.EntityProperties;
import com.holysweet.sweetbusiness.valuation.discovery.EntityDiscoverer.DiscoveryResult;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    public record EntityTier(int tier, Set<Promotion> promotions, double medianXp) {
        public EntityTier {
            promotions = Set.copyOf(promotions);
        }
    }

    public static Map<EntityType<?>, EntityTier> calculateTiers(DiscoveryResult discovery) {
        Map<EntityType<?>, EntityTier> tiers = new HashMap<>();
        discovery.properties().forEach((type, data) -> tiers.put(type, evaluate(data)));
        discovery.failures().forEach((type, reason) -> LOGGER.warn("Could not determine mob tier for {}: {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(type), reason));
        LOGGER.info("Mapped mob tiers: {} | Unidentified entity types: {}", tiers.size(), discovery.failures().size());
        logDebugTiers(tiers);
        return Map.copyOf(tiers);
    }

    private static void logDebugTiers(Map<EntityType<?>, EntityTier> tiers) {
        if (!LOGGER.isDebugEnabled()) return;

        tiers.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey()).toString()))
                .forEach(entry -> LOGGER.debug("Entity: {} | Tier: {} | Median XP: {} | Promotions: {}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey()),
                        entry.getValue().tier(), entry.getValue().medianXp(), entry.getValue().promotions()));
    }

    public static EntityTier evaluate(EntityProperties properties) {
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
        return new EntityTier(Math.min(tier, Constants.MAX_MOB_TIER), promotions, properties.medianXp());
    }
}
