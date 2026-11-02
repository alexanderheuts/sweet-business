package com.holysweet.sweetbusiness.util;

import java.util.Arrays;

public final class Statistics {

    private Statistics() {}

    public static double median(double[] samples) {
        if (samples.length == 0) {
            throw new IllegalArgumentException("Cannot calculate the median of an empty sample");
        }
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        return sorted[(sorted.length - 1) / 2] / 2.0 + sorted[sorted.length / 2] / 2.0;
    }
}
