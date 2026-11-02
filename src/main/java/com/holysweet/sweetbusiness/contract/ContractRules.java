package com.holysweet.sweetbusiness.contract;

import com.holysweet.sweetbusiness.util.Constants;

public final class ContractRules {

    private ContractRules() {}

    public static boolean isValidTier(int tier) {
        return tier >= Constants.MIN_CONTRACT_TIER && tier <= Constants.MAX_CONTRACT_TIER;
    }

    public static void validateTier(int tier) {
        if (!isValidTier(tier)) {
            throw new IllegalArgumentException("Contract tier must be between "
                    + Constants.MIN_CONTRACT_TIER + " and " + Constants.MAX_CONTRACT_TIER);
        }
    }

    public static void validateLineCount(int tier, int lineCount) {
        validateTier(tier);
        int maximumLines = Constants.BASE_CONTRACT_LINES + tier;
        if (lineCount < Constants.BASE_CONTRACT_LINES || lineCount > maximumLines) {
            throw new IllegalArgumentException("Contract must have between "
                    + Constants.BASE_CONTRACT_LINES + " and " + maximumLines + " delivery lines");
        }
    }
}
