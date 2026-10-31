package com.holysweet.sweetbusiness.util;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashMap;
import java.util.Map;

public class LootSimulator {

    private static final LootSimulator INSTANCE = new LootSimulator();

    private LootSimulator() {}

    public static LootSimulator getInstance() {
        return INSTANCE;
    }

    /**
     * Rolls a LootTable N times and returns the average yield per item.
     */
    public Map<Item, Double> simulate(LootTable table, LootParams params, int iterations) {
        Map<Item, Integer> totalDrops = new HashMap<>();

        for (int i = 0; i < iterations; i++) {
            // getRandomItems actually executes the roll based on the provided params
            for (ItemStack drop : table.getRandomItems(params)) {
                if (!drop.isEmpty()) {
                    totalDrops.merge(drop.getItem(), drop.getCount(), Integer::sum);
                }
            }
        }

        Map<Item, Double> averageDrops = new HashMap<>();
        for (Map.Entry<Item, Integer> entry : totalDrops.entrySet()) {
            averageDrops.put(entry.getKey(), (double) entry.getValue() / iterations);
        }

        return averageDrops;
    }
}