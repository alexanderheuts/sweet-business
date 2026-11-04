package com.holysweet.sweetbusiness.valuation;

public final class ManufacturingCostCalculator {

    private ManufacturingCostCalculator() {}

    public static EconomyResult calculate(EconomyInputs inputs, boolean includeConfiguredAnchors) {
        return ProductionValuationCalculator.calculate(inputs, includeConfiguredAnchors, false);
    }
}