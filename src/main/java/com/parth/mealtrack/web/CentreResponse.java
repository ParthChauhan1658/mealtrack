package com.parth.mealtrack.web;

import com.parth.mealtrack.domain.Centre;
import com.parth.mealtrack.domain.CentreType;

public record CentreResponse(Long id, String code, String name, CentreType type, String district, String block,
                             int expectedMeals, boolean active) {
    public static CentreResponse from(Centre c) {
        return new CentreResponse(c.getId(), c.getCode(), c.getName(), c.getType(), c.getDistrict(), c.getBlock(),
                c.getExpectedMeals(), c.isActive());
    }
}
