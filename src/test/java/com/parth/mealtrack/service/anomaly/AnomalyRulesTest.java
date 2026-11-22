package com.parth.mealtrack.service.anomaly;

import static org.junit.jupiter.api.Assertions.*;

import com.parth.mealtrack.config.MealtrackProperties;
import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.domain.Severity;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Boundary tests for each rule, using the default thresholds. */
class AnomalyRulesTest {
    final MealtrackProperties props = new MealtrackProperties("k", "http://x",
            new MealtrackProperties.Validation(7, 2.0), MealtrackProperties.Anomaly.defaults(), MealtrackProperties.Llm.defaults());
    final SuddenDropRule drop = new SuddenDropRule(props);
    final SpikeRule spike = new SpikeRule(props);
    final ExceedsCapacityRule capacity = new ExceedsCapacityRule(props);
    final ZeroMealsRule zero = new ZeroMealsRule();

    /** Seven days all at 100, so the average is exactly 100. */
    static AnomalyContext ctx(int expected, int served) {
        return new AnomalyContext(expected, served, Collections.nCopies(7, 100));
    }

    // --- SUDDEN_DROP ---
    @Test void dropAtExactly60PercentDoesNotFire() { assertTrue(drop.evaluate(ctx(100, 60)).isEmpty()); }
    @Test void dropJustBelow60PercentIsMedium() {
        var f = drop.evaluate(ctx(100, 59)).orElseThrow();
        assertEquals(AlertType.SUDDEN_DROP, f.type());
        assertEquals(Severity.MEDIUM, f.severity());
    }
    @Test void dropAtExactly40PercentIsStillMedium() {
        assertEquals(Severity.MEDIUM, drop.evaluate(ctx(100, 40)).orElseThrow().severity());
    }
    @Test void dropJustBelow40PercentIsHigh() {
        assertEquals(Severity.HIGH, drop.evaluate(ctx(100, 39)).orElseThrow().severity());
    }
    @Test void dropMessageContainsTheNumbers() {
        assertEquals("Reported 38 meals; 7-day average is 100 (38%).", drop.evaluate(ctx(100, 38)).orElseThrow().message());
    }
    @Test void dropNeedsThreeDaysOfHistory() {
        assertTrue(drop.evaluate(new AnomalyContext(100, 10, List.of(100, 100))).isEmpty());
        assertTrue(drop.evaluate(new AnomalyContext(100, 10, List.of(100, 100, 100))).isPresent());
    }
    @Test void zeroMealsIsLeftToTheZeroMealsRule() { assertTrue(drop.evaluate(ctx(100, 0)).isEmpty()); }
    @Test void dropWithNoHistoryDoesNotFire() { assertTrue(drop.evaluate(new AnomalyContext(100, 0, List.of())).isEmpty()); }

    // --- SPIKE ---
    @Test void spikeAtExactly140PercentDoesNotFire() { assertTrue(spike.evaluate(ctx(200, 140)).isEmpty()); }
    @Test void spikeJustAbove140PercentFires() {
        var f = spike.evaluate(ctx(200, 141)).orElseThrow();
        assertEquals(AlertType.SPIKE, f.type());
        assertEquals(Severity.MEDIUM, f.severity());
    }
    @Test void spikeNeedsThreeDaysOfHistory() {
        assertTrue(spike.evaluate(new AnomalyContext(200, 300, List.of(100, 100))).isEmpty());
    }

    // --- EXCEEDS_CAPACITY ---
    @Test void capacityAtExactly150PercentDoesNotFire() { assertTrue(capacity.evaluate(ctx(100, 150)).isEmpty()); }
    @Test void capacityJustAbove150PercentFires() {
        var f = capacity.evaluate(ctx(100, 151)).orElseThrow();
        assertEquals(AlertType.EXCEEDS_CAPACITY, f.type());
        assertEquals(Severity.MEDIUM, f.severity());
    }
    @Test void capacityNeedsNoHistory() {
        assertTrue(capacity.evaluate(new AnomalyContext(100, 160, List.of())).isPresent());
    }

    // --- ZERO_MEALS ---
    @Test void zeroFiresOnZero() {
        var f = zero.evaluate(ctx(100, 0)).orElseThrow();
        assertEquals(AlertType.ZERO_MEALS, f.type());
        assertEquals(Severity.MEDIUM, f.severity());
    }
    @Test void zeroDoesNotFireOnOne() { assertTrue(zero.evaluate(ctx(100, 1)).isEmpty()); }

    // --- thresholds come from config, not constants ---
    @ParameterizedTest
    @CsvSource({"0.50,55,false", "0.50,49,true", "0.80,79,true", "0.80,80,false"})
    void dropRatioIsConfigurable(double ratio, int served, boolean fires) {
        var cfg = new MealtrackProperties.Anomaly(ratio, 0.1, 1.4, 1.5, 7, 3, "14:00", "0 0 14 * * MON-SAT");
        var rule = new SuddenDropRule(new MealtrackProperties("k", "x", new MealtrackProperties.Validation(7, 2.0), cfg, MealtrackProperties.Llm.defaults()));
        assertEquals(fires, rule.evaluate(ctx(100, served)).isPresent());
    }
}
