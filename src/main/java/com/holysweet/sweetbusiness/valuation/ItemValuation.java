package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.util.Constants;

/**
 * Represents the calculated economic cost and infrastructure requirements to produce an item.
 *
 * @param value            The active economic point value of the item (used in recipes).
 * @param calculatedCost   The calculated cost, retained diagnostically when a hard config anchor fixes value.
 * @param depth            The longest ingredient chain in the selected production route, not the shortest route.
 * @param techLevel        The highest tier of machinery/recipe type required in its lineage.
 * @param isBulkFarmable   True if the item can be mass-produced autonomously.
 * @param isConfigOverride True if the active 'value' was forced by the manual config.
 */
public record ItemValuation(
        float value,
        float calculatedCost,
        int depth,
        int techLevel,
        boolean isBulkFarmable,
        boolean isConfigOverride
) {

    /**
     * A constant representing an item that has not yet been calculated
     * or cannot be crafted/gathered. Use this to avoid null checks.
     */
    public static final ItemValuation UNKNOWN = new ItemValuation(
            Constants.UNKNOWN_VALUE,
            Constants.UNKNOWN_VALUE,
            Constants.UNKNOWN_DEPTH,
            Constants.UNKNOWN_TECH_LEVEL,
            false,
            false
    );

    /**
     * Constructor for items priced by discovery and production calculations.
     * Sets the calculatedCost to match the active value, and flags it as NOT an override.
     */
    public ItemValuation(float value, int depth, int techLevel, boolean isBulkFarmable) {
        this(value, value, depth, techLevel, isBulkFarmable, false);
    }

    /**
     * Helper method to quickly check if a valuation is valid.
     */
    public boolean isKnown() {
        return this.value != Constants.UNKNOWN_VALUE;
    }

    /**
     * Creates a hard config anchor. Its calculated cost starts at zero until discovered;
     * updating that diagnostic cost never changes the active configured value.
     */
    public static ItemValuation createOverride(float configValue, int techLevel) {
        return new ItemValuation(configValue, 0.0f, 0, techLevel, false, true);
    }

    /**
     * Creates a copy with an updated diagnostic cost, preserving the active value.
     */
    public ItemValuation withCalculatedCost(float newCalculated) {
        return new ItemValuation(this.value, newCalculated, this.depth, this.techLevel, this.isBulkFarmable, this.isConfigOverride);
    }

    public ItemValuation withBulkFarmable(boolean bulkFarmable) {
        return new ItemValuation(value, calculatedCost, depth, techLevel, bulkFarmable, isConfigOverride);
    }
}
