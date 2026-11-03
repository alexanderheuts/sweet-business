package com.holysweet.sweetbusiness.valuation.entity;

import com.holysweet.sweetbusiness.valuation.ItemValuation;

import com.holysweet.sweetbusiness.evaluation.entity.EntityTierEvaluator.EntityTier;
import com.holysweet.sweetbusiness.discovery.entity.EntityLootDiscoverer.DiscoveryResult;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public final class EntityDropEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private EntityDropEvaluator() {}

    public static Map<Item, ItemValuation> evaluateDrops(Map<EntityType<?>, EntityTier> tiers,
            DiscoveryResult discovery, double xpValueMultiplier) {
        Map<Item, ItemValuation> anchors = new HashMap<>();
        discovery.failures().forEach((type, reason) -> LOGGER.error("Could not simulate loot for {}: {}",
                BuiltInRegistries.ENTITY_TYPE.getKey(type), reason));

        discovery.expectedDrops().forEach((type, drops) -> {
            EntityTier tier = tiers.get(type);
            if (tier == null || tier.effectiveXp().isEmpty()) return;

            double budget = tier.effectiveXp().getAsDouble() * tier.tier() * xpValueMultiplier;
            if (!Double.isFinite(budget) || budget <= 0) {
                LOGGER.error("Invalid mob loot value budget {} for {}", budget, BuiltInRegistries.ENTITY_TYPE.getKey(type));
                return;
            }
            Map<Item, Double> values = calculateDropValues(budget, drops);
            if (values.isEmpty()) {
                LOGGER.warn("Defined loot table {} for {} produced no item drops in the baseline simulation",
                        tier.lootTableId().location(), BuiltInRegistries.ENTITY_TYPE.getKey(type));
                return;
            }
            values.forEach((item, value) -> {
                float unitValue = value.floatValue();
                if (!Float.isFinite(unitValue) || unitValue <= 0) {
                    LOGGER.error("Invalid loot value {} for {} from {}", value,
                            BuiltInRegistries.ITEM.getKey(item), BuiltInRegistries.ENTITY_TYPE.getKey(type));
                    return;
                }
                ItemValuation candidate = new ItemValuation(unitValue, 0, tier.tier(), tier.farmable());
                anchors.merge(item, candidate, EntityDropEvaluator::cheapestSource);
                LOGGER.debug("Mob: {} | Item: {} | Expected yield: {} | Budget: {} | Unit value: {}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(type), BuiltInRegistries.ITEM.getKey(item),
                        drops.get(item), budget, value);
            });
        });
        LOGGER.info("Mapped mob-drop anchors: {}", anchors.size());
        return Map.copyOf(anchors);
    }

    public static Map<Item, Double> calculateDropValues(double mobValue, Map<Item, Double> expectedDrops) {
        Map<Item, Double> drops = positiveDrops(expectedDrops);
        if (drops.isEmpty()) return Map.of();

        double valuePerDropType = mobValue / drops.size();
        Map<Item, Double> values = new HashMap<>();
        drops.forEach((item, yield) -> values.put(item, valuePerDropType / yield));
        return Map.copyOf(values);
    }

    private static Map<Item, Double> positiveDrops(Map<Item, Double> drops) {
        Map<Item, Double> valid = new HashMap<>();
        drops.forEach((item, yield) -> {
            if (item != Items.AIR && Double.isFinite(yield) && yield > 0) {
                valid.put(item, yield);
            }
        });
        return valid;
    }

    private static ItemValuation cheapestSource(ItemValuation first, ItemValuation second) {
        ItemValuation cheapest = first;
        if (second.value() < first.value()
                || (second.value() == first.value() && second.techLevel() < first.techLevel())) {
            cheapest = second;
        }
        return cheapest.withBulkFarmable(first.isBulkFarmable() || second.isBulkFarmable());
    }
}
