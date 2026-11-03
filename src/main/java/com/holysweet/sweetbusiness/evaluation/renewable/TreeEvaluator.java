package com.holysweet.sweetbusiness.evaluation.renewable;

import com.holysweet.sweetbusiness.discovery.renewable.TreeDiscoverer.DiscoveryResult;
import com.holysweet.sweetbusiness.discovery.renewable.TreeHarvestDiscoverer.HarvestMode;
import com.holysweet.sweetbusiness.discovery.worldgen.TreeFeatureDiscoverer.Part;
import com.holysweet.sweetbusiness.util.Constants;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TreeEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private TreeEvaluator() {}

    public enum ProductType { SAPLING_OR_FRUIT, WOOD }

    public record TreeEvaluation(Map<Item, ProductType> outputs, boolean hasWorldgenSource, int techTier) {
        public TreeEvaluation {
            outputs = Map.copyOf(outputs);
        }
    }

    public static Map<Item, TreeEvaluation> evaluateTrees(DiscoveryResult discovery, Set<Block> naturalBlocks,
                                                          Set<ResourceLocation> naturalFeatures, Set<Item> craftableItems) {
        reportFailures(discovery);
        Map<Item, TreeEvaluation> trees = new HashMap<>();
        discovery.plantingItems().forEach((plantingItem, planting) -> {
            boolean natural = planting.variants().stream().anyMatch(variant ->
                    naturalFeatures.contains(variant.featureId())
                            || hasNaturalPlantingDrop(discovery, variant.featureId(), plantingItem, naturalBlocks));
            Map<Item, ProductType> outputs = new HashMap<>();
            outputs.put(plantingItem, ProductType.SAPLING_OR_FRUIT);
            planting.variants().forEach(variant -> {
                var contents = discovery.features().trees().get(variant.featureId());
                if (contents == null) return;
                contents.parts().forEach((part, states) -> {
                    if (part == Part.GROUND) return;
                    for (BlockState state : states) {
                        if (state.is(BlockTags.BEEHIVES)) continue;
                        var harvest = discovery.harvests().harvests().get(state);
                        if (harvest == null) continue;
                        harvest.expectedDrops().values().forEach(drops -> drops.forEach((item, yield) -> {
                            if (item == Items.AIR || !Double.isFinite(yield) || yield <= 0) return;
                            if (craftableItems.contains(item) && !(natural && item == plantingItem)) return;
                            boolean woody = part == Part.TRUNK || part == Part.ROOT
                                    || (part == Part.FOLIAGE && item == state.getBlock().asItem());
                            ProductType type = woody && item != plantingItem ? ProductType.WOOD : ProductType.SAPLING_OR_FRUIT;
                            outputs.merge(item, type, (first, second) ->
                                    first == ProductType.SAPLING_OR_FRUIT ? first : second);
                        }));
                    }
                });
            });
            trees.put(plantingItem, new TreeEvaluation(outputs, natural,
                    natural ? Constants.BASE_TECH_LEVEL : Constants.UNKNOWN_TECH_LEVEL));
        });
        LOGGER.info("Evaluated trees: {}", trees.size());
        return Map.copyOf(trees);
    }

    private static boolean hasNaturalPlantingDrop(DiscoveryResult discovery, ResourceLocation featureId,
                                                   Item plantingItem, Set<Block> naturalBlocks) {
        var contents = discovery.features().trees().get(featureId);
        if (contents == null) return false;
        for (var part : contents.parts().entrySet()) {
            if (part.getKey() == Part.GROUND) continue;
            for (BlockState state : part.getValue()) {
                if (!naturalBlocks.contains(state.getBlock())) continue;
                var harvest = discovery.harvests().harvests().get(state);
                if (harvest != null && harvest.expectedDrops().getOrDefault(HarvestMode.NORMAL, Map.of())
                        .getOrDefault(plantingItem, 0.0) > 0) return true;
            }
        }
        return false;
    }

    private static void reportFailures(DiscoveryResult discovery) {
        discovery.failures().forEach((item, reason) -> LOGGER.warn("Could not discover tree planting item {}: {}",
                BuiltInRegistries.ITEM.getKey(item), reason));
        discovery.features().failures().forEach((id, reasons) -> reasons.forEach(reason ->
                LOGGER.warn("Incomplete tree feature discovery for {}: {}", id, reason)));
        discovery.harvests().failures().forEach((state, reasons) -> reasons.forEach((mode, reason) ->
                LOGGER.warn("Could not discover tree harvest {} ({}) : {}", state, mode, reason)));
    }
}
