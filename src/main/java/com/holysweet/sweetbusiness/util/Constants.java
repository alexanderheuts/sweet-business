package com.holysweet.sweetbusiness.util;

public final class Constants {

    public static final int BASE_MOB_TIER = 1;
    public static final int MAX_MOB_TIER = 5;
    public static final double MOB_HIGH_HEALTH_THRESHOLD = 80.0;
    public static final double MOB_MODERATE_HEALTH_THRESHOLD = 40.0;
    public static final int MOB_XP_SIMULATION_ITERATIONS = 50;
    public static final int MOB_LOOT_SIMULATION_ITERATIONS = 1000;
    public static final int CROP_LOOT_SIMULATION_ITERATIONS = 1000;
    public static final int GRASS_LOOT_SIMULATION_ITERATIONS = 1000;
    public static final int TREE_LOOT_SIMULATION_ITERATIONS = 1000;
    public static final double DEFAULT_CROP_BASELINE = 0.01;
    public static final double DEFAULT_SAPLING_BASELINE = 0.01;
    public static final double DEFAULT_TREE_WOOD_BASELINE = 0.016;
    public static final double DEFAULT_MOB_XP_VALUE_MULTIPLIER = 1.0;

    // Contract details
    public static final int MIN_CONTRACT_TIER = 1;
    public static final int MAX_CONTRACT_TIER = 5;
    public static final int BASE_CONTRACT_LINES = 1;
    public static final double CONTRACT_TARGET_VALUE_BASE = 5.0;
    public static final int CONTRACT_ITEMS_PER_TARGET_VALUE = 64;
    public static final double CONTRACT_MIN_LINE_WEIGHT = 0.1;
    public static final double CONTRACT_MAX_LINE_WEIGHT = 0.9;
    public static final double DEFAULT_CONTRACT_EXTRA_LINE_CHANCE = 15.0;

    // Valuation Engine Defaults
    public static final int ENGINE_MAX_ITERATIONS = 25;

    // Default fallback values for un-calculatable states
    public static final float UNKNOWN_VALUE = -1.0f;
    public static final int UNKNOWN_DEPTH = -1;
    public static final int UNKNOWN_TECH_LEVEL = -1;

    // Base defaults for standard crafting
    public static final int BASE_TECH_LEVEL = 1;

    private Constants() {
    }
}
