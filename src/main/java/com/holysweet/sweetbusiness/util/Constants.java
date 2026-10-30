package com.holysweet.sweetbusiness.util;

public final class Constants {

    // Valuation Engine Defaults
    public static final int ENGINE_MAX_ITERATIONS = 10;

    // Default fallback values for un-calculatable states
    public static final float UNKNOWN_VALUE = -1.0f;
    public static final int UNKNOWN_DEPTH = -1;
    public static final int UNKNOWN_TECH_LEVEL = -1;

    // Base defaults for standard crafting
    public static final int BASE_TECH_LEVEL = 1;
    public static final float DEFAULT_RECIPE_MULTIPLIER = 1.0f;

    private Constants() {
    }
}