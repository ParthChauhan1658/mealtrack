package com.parth.mealtrack.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.parth.mealtrack.domain.ParseMethod;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LlmResponseParserTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    @Test
    void parsesAValidAnswer() {
        var p = LlmResponseParser.parse("{\"mealsServed\":180,\"date\":\"2026-11-21\",\"confidence\":\"HIGH\"}", TODAY);
        assertEquals(180, p.mealsServed());
        assertEquals(LocalDate.of(2026, 11, 21), p.date());
        assertEquals(Confidence.HIGH, p.confidence());
        assertEquals(ParseMethod.LLM, p.method());
    }

    @Test
    void toleratesCodeFencesAndLowercaseConfidence() {
        var p = LlmResponseParser.parse("```json\n{\"mealsServed\":7,\"date\":null,\"confidence\":\"low\"}\n```", TODAY);
        assertEquals(7, p.mealsServed());
        assertEquals(TODAY, p.date());
        assertEquals(Confidence.LOW, p.confidence());
    }

    @Test
    void nullNumberIsAllowedHereAndLeftToTheCaller() {
        var p = LlmResponseParser.parse("{\"mealsServed\":null,\"date\":null,\"confidence\":\"LOW\"}", TODAY);
        assertNull(p.mealsServed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "I think 180 meals were served",
            "{not json}",
            "[1,2,3]",
            "{\"mealsServed\":180,\"date\":null}",
            "{\"date\":null,\"confidence\":\"HIGH\"}",
            "{\"mealsServed\":\"180\",\"date\":null,\"confidence\":\"HIGH\"}",
            "{\"mealsServed\":180.5,\"date\":null,\"confidence\":\"HIGH\"}",
            "{\"mealsServed\":-4,\"date\":null,\"confidence\":\"HIGH\"}",
            "{\"mealsServed\":99999999999,\"date\":null,\"confidence\":\"HIGH\"}",
            "{\"mealsServed\":10,\"date\":\"22 Nov\",\"confidence\":\"HIGH\"}",
            "{\"mealsServed\":10,\"date\":20261122,\"confidence\":\"HIGH\"}",
            "{\"mealsServed\":10,\"date\":null,\"confidence\":\"MAYBE\"}",
            "{\"mealsServed\":10,\"date\":null,\"confidence\":1}"})
    void rejectsInvalidOutputAsNonRetryableFailure(String raw) {
        var e = assertThrows(LlmException.class, () -> LlmResponseParser.parse(raw, TODAY));
        assertFalse(e.isRetryable());
    }

    @Test
    void nullBodyIsRejected() {
        assertThrows(LlmException.class, () -> LlmResponseParser.parse(null, TODAY));
    }
}
