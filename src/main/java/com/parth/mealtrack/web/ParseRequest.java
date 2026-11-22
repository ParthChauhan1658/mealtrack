package com.parth.mealtrack.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParseRequest(@NotBlank String centreCode, @NotBlank @Size(max = 1000) String message) {}
