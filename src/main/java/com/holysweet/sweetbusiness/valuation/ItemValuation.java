package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.util.Constants;

/**
 * Represents the calculated economic cost and infrastructure requirements to produce an item.
 *
 * @param value     The total calculated economic point value of the item.
 * @param depth     The longest chain of consecutive crafting steps from a raw material.
 * @param techLevel The highest tier of machinery/recipe type required in its lineage.
 */
public record ItemValuation(float value, int depth, int techLevel) {

    /**
     * A constant representing an item that has not yet been calculated
     * or cannot be crafted/gathered. Use this to avoid null checks.
     */
    public static final ItemValuation UNKNOWN = new ItemValuation(
            Constants.UNKNOWN_VALUE,
            Constants.UNKNOWN_DEPTH,
            Constants.UNKNOWN_TECH_LEVEL
    );

    /**
     * Helper method to quickly check if a valuation is valid.
     */
    public boolean isKnown() {
        return this.value != Constants.UNKNOWN_VALUE;
    }
}