package com.parth.mealtrack.web;

import com.parth.mealtrack.llm.ParsedReport;
import org.springframework.http.HttpStatus;

/** The message was understood only with LOW confidence, so nothing was saved (HTTP 422). */
public class ParseUncertainException extends ApiException {
    private final ParsedReport parsed;

    public ParseUncertainException(String message, ParsedReport parsed) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
        this.parsed = parsed;
    }

    public ParsedReport getParsed() { return parsed; }
}
