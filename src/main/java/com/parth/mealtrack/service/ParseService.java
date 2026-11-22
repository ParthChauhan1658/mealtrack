package com.parth.mealtrack.service;

import com.parth.mealtrack.config.MealtrackProperties;
import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.domain.Centre;
import com.parth.mealtrack.domain.ReportSource;
import com.parth.mealtrack.domain.Severity;
import com.parth.mealtrack.llm.Confidence;
import com.parth.mealtrack.llm.LlmClient;
import com.parth.mealtrack.llm.LlmException;
import com.parth.mealtrack.llm.ParsedReport;
import com.parth.mealtrack.llm.RuleBasedParser;
import com.parth.mealtrack.repo.AlertRepository;
import com.parth.mealtrack.repo.CentreRepository;
import com.parth.mealtrack.web.ApiException;
import com.parth.mealtrack.web.ParseUncertainException;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Turns a free-text message into a saved report. The LLM is optional and never trusted: its output is validated
 * with the same rules as a structured report, and any failure falls back to {@link RuleBasedParser}.
 */
@Service
public class ParseService {
    private static final Logger log = LoggerFactory.getLogger(ParseService.class);

    public record Outcome(ParsedReport parsed, ReportService.SubmitResult saved) {}

    private final LlmClient llm; // null when provider is "none"
    private final RuleBasedParser rules;
    private final ReportService reports;
    private final CentreRepository centres;
    private final AlertRepository alerts;
    private final Clock clock;
    private final MealtrackProperties.Llm cfg;

    public ParseService(ObjectProvider<LlmClient> llm, RuleBasedParser rules, ReportService reports,
                        CentreRepository centres, AlertRepository alerts, Clock clock, MealtrackProperties props) {
        this.llm = llm.getIfAvailable();
        this.rules = rules;
        this.reports = reports;
        this.centres = centres;
        this.alerts = alerts;
        this.clock = clock;
        this.cfg = props.llm();
    }

    public Outcome parseAndSave(String centreCode, String message) {
        Centre centre = centres.findByCode(centreCode)
                .orElseThrow(() -> ApiException.badRequest("Unknown centre code: " + centreCode));
        LocalDate today = LocalDate.now(clock);

        ParsedReport parsed = tryLlm(centre, message, today);
        if (parsed == null) {
            parsed = timed("RULES", () -> rules.parse(message, today));
        }

        if (parsed.confidence() == Confidence.LOW || parsed.mealsServed() == null) {
            flagForHuman(centre, message, parsed, today);
            throw new ParseUncertainException("Could not read this message with confidence (%s found %s meals). "
                    .formatted(parsed.method(), parsed.mealsServed() == null ? "no number of" : parsed.mealsServed())
                    + "Nothing was saved; a human has been alerted to check it.", parsed);
        }
        var saved = reports.submit(centre.getCode(), parsed.date(), parsed.mealsServed(), ReportSource.MESSAGE,
                message, parsed.method());
        return new Outcome(parsed, saved);
    }

    /** Returns a validated LLM result, or null if the LLM is absent or failed in any way. */
    private ParsedReport tryLlm(Centre centre, String message, LocalDate today) {
        if (llm == null) return null;
        for (int attempt = 0; ; attempt++) {
            long start = System.nanoTime();
            try {
                ParsedReport p = llm.parse(message, today);
                if (p == null || p.mealsServed() == null) throw new LlmException("LLM found no number", false);
                reports.validate(centre, p.date(), p.mealsServed()); // never trust the model's number
                logParse("LLM", true, start, null);
                return p;
            } catch (LlmException e) {
                logParse("LLM", false, start, e.getMessage());
                if (!e.isRetryable() || attempt >= cfg.maxRetries()) return null;
                sleep((long) cfg.backoffMs() << attempt);
            } catch (ApiException e) {
                logParse("LLM", false, start, "failed validation: " + e.getMessage());
                return null;
            } catch (RuntimeException e) {
                logParse("LLM", false, start, "unexpected " + e.getClass().getSimpleName());
                return null;
            }
        }
    }

    private void flagForHuman(Centre centre, String message, ParsedReport parsed, LocalDate today) {
        LocalDate alertDate = parsed.date() != null && !parsed.date().isAfter(today)
                && !parsed.date().isBefore(today.minusDays(7)) ? parsed.date() : today;
        String shown = message.length() > 200 ? message.substring(0, 200) + "..." : message;
        alerts.insertIfAbsent(centre.getId(), alertDate, AlertType.PARSE_UNCERTAIN.name(), Severity.MEDIUM.name(),
                "Message could not be parsed with confidence (%s, found %s): \"%s\""
                        .formatted(parsed.method(), parsed.mealsServed(), shown));
    }

    private ParsedReport timed(String method, java.util.function.Supplier<ParsedReport> work) {
        long start = System.nanoTime();
        ParsedReport p = work.get();
        logParse(method, true, start, null);
        return p;
    }

    private static void logParse(String method, boolean ok, long startNanos, String problem) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000;
        if (ok) log.info("parse method={} success=true latencyMs={}", method, ms);
        else log.warn("parse method={} success=false latencyMs={} reason={}", method, ms, problem);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
