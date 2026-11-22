package com.parth.mealtrack.web;

import java.time.LocalDate;
import java.util.List;

public final class SummaryDtos {
    private SummaryDtos() {}

    public record DistrictSummary(String district, long meals, long reported, long total) {}

    public record DailySummary(LocalDate date, long totalMeals, long centresActive, long centresReported,
                               double coveragePercent, long openAlerts, List<DistrictSummary> byDistrict) {}

    public record TrendPoint(LocalDate date, long totalMeals, long centresReported) {}
}
