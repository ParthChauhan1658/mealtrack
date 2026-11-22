package com.parth.mealtrack.web;

import com.parth.mealtrack.domain.Alert;
import com.parth.mealtrack.domain.AlertStatus;
import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.domain.Severity;
import java.time.Instant;
import java.time.LocalDate;

public record AlertResponse(Long id, String centreCode, String centreName, String district, LocalDate date,
                            AlertType type, Severity severity, String message, AlertStatus status,
                            Instant createdAt, Instant resolvedAt) {
    public static AlertResponse from(Alert a) {
        var c = a.getCentre();
        return new AlertResponse(a.getId(), c.getCode(), c.getName(), c.getDistrict(), a.getReportDate(), a.getType(),
                a.getSeverity(), a.getMessage(), a.getStatus(), a.getCreatedAt(), a.getResolvedAt());
    }
}
