package com.holysweet.sweetbusiness.evaluation.worldgen;

import com.holysweet.sweetbusiness.discovery.worldgen.OreBlockDiscoverer.OreProperties;
import com.holysweet.sweetbusiness.discovery.worldgen.WorldGenDiscoverer.DiscoveryResult;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OreVolumeEvaluator {

    private OreVolumeEvaluator() {}

    public static Map<Block, Double> evaluateVolumes(DiscoveryResult worldgen, Map<PlacedFeature, OreProperties> ores) {
        Map<Block, Double> volumes = new HashMap<>();
        if (worldgen.totalBiomes() == 0) return Map.of();

        ores.forEach((feature, ore) -> {
            int biomeCount = worldgen.featureBiomes().getOrDefault(feature, List.of()).size();
            if (biomeCount == 0) return;

            double biomeWeight = (double) biomeCount / worldgen.totalBiomes();
            double placements = calculateAveragePlacements(feature.placement());
            double expectedYield = placements * ore.veinSize() * biomeWeight;
            for (var state : ore.targetStates()) {
                volumes.merge(state.getBlock(), expectedYield, Double::sum);
            }
        });
        return Map.copyOf(volumes);
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