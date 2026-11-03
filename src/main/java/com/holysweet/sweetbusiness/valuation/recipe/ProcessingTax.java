package com.holysweet.sweetbusiness.valuation.recipe;

public record ProcessingTax(double basePercentage, double tierPercentage, double manualPercentage) {

    public double totalPercentage() {
        return basePercentage + tierPercentage + manualPercentage;
    }

    public double calculateUnitValue(double ingredientCost, int outputCount) {
        return ingredientCost * (1.0 + totalPercentage() / 100.0) / outputCount;
    }
}
