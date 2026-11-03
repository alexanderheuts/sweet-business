package com.holysweet.sweetbusiness.evaluation.entity;

import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.discovery.entity.EntityDiscoverer.EntityProperties;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.EnumSet;
import java.util.Set;
import java.util.OptionalDouble;

public final class EntityTierEvaluator {

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
