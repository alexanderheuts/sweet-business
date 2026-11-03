package com.holysweet.sweetbusiness.discovery.renewable;

import com.holysweet.sweetbusiness.discovery.worldgen.TreeFeatureDiscoverer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TreeDiscoverer {

    private TreeDiscoverer() {}

    public record TreeVariant(ResourceLocation featureId, int plantingItemCount) {}

    public record PlantingProperties(Block plantingBlock, Set<TreeVariant> variants) {
        public PlantingProperties {
            variants = Set.copyOf(variants);
        }
    }

    public record DiscoveryResult(Map<Item, PlantingProperties> plantingItems,
                                  TreeFeatureDiscoverer.DiscoveryResult features,
                                  TreeHarvestDiscoverer.DiscoveryResult harvests,
                                  Map<Item, String> failures) {
        public DiscoveryResult {
            plantingItems = Map.copyOf(plantingItems);
            failures = Map.copyOf(failures);
        }
    }

    public static DiscoveryResult discoverTrees(ServerLevel level) {
        Map<Item, PlantingProperties> plantingItems = new HashMap<>();
        Map<Item, String> failures = new HashMap<>();
        Map<Block, PlantingProperties> discoveredBlocks = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem blockItem)) continue;
            Block block = blockItem.getBlock();
            if (!(block instanceof SaplingBlock) && block != Blocks.AZALEA && block != Blocks.FLOWERING_AZALEA) continue;
            try {
                plantingItems.put(item, discoveredBlocks.computeIfAbsent(block, TreeDiscoverer::discoverPlanting));
            } catch (RuntimeException | LinkageError exception) {
                failures.put(item, exception.getClass().getName() + ": " + exception.getMessage());
            }
        }

        Set<ResourceLocation> featureIds = new HashSet<>();
        plantingItems.values().forEach(planting -> planting.variants().forEach(variant -> featureIds.add(variant.featureId())));
        var features = TreeFeatureDiscoverer.discoverFeatures(level.registryAccess(), featureIds);
        // Shared features and block states are inspected once per discovery pass.
        Set<BlockState> states = new HashSet<>();
        features.trees().values().forEach(tree -> states.addAll(tree.allStates()));
        return new DiscoveryResult(plantingItems, features, TreeHarvestDiscoverer.discoverDrops(level, states), failures);
    }

    private static PlantingProperties discoverPlanting(Block block) {
        TreeGrower grower = block instanceof SaplingBlock sapling ? sapling.treeGrower : TreeGrower.AZALEA;
        Set<TreeVariant> variants = new HashSet<>();
        float secondaryChance = grower.secondaryChance;
        if (secondaryChance < 1 || grower.secondaryTree.isEmpty()) {
            grower.tree.ifPresent(feature -> variants.add(new TreeVariant(feature.location(), 1)));
        }
        if (secondaryChance < 1 || (grower.secondaryFlowers.isEmpty() && grower.secondaryTree.isEmpty())) {
            grower.flowers.ifPresent(feature -> variants.add(new TreeVariant(feature.location(), 1)));
        }
        if (secondaryChance < 1 || grower.secondaryMegaTree.isEmpty()) {
            grower.megaTree.ifPresent(feature -> variants.add(new TreeVariant(feature.location(), 4)));
        }
        if (secondaryChance > 0) {
            grower.secondaryTree.ifPresent(feature -> variants.add(new TreeVariant(feature.location(), 1)));
            grower.secondaryFlowers.ifPresent(feature -> variants.add(new TreeVariant(feature.location(), 1)));
            grower.secondaryMegaTree.ifPresent(feature -> variants.add(new TreeVariant(feature.location(), 4)));
        }
        if (variants.isEmpty()) throw new IllegalStateException("No declared tree features found for planting block");
        return new PlantingProperties(block, variants);
    }
}
