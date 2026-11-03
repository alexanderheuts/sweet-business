package com.holysweet.sweetbusiness.evaluation.renewable;

import com.holysweet.sweetbusiness.discovery.renewable.CropDiscoverer.DiscoveryResult;
import com.holysweet.sweetbusiness.util.Constants;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CropEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CropEvaluator() {}

    public record CropEvaluation(Set<Item> harvestItems, boolean hasWorldgenSource, int techTier,
                                 boolean bulkFarmable, Set<ResourceLocation> seedRecipes) {
        public CropEvaluation {
            harvestItems = Set.copyOf(harvestItems);
            seedRecipes = Set.copyOf(seedRecipes);
        }
    }

    public static Map<Item, CropEvaluation> evaluateCrops(DiscoveryResult discovery) {
        reportFailures(discovery);
        Map<Item, CropEvaluation> crops = new HashMap<>();
        discovery.crops().forEach((plantingItem, properties) -> {
            Set<Item> harvest = new HashSet<>();
            properties.expectedDrops().forEach((item, yield) -> {
                if (item != Items.AIR && Double.isFinite(yield) && yield > 0) harvest.add(item);
            });
            boolean natural = properties.hasWorldgenSource();
            crops.put(plantingItem, new CropEvaluation(harvest, natural,
                    natural ? Constants.BASE_TECH_LEVEL : Constants.UNKNOWN_TECH_LEVEL, true, properties.seedRecipes()));
        });
        LOGGER.info("Evaluated crops: {}", crops.size());
        return Map.copyOf(crops);
    }

    private static void reportFailures(DiscoveryResult discovery) {
        discovery.failures().forEach((item, reason) -> LOGGER.warn("Could not fully discover crop {}: {}",
                BuiltInRegistries.ITEM.getKey(item), reason));
        discovery.grassFailures().forEach((block, reason) -> LOGGER.warn("Could not discover grass drops for {}: {}",
                BuiltInRegistries.BLOCK.getKey(block), reason));
        if (!discovery.worldgenFailures().isEmpty()) {
            LOGGER.warn("Crop worldgen source discovery incomplete for {} placed features; see debug output for details",
                    discovery.worldgenFailures().size());
            discovery.worldgenFailures().forEach((feature, reasons) -> reasons.forEach(reason -> LOGGER.debug(
                    "Incomplete worldgen source discovery for {}: {}",
                    feature.feature().unwrapKey().map(key -> key.location().toString()).orElse("inline"), reason)));
        }
        if (!discovery.structureFailures().isEmpty()) {
            LOGGER.warn("Crop structure source discovery incomplete for {} structures; see debug output for details",
                    discovery.structureFailures().size());
            discovery.structureFailures().forEach((id, reasons) -> reasons.forEach(reason ->
                    LOGGER.debug("Incomplete structure source discovery for {}: {}", id, reason)));
        }
    }
}
