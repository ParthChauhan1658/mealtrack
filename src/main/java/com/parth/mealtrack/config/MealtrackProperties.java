package com.parth.mealtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("mealtrack")
public record MealtrackProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("http://localhost:5173") String corsOrigin,
        @DefaultValue Validation validation,
        @DefaultValue Anomaly anomaly,
        @DefaultValue Llm llm) {

    /** Report acceptance rules (spec section 7). */
    public record Validation(
            @DefaultValue("7") int maxAgeDays,
            @DefaultValue("2.0") double rejectFactor) {}

    /** Anomaly thresholds (spec section 8). Ratios are fractions of the centre's recent average. */
    public record Anomaly(
            @DefaultValue("0.60") double dropRatio,
            @DefaultValue("0.40") double severeDropRatio,
            @DefaultValue("1.40") double spikeRatio,
            @DefaultValue("1.5") double capacityFactor,
            @DefaultValue("7") int windowDays,
            @DefaultValue("3") int minHistoryDays,
            @DefaultValue("14:00") String cutoffTime,
            @DefaultValue("0 0 14 * * MON-SAT") String missingReportCron) {

        public static Anomaly defaults() {
            return new Anomaly(0.60, 0.40, 1.40, 1.5, 7, 3, "14:00", "0 0 14 * * MON-SAT");
        }
    }

    /** LLM provider settings. provider: claude | gemini | none. With none (or any failure) rules parse the message. */
    public record Llm(
            @DefaultValue("none") String provider,
            @DefaultValue("") String apiKey,
            @DefaultValue("") String model,
            @DefaultValue("") String baseUrl,
            @DefaultValue("5000") int timeoutMs,
            @DefaultValue("2") int maxRetries,
            @DefaultValue("300") int backoffMs) {

        public static Llm defaults() {
            return new Llm("none", "", "", "", 5000, 2, 300);
        }
    }
}
