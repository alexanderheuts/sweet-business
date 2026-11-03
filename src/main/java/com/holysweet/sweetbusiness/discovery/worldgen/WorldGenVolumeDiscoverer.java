package com.holysweet.sweetbusiness.discovery.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.core.Registry;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldGenVolumeDiscoverer {

    private WorldGenVolumeDiscoverer() {}

    /**
     * Scans worldgen registries to calculate the average expected blocks per chunk,
     * factoring in Biome scarcity and rarity modifiers.
     */
    public static Map<Block, Double> discoverOreVolumes(MinecraftServer server) {
        Map<Block, Double> expectedVolumes = new HashMap<>();

        var biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        var placedFeatureRegistry = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);

        int totalBiomes = biomeRegistry.size();
        if (totalBiomes == 0) return expectedVolumes;

        // 1. Pre-calculate how widely distributed each feature is
        Map<PlacedFeature, Integer> featureBiomeCounts = countFeaturesInBiomes(biomeRegistry);

        // 2. Evaluate the physical yield of each feature
        for (PlacedFeature placedFeature : placedFeatureRegistry) {
            int biomeCount = featureBiomeCounts.getOrDefault(placedFeature, 0);
            if (biomeCount == 0) continue;

            double biomeWeight = (double) biomeCount / totalBiomes;
            double averagePlacements = calculateAveragePlacements(placedFeature.placement());

            mergeBlockVolumes(placedFeature, averagePlacements, biomeWeight, expectedVolumes);
        }

        return expectedVolumes;
    }

    private static Map<PlacedFeature, Integer> countFeaturesInBiomes(Registry<Biome> biomeRegistry) {
        Map<PlacedFeature, Integer> counts = new HashMap<>();
        for (Biome biome : biomeRegistry) {
            for (var stepList : biome.getGenerationSettings().features()) {
                for (Holder<PlacedFeature> featureHolder : stepList) {
                    counts.merge(featureHolder.value(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private static double calculateAveragePlacements(List<PlacementModifier> modifiers) {
        double averagePlacements = 1.0;

        for (PlacementModifier modifier : modifiers) {
            if (modifier instanceof CountPlacement countPlacement) {
                IntProvider provider = extractCountProvider(countPlacement);
                if (provider != null) {
                    averagePlacements *= (provider.getMinValue() + provider.getMaxValue()) / 2.0;
                }
            } else if (modifier instanceof RarityFilter rarityFilter) {
                int chance = extractRarityChance(rarityFilter);
                averagePlacements *= (1.0 / chance);
            }
        }

        return averagePlacements;
    }

    private static void mergeBlockVolumes(PlacedFeature placedFeature, double averagePlacements, double biomeWeight, Map<Block, Double> expectedVolumes) {
        ConfiguredFeature<?, ?> configuredFeature = placedFeature.feature().value();

        if (configuredFeature.config() instanceof OreConfiguration oreConfig) {
            double expectedYield = averagePlacements * oreConfig.size * biomeWeight;

            for (OreConfiguration.TargetBlockState target : oreConfig.targetStates) {
                Block targetBlock = target.state.getBlock();
                expectedVolumes.merge(targetBlock, expectedYield, Double::sum);
            }
        }
    }

    private static IntProvider extractCountProvider(CountPlacement placement) {
        try {
            Field field = CountPlacement.class.getDeclaredField("count");
            field.setAccessible(true);
            return (IntProvider) field.get(placement);
        } catch (Exception e) {
            return null;
        }
    }

    private static int extractRarityChance(RarityFilter filter) {
        try {
            Field field = RarityFilter.class.getDeclaredField("chance");
            field.setAccessible(true);
            return (int) field.get(filter);
        } catch (Exception e) {
            return 1;
        }
    }
}