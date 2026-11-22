package com.parth.mealtrack.llm;

import java.time.LocalDate;

/** Provider-neutral message parser. Implementations must throw {@link LlmException} on any failure. */
public interface LlmClient {
    ParsedReport parse(String message, LocalDate today);
}
