package com.parth.mealtrack.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SummaryServiceTest {
    @Test void roundsToOneDecimal() { assertEquals(96.2, SummaryService.coveragePercent(962, 1000)); }
    @Test void roundsHalfUp() { assertEquals(66.7, SummaryService.coveragePercent(2, 3)); }
    @Test void noActiveCentresMeansZeroNotNaN() { assertEquals(0.0, SummaryService.coveragePercent(0, 0)); }
    @Test void fullCoverageIsOneHundred() { assertEquals(100.0, SummaryService.coveragePercent(7, 7)); }
}
