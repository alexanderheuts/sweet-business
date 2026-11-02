package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.util.Constants;

/**
 * Represents the calculated economic cost and infrastructure requirements to produce an item.
 *
 * @param value            The active economic point value of the item (used in recipes).
 * @param calculatedValue  The true mathematical value (Shadow Value) derived by the engine.
 * @param depth            The longest chain of consecutive crafting steps from a raw material.
 * @param techLevel        The highest tier of machinery/recipe type required in its lineage.
 * @param isBulkFarmable   True if the item can be mass-produced autonomously.
 * @param isConfigOverride True if the active 'value' was forced by the manual config.
 */
public record ItemValuation(
        float value,
        float calculatedValue,
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
     * Backward-compatible constructor for items priced purely by the engine.
     * Sets the calculatedValue to match the active value, and flags it as NOT an override.
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
     * Helper to create a Config Override baseline.
     * calculatedValue starts at 0.0f until the engine discovers its true math.
     */
    public static ItemValuation createOverride(float configValue, int techLevel) {
        return new ItemValuation(configValue, 0.0f, 0, techLevel, false, true);
    }

    /**
     * Creates a copy of this record with an updated calculated (shadow) value.
     */
    public ItemValuation withCalculatedValue(float newCalculated) {
        return new ItemValuation(this.value, newCalculated, this.depth, this.techLevel, this.isBulkFarmable, this.isConfigOverride);
    }

    public ItemValuation withBulkFarmable(boolean bulkFarmable) {
        return new ItemValuation(value, calculatedValue, depth, techLevel, bulkFarmable, isConfigOverride);
    }
}
