package com.parth.mealtrack.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ReportRequest(
        @NotBlank String centreCode,
        @NotNull LocalDate date,
        @NotNull @Min(0) Integer mealsServed) {}
