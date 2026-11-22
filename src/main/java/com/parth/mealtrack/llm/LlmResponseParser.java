package com.parth.mealtrack.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parth.mealtrack.domain.ParseMethod;
import java.time.DateTimeException;
import java.time.LocalDate;

/** Strictly validates the JSON an LLM returned. Anything unexpected is an {@link LlmException}. */
public final class LlmResponseParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmResponseParser() {}

    public static ParsedReport parse(String raw, LocalDate today) {
        if (raw == null) throw invalid("empty response");
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) throw invalid("no JSON object");
        JsonNode node;
        try {
            node = MAPPER.readTree(raw.substring(start, end + 1));
        } catch (Exception e) {
            throw invalid("malformed JSON");
        }
        if (!node.isObject() || !node.has("mealsServed") || !node.has("date") || !node.has("confidence")) {
            throw invalid("missing field");
        }

        JsonNode meals = node.get("mealsServed");
        Integer mealsServed = null;
        if (meals.isNull()) {
            // an explicit "don't know"; the caller decides what to do
        } else if (meals.isIntegralNumber() && meals.canConvertToInt() && meals.asInt() >= 0) {
            mealsServed = meals.asInt();
        } else {
            throw invalid("mealsServed is not a non-negative integer");
        }

        JsonNode d = node.get("date");
        LocalDate date;
        if (d.isNull()) {
            date = today;
        } else if (d.isTextual()) {
            try {
                date = LocalDate.parse(d.asText());
            } catch (DateTimeException e) {
                throw invalid("date is not YYYY-MM-DD");
            }
        } else {
            throw invalid("date is not a string");
        }

        JsonNode c = node.get("confidence");
        if (!c.isTextual()) throw invalid("confidence is not a string");
        Confidence confidence;
        try {
            confidence = Confidence.valueOf(c.asText().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw invalid("confidence must be HIGH or LOW");
        }
        return new ParsedReport(mealsServed, date, confidence, ParseMethod.LLM);
    }

    private static LlmException invalid(String why) {
        return new LlmException("Invalid LLM output: " + why, false);
    }
}
