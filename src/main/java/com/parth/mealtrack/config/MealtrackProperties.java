package com.parth.mealtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("mealtrack")
public record MealtrackProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("http://localhost:5173") String corsOrigin,
        @DefaultValue Validation validation) {

    /** Report acceptance rules (spec section 7). */
    public record Validation(
            @DefaultValue("7") int maxAgeDays,
            @DefaultValue("2.0") double rejectFactor) {}
}
