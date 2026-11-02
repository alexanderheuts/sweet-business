package com.holysweet.sweetbusiness.contract.generator;

import com.holysweet.sweetbusiness.contract.ContractRules;

import java.util.List;

public record ContractPlan(int tier, double targetValue, List<Double> lineBudgets, int maxItemsPerLine) {

    public ContractPlan {
        if (!Double.isFinite(targetValue) || targetValue <= 0 || maxItemsPerLine <= 0) {
            throw new IllegalArgumentException("Target value and item cap must be positive and finite");
        }
        lineBudgets = List.copyOf(lineBudgets);
        ContractRules.validateLineCount(tier, lineBudgets.size());
        if (lineBudgets.stream().anyMatch(value -> !Double.isFinite(value) || value <= 0)) {
            throw new IllegalArgumentException("Line budgets must be finite and positive");
        }
    }
}
