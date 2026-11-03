package com.holysweet.sweetbusiness.evaluation.entity;

import com.holysweet.sweetbusiness.discovery.entity.EntityDiscoverer.DiscoveryResult;
import com.holysweet.sweetbusiness.evaluation.entity.EntityTierEvaluator.EntityTier;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public final class EntityEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EntityEvaluator() {}

    public static Map<EntityType<?>, EntityTier> evaluateEntities(DiscoveryResult discovery, boolean spawnEggsEnableFarming) {
        Map<EntityType<?>, EntityTier> tiers = new HashMap<>();
        discovery.properties().forEach((type, data) -> tiers.put(type, EntityTierEvaluator.evaluate(data, spawnEggsEnableFarming)));
        var xp = EntityXpEvaluator.resolveXp(tiers);
        tiers.putAll(xp.tiers());
        xp.failures().forEach((type, reason) -> LOGGER.error("Cannot resolve loot valuation XP for {} in tier {}: {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(type), tiers.get(type).tier(), reason));
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
                .forEach(entry -> LOGGER.debug("Entity: {} | Tier: {} | Median XP: {} | Has loot: {} | Effective XP: {} | Farmable: {} | Promotions: {}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey()),
                        entry.getValue().tier(), entry.getValue().medianXp(), entry.getValue().hasLootTable(),
                        entry.getValue().effectiveXp(), entry.getValue().farmable(), entry.getValue().promotions()));
    }
}
