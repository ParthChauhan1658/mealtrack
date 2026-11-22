package com.parth.mealtrack.service.anomaly;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Inputs for the rules.
 *
 * @param expectedMeals the centre's typical daily meals
 * @param mealsServed   the value just reported
 * @param history       meals of the most recent reported days before this one (newest first)
 */
public record AnomalyContext(int expectedMeals, int mealsServed, List<Integer> history) {

    public OptionalDouble historyAverage() {
        return history.stream().mapToInt(Integer::intValue).average();
    }
}
