package com.parth.mealtrack.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.parth.mealtrack.config.MealtrackProperties;
import com.parth.mealtrack.domain.*;
import com.parth.mealtrack.llm.*;
import com.parth.mealtrack.repo.AlertRepository;
import com.parth.mealtrack.repo.CentreRepository;
import com.parth.mealtrack.repo.MealReportRepository;
import com.parth.mealtrack.web.ApiException;
import com.parth.mealtrack.web.ParseUncertainException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/** Proves the LLM is optional and never trusted: every failure path ends in the rule-based parser. */
class ParseServiceTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22);
    static final String MSG = "served 90 kids, 10 absent"; // rules read this as 90, HIGH

    final LlmClient llm = mock(LlmClient.class);
    final AlertRepository alerts = mock(AlertRepository.class);
    final CentreRepository centres = mock(CentreRepository.class);
    final Centre centre = new Centre("C-1", "c", CentreType.SCHOOL, "D", "B", 100);
    ReportService reports;
    ParseService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(centre, "id", 1L);
        when(centres.findByCode("C-1")).thenReturn(Optional.of(centre));
        reports = spy(new ReportService(centres, mock(MealReportRepository.class),
                new MealtrackProperties("k", "x", new MealtrackProperties.Validation(7, 2.0),
                        MealtrackProperties.Anomaly.defaults(), MealtrackProperties.Llm.defaults()),
                Clock.fixed(Instant.parse("2026-11-22T06:00:00Z"), ZoneId.of("Asia/Kolkata")),
                mock(AnomalyService.class), mock(PlatformTransactionManager.class)));
        doAnswer(inv -> new ReportService.SubmitResult(new MealReport(centre, inv.getArgument(1)), true))
                .when(reports).submit(any(), any(), anyInt(), any(), any(), any());
        service = build(llm);
    }

    private ParseService build(LlmClient client) {
        @SuppressWarnings("unchecked")
        ObjectProvider<LlmClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        var cfg = new MealtrackProperties.Llm("claude", "key", "", "", 5000, 2, 1); // 1 ms backoff keeps tests fast
        return new ParseService(provider, new RuleBasedParser(), reports, centres, alerts,
                Clock.fixed(Instant.parse("2026-11-22T06:00:00Z"), ZoneId.of("Asia/Kolkata")),
                new MealtrackProperties("k", "x", new MealtrackProperties.Validation(7, 2.0),
                        MealtrackProperties.Anomaly.defaults(), cfg));
    }

    private static ParsedReport llmResult(Integer meals, LocalDate date, Confidence c) {
        return new ParsedReport(meals, date, c, ParseMethod.LLM);
    }

    private void assertSavedWith(int meals, ParseMethod method) {
        verify(reports).submit(eq("C-1"), any(), eq(meals), eq(ReportSource.MESSAGE), eq(MSG), eq(method));
    }

    @Test
    void usesLlmResultWhenItIsValid() {
        when(llm.parse(MSG, TODAY)).thenReturn(llmResult(88, TODAY, Confidence.HIGH));
        var out = service.parseAndSave("C-1", MSG);
        assertEquals(ParseMethod.LLM, out.parsed().method());
        assertSavedWith(88, ParseMethod.LLM);
    }

    @Test
    void timeoutIsRetriedTwiceThenFallsBackToRules() {
        when(llm.parse(any(), any())).thenThrow(new LlmException("timed out", true));
        var out = service.parseAndSave("C-1", MSG);
        verify(llm, times(3)).parse(MSG, TODAY); // 1 try + 2 retries
        assertEquals(ParseMethod.RULES, out.parsed().method());
        assertSavedWith(90, ParseMethod.RULES);
    }

    @Test
    void transientFailureThenSuccessUsesLlm() {
        when(llm.parse(any(), any())).thenThrow(new LlmException("503", true))
                .thenReturn(llmResult(91, TODAY, Confidence.HIGH));
        service.parseAndSave("C-1", MSG);
        verify(llm, times(2)).parse(MSG, TODAY);
        assertSavedWith(91, ParseMethod.LLM);
    }

    @Test
    void invalidOutputFallsBackWithoutRetrying() {
        when(llm.parse(any(), any())).thenThrow(new LlmException("Invalid LLM output: malformed JSON", false));
        service.parseAndSave("C-1", MSG);
        verify(llm, times(1)).parse(MSG, TODAY);
        assertSavedWith(90, ParseMethod.RULES);
    }

    @Test
    void wrongNumberFailingValidationFallsBackToRules() {
        when(llm.parse(any(), any())).thenReturn(llmResult(5000, TODAY, Confidence.HIGH)); // > 2x expected 100
        service.parseAndSave("C-1", MSG);
        assertSavedWith(90, ParseMethod.RULES);
    }

    @Test
    void futureDateFromLlmFallsBackToRules() {
        when(llm.parse(any(), any())).thenReturn(llmResult(90, TODAY.plusDays(3), Confidence.HIGH));
        service.parseAndSave("C-1", MSG);
        assertSavedWith(90, ParseMethod.RULES);
    }

    @Test
    void nullNumberFromLlmFallsBackToRules() {
        when(llm.parse(any(), any())).thenReturn(llmResult(null, TODAY, Confidence.HIGH));
        service.parseAndSave("C-1", MSG);
        assertSavedWith(90, ParseMethod.RULES);
    }

    @Test
    void missingApiKeyFallsBackToRules() {
        when(llm.parse(any(), any())).thenThrow(new LlmException("LLM API key is not configured", false));
        service.parseAndSave("C-1", MSG);
        verify(llm, times(1)).parse(MSG, TODAY);
        assertSavedWith(90, ParseMethod.RULES);
    }

    @Test
    void unexpectedRuntimeErrorFromLlmFallsBackToRules() {
        when(llm.parse(any(), any())).thenThrow(new IllegalStateException("boom"));
        service.parseAndSave("C-1", MSG);
        assertSavedWith(90, ParseMethod.RULES);
    }

    @Test
    void worksWithNoLlmConfiguredAtAll() {
        service = build(null);
        var out = service.parseAndSave("C-1", MSG);
        assertEquals(ParseMethod.RULES, out.parsed().method());
        assertSavedWith(90, ParseMethod.RULES);
        verifyNoInteractions(llm);
    }

    @Test
    void lowConfidenceFromLlmIsNotSavedAndRaisesAlert() {
        when(llm.parse(any(), any())).thenReturn(llmResult(0, TODAY, Confidence.LOW));
        var e = assertThrows(ParseUncertainException.class, () -> service.parseAndSave("C-1", MSG));
        assertEquals(422, e.getStatus().value());
        verify(reports, never()).submit(any(), any(), anyInt(), any(), any(), any());
        verify(alerts).insertIfAbsent(eq(1L), eq(TODAY), eq("PARSE_UNCERTAIN"), eq("MEDIUM"), contains(MSG));
    }

    @Test
    void lowConfidenceFromRulesIsNotSavedAndRaisesAlert() {
        service = build(null);
        String msg = "khana nahi bana aaj, gas khatam";
        var e = assertThrows(ParseUncertainException.class, () -> service.parseAndSave("C-1", msg));
        assertEquals(0, e.getParsed().mealsServed());
        assertEquals(ParseMethod.RULES, e.getParsed().method());
        verify(reports, never()).submit(any(), any(), anyInt(), any(), any(), any());
        verify(alerts).insertIfAbsent(eq(1L), eq(TODAY), eq("PARSE_UNCERTAIN"), eq("MEDIUM"), contains("khana nahi bana"));
    }

    @Test
    void messageWithNoNumberIsUncertain() {
        service = build(null);
        var e = assertThrows(ParseUncertainException.class, () -> service.parseAndSave("C-1", "hello"));
        assertNull(e.getParsed().mealsServed());
    }

    @Test
    void unknownCentreIsBadRequest() {
        when(centres.findByCode("NOPE")).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.parseAndSave("NOPE", MSG));
        verifyNoInteractions(llm);
    }
}
