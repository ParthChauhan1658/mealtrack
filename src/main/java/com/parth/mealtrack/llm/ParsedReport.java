package com.parth.mealtrack.llm;

import com.parth.mealtrack.domain.ParseMethod;
import java.time.LocalDate;

/**
 * Result of parsing a free-text message.
 *
 * @param mealsServed the number found, or null if none could be determined
 * @param date        the report date (today when the message names none)
 */
public record ParsedReport(Integer mealsServed, LocalDate date, Confidence confidence, ParseMethod method) {}
