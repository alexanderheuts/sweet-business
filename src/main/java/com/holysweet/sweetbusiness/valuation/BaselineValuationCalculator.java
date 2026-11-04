package com.holysweet.sweetbusiness.valuation;

public final class BaselineValuationCalculator {

    private BaselineValuationCalculator() {}

    public static EconomyResult calculate(EconomyInputs inputs, boolean includeConfiguredAnchors) {
        return ProductionValuationCalculator.calculate(inputs, includeConfiguredAnchors, true);
    }
}
