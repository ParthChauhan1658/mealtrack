package com.parth.mealtrack.web;

import com.parth.mealtrack.domain.CentreType;
import jakarta.validation.constraints.*;

public record CentreRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull CentreType type,
        @NotBlank @Size(max = 60) String district,
        @NotBlank @Size(max = 60) String block,
        @Min(1) int expectedMeals) {}
