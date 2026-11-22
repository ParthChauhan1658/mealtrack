package com.parth.mealtrack.web;

import com.parth.mealtrack.domain.ParseMethod;
import com.parth.mealtrack.llm.Confidence;
import java.time.LocalDate;

public record ParseResponse(String centreCode, LocalDate date, int mealsServed, ParseMethod parseMethod,
                            Confidence confidence, boolean created, ReportResponse report) {}
