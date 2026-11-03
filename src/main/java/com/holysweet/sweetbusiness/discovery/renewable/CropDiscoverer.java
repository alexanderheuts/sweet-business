package com.holysweet.sweetbusiness.discovery.renewable;

import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.discovery.block.GrassDropDiscoverer;
import com.holysweet.sweetbusiness.discovery.simulation.BlockLootSimulator;
import com.holysweet.sweetbusiness.discovery.worldgen.BlockPlacementDiscoverer;
import com.holysweet.sweetbusiness.discovery.worldgen.BlockPlacementDiscoverer.PlacementSource;
import com.holysweet.sweetbusiness.discovery.worldgen.WorldGenDiscoverer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CropDiscoverer {

    private CropDiscoverer() {}

    public record CropProperties(CropBlock crop, BlockState matureState, Map<Item, Double> expectedDrops,
                                 Set<ResourceLocation> seedRecipes, Set<PlacementSource> worldgenSources,
                                 Map<Block, Double> grassSources) {
        public CropProperties {
            expectedDrops = Map.copyOf(expectedDrops);
            seedRecipes = Set.copyOf(seedRecipes);
            worldgenSources = Set.copyOf(worldgenSources);
            grassSources = Map.copyOf(grassSources);
        }

        public boolean dropsPlantingItem(Item plantingItem) {
            return expectedDrops.getOrDefault(plantingItem, 0.0) > 0.0;
        }

        public boolean hasWorldgenSource() {
            return !grassSources.isEmpty() || !worldgenSources.isEmpty();
        }
    }

    public record DiscoveryResult(Map<Item, CropProperties> crops, Map<Item, String> failures,
                                  Map<PlacedFeature, Set<String>> worldgenFailures, Map<Block, String> grassFailures) {
        public DiscoveryResult {
            crops = Map.copyOf(crops);
            failures = Map.copyOf(failures);
            Map<PlacedFeature, Set<String>> snapshot = new HashMap<>();
            worldgenFailures.forEach((feature, reasons) -> snapshot.put(feature, Set.copyOf(reasons)));
            worldgenFailures = Map.copyOf(snapshot);
            grassFailures = Map.copyOf(grassFailures);
        }
    }

    public static DiscoveryResult discoverCrops(ServerLevel level) {
        var worldgen = WorldGenDiscoverer.discoverFeatures(level.getServer());
        var placements = BlockPlacementDiscoverer.discoverBlocks(level.registryAccess(), worldgen);
        return discoverCrops(level, placements);
    }

    public static DiscoveryResult discoverCrops(ServerLevel level, BlockPlacementDiscoverer.DiscoveryResult placements) {
        return discoverCrops(level, placements, GrassDropDiscoverer.discoverDrops(level));
    }

    public static DiscoveryResult discoverCrops(ServerLevel level, BlockPlacementDiscoverer.DiscoveryResult placements,
                                                GrassDropDiscoverer.DiscoveryResult grass) {
        Map<Item, CropProperties> crops = new HashMap<>();
        Map<Item, String> failures = new HashMap<>();
        Map<CropBlock, CropProperties> discoveredBlocks = new HashMap<>();

        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof CropBlock crop)) {
                continue;
            }

            try {
                CropProperties properties = discoveredBlocks.computeIfAbsent(crop,
                        block -> discoverCrop(level, block, placements.sources().getOrDefault(block, Set.of())));
                Map<Block, Double> grassSources = findGrassSources(item, grass);
                properties = new CropProperties(crop, properties.matureState(), properties.expectedDrops(),
                        properties.seedRecipes(), properties.worldgenSources(), grassSources);
                crops.put(item, properties);
                Set<ResourceLocation> seedRecipes = findSeedRecipes(level, item, properties.expectedDrops());
                crops.put(item, new CropProperties(crop, properties.matureState(), properties.expectedDrops(),
                        seedRecipes, properties.worldgenSources(), grassSources));
            } catch (RuntimeException | LinkageError exception) {
                failures.put(item, exception.getClass().getName() + ": " + exception.getMessage());
            }
        }
        return new DiscoveryResult(crops, failures, placements.failures(), grass.failures());
    }

    private static CropProperties discoverCrop(ServerLevel level, CropBlock crop, Set<PlacementSource> worldgenSources) {
        BlockState matureState = crop.getStateForAge(crop.getMaxAge());
        LootTable table = level.getServer().reloadableRegistries().getLootTable(crop.getLootTable());
        Map<Item, Double> expectedDrops = BlockLootSimulator.simulate(
                level, table, matureState, ItemStack.EMPTY, Constants.CROP_LOOT_SIMULATION_ITERATIONS);
        return new CropProperties(crop, matureState, expectedDrops, Set.of(), worldgenSources, Map.of());
    }

    private static Map<Block, Double> findGrassSources(Item plantingItem, GrassDropDiscoverer.DiscoveryResult grass) {
        Map<Block, Double> sources = new HashMap<>();
        grass.expectedDrops().forEach((block, drops) -> {
            double yield = drops.getOrDefault(plantingItem, 0.0);
            if (Double.isFinite(yield) && yield > 0.0) sources.put(block, yield);
        });
        return Map.copyOf(sources);
    }

    private static Set<ResourceLocation> findSeedRecipes(ServerLevel level, Item plantingItem,
                                                        Map<Item, Double> expectedDrops) {
        Set<ResourceLocation> seedRecipes = new HashSet<>();
        for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            Recipe<?> recipe = holder.value();
            if (!recipe.getResultItem(level.registryAccess()).is(plantingItem)) continue;

            if (usesOnlyHarvest(recipe, plantingItem, expectedDrops)) {
                seedRecipes.add(holder.id());
            }
        }
        return Set.copyOf(seedRecipes);
    }

    private static boolean usesOnlyHarvest(Recipe<?> recipe, Item plantingItem, Map<Item, Double> expectedDrops) {
        boolean hasIngredient = false;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            hasIngredient = true;
            boolean matchesHarvest = expectedDrops.entrySet().stream()
                    .anyMatch(drop -> drop.getKey() != plantingItem && drop.getValue() > 0.0
                            && ingredient.test(new ItemStack(drop.getKey())));
            if (!matchesHarvest) return false;
        }
        return hasIngredient;
    }
}
