package com.parth.mealtrack.repo;

import java.time.LocalDate;

public interface TrendRow {
    LocalDate getDate();

    long getMeals();

    long getReported();
}
