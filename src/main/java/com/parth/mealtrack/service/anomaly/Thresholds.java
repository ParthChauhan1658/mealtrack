package com.parth.mealtrack.service.anomaly;

/** Strict comparisons that stay exact at boundaries such as "exactly 60%". */
final class Thresholds {
    private static final double EPS = 1e-9;

    private Thresholds() {}

    static boolean below(double value, double limit) { return value < limit - EPS; }

    static boolean above(double value, double limit) { return value > limit + EPS; }

    static long percent(double value, double base) { return Math.round(value / base * 100); }
}
