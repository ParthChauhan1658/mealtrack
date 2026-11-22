package com.parth.mealtrack.repo;

/** One district's figures for a single day; aliases match the JPQL in {@link CentreRepository}. */
public interface DistrictRow {
    String getDistrict();

    long getMeals();

    long getReported();

    long getTotal();
}
