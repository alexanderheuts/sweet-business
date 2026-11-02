package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.config.Config;
import com.holysweet.sweetbusiness.util.TerrainClassifier;
import com.holysweet.sweetbusiness.util.TierResolver;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChunkStandardEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double BASE_TYPE_SLICE = 100.0;
    private static final double PLACEMENT_SUCCESS_RATE = 0.4;

    private ChunkStandardEvaluator() {}

    public static Map<Block, Double> calculateAnchorValues(Map<Block, Double> oreVolumes) {
        Map<Block, Double> finalValues = new HashMap<>();
        Map<Block, Double> fancyVolumes = new HashMap<>();

        double boringBlockValue = Config.TERRAIN_BASELINE.get();

        // Structural terrain is not necessarily represented by an ore feature.
        for (Block block : BuiltInRegistries.BLOCK) {
            if (TerrainClassifier.isBoringTerrain(block)) {
                finalValues.put(block, boringBlockValue);
            }
        }

        for (Map.Entry<Block, Double> entry : oreVolumes.entrySet()) {
            if (!finalValues.containsKey(entry.getKey())) {
                fancyVolumes.put(entry.getKey(), entry.getValue());
            }
        }

        // 2. Group ONLY the fancy blocks by Tier and merge their variants
        Map<Integer, Map<String, MergedOre>> tierGroups = groupBlocksByTier(fancyVolumes);

        // 3. Evaluate the fancy blocks
        for (Map.Entry<Integer, Map<String, MergedOre>> tierEntry : tierGroups.entrySet()) {
            evaluateTierBlocks(tierEntry.getKey(), tierEntry.getValue(), finalValues);
        }

        logDebugValues(finalValues);

        return finalValues;
    }

    private static Map<Integer, Map<String, MergedOre>> groupBlocksByTier(Map<Block, Double> fancyVolumes) {
        Map<Integer, Map<String, MergedOre>> tierGroups = new HashMap<>();

        for (Map.Entry<Block, Double> entry : fancyVolumes.entrySet()) {
            Block block = entry.getKey();
            int tier = TierResolver.getBlockTechLevel(block.defaultBlockState());

            // Normalize the name to group variants (e.g., "deepslate_iron_ore" -> "iron_ore")
            String baseName = getBaseOreName(block);

            tierGroups.computeIfAbsent(tier, k -> new HashMap<>())
                    .computeIfAbsent(baseName, k -> new MergedOre())
                    .addVariant(block, entry.getValue());
        }

        return tierGroups;
    }

    private static String getBaseOreName(Block block) {
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return path.replace("deepslate_", "")
                .replace("nether_", "")
                .replace("end_", "")
                .replace("poor_", "")
                .replace("dense_", "");
    }

    private static void evaluateTierBlocks(int rawTier, Map<String, MergedOre> tierBlocks, Map<Block, Double> finalValues) {
        if (tierBlocks.isEmpty()) return;

        int mathTier = Math.max(1, rawTier);
        double typeSlice = BASE_TYPE_SLICE * mathTier;

        for (MergedOre merged : tierBlocks.values()) {
            // Do the math on the COMBINED yield of all variants
            double expectedYield = merged.combinedYield * PLACEMENT_SUCCESS_RATE;
            expectedYield = Math.max(1.0, expectedYield);

            double blockValue = typeSlice / expectedYield;

            // Apply that harmonized value back to every variant (Stone, Deepslate, Nether, etc.)
            for (Block variantBlock : merged.variants) {
                finalValues.put(variantBlock, blockValue);
            }
        }
    }

    private static void logDebugValues(Map<Block, Double> finalValues) {
        LOGGER.debug("=== Sweet Business: Chunk Standard Valuation Results ===");
        finalValues.entrySet().stream()
                .sorted(Map.Entry.<Block, Double>comparingByValue().reversed())
                .forEach(entry -> {
                    String blockName = BuiltInRegistries.BLOCK.getKey(entry.getKey()).toString();
                    LOGGER.debug("Block: {} | Value: {}", blockName, String.format("%.5f", entry.getValue()));
                });
        LOGGER.debug("======================================================");
    }

    /**
     * Helper class to track a unified group of ore variants and their combined yield.
     */
    private static class MergedOre {
        double combinedYield = 0;
        final List<Block> variants = new ArrayList<>();

        void addVariant(Block block, double yield) {
            this.variants.add(block);
            this.combinedYield += yield;
        }
    }
}
