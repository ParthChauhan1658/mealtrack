package com.parth.mealtrack.web;

import com.parth.mealtrack.domain.MealReport;
import com.parth.mealtrack.domain.ParseMethod;
import com.parth.mealtrack.domain.ReportSource;
import java.time.Instant;
import java.time.LocalDate;

public record ReportResponse(Long id, String centreCode, LocalDate date, int mealsServed, ReportSource source,
                             ParseMethod parseMethod, Instant createdAt, Instant updatedAt) {
    public static ReportResponse from(MealReport r) {
        return new ReportResponse(r.getId(), r.getCentre().getCode(), r.getReportDate(), r.getMealsServed(),
                r.getSource(), r.getParseMethod(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
