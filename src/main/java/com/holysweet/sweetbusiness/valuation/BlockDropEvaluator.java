package com.holysweet.sweetbusiness.valuation;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import org.slf4j.Logger;
import java.util.HashMap;
import java.util.Map;

public final class BlockDropEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private BlockDropEvaluator() {}

    /**
     * Converts physical block values into individual item drop values
     * using Inverse Yield Proportionality.
     *
     * @param blockAnchorValue The macroeconomic value of the physical block.
     * @param expectedDrops The raw yields discovered from the Monte Carlo simulation.
     * @return A map of the dropped items and their calculated economic values.
     */
    public static Map<Item, Double> evaluateDropValues(double blockAnchorValue, Map<Item, Double> expectedDrops) {
        Map<Item, Double> evaluatedItems = new HashMap<>();

        for (Map.Entry<Item, Double> entry : expectedDrops.entrySet()) {
            Item droppedItem = entry.getKey();
            double averageYield = entry.getValue();

            // Ignore impossible drops or empty air
            if (averageYield <= 0 || droppedItem == Items.AIR) {
                continue;
            }

            // The Core Math: Block Value / Average Drop Yield
            double dropValue = blockAnchorValue / averageYield;

            evaluatedItems.put(droppedItem, dropValue);
        }

        logDebugValues(evaluatedItems);

        return evaluatedItems;
    }

    private static void logDebugValues(Map<Item, Double> finalValues) {
        if (!LOGGER.isDebugEnabled()) return;

        finalValues.entrySet().stream()
                .sorted(Map.Entry.<Item, Double>comparingByValue().reversed())
                .forEach(entry -> {
                    String itemName = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
                    LOGGER.debug("Item: {} | Value: {}", itemName, String.format("%.5f", entry.getValue()));
                });
    }
}
