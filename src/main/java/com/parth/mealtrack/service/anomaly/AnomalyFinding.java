package com.parth.mealtrack.service.anomaly;

import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.domain.Severity;

public record AnomalyFinding(AlertType type, Severity severity, String message) {}
