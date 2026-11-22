package com.parth.mealtrack.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parth.mealtrack.PostgresTestBase;
import com.parth.mealtrack.domain.Centre;
import com.parth.mealtrack.domain.CentreType;
import com.parth.mealtrack.repo.CentreRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Full stack with no LLM configured: proves the app works on rules alone. */
@SpringBootTest
@AutoConfigureMockMvc
class ParseApiTest extends PostgresTestBase {
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Autowired MockMvc mvc;
    @Autowired CentreRepository centres;

    @BeforeEach
    void centre() {
        if (!centres.existsByCode("PRS-1")) {
            centres.save(new Centre("PRS-1", "Parse test", CentreType.SCHOOL, "PrsD1", "B", 200));
        }
    }

    private ResultActions parse(String code, String message, String key) throws Exception {
        String body = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(java.util.Map.of("centreCode", code, "message", message));
        var req = post("/api/reports/parse").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) req = req.header("X-API-Key", key);
        return mvc.perform(req);
    }

    @Test
    void confidentMessageIsSavedViaRulesThenUpdatedOnResend() throws Exception {
        parse("PRS-1", "aaj 180 bachchon ko khana diya, 20 absent the", API_KEY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealsServed").value(180))
                .andExpect(jsonPath("$.parseMethod").value("RULES"))
                .andExpect(jsonPath("$.confidence").value("HIGH"))
                .andExpect(jsonPath("$.date").value(TODAY.toString()))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.report.parseMethod").value("RULES"));
        parse("PRS-1", "aaj 185 bachchon ko khana diya", API_KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealsServed").value(185))
                .andExpect(jsonPath("$.created").value(false));
    }

    @Test
    void messageWithExplicitDateIsSavedForThatDate() throws Exception {
        LocalDate d = TODAY.minusDays(3);
        parse("PRS-1", "served 150 meals on " + d, API_KEY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(d.toString()));
    }

    @Test
    void uncertainMessageIs422SavesNothingAndRaisesAlert() throws Exception {
        centres.save(new Centre("PRS-2", "Parse test 2", CentreType.ANGANWADI, "PrsD2", "B", 50));
        parse("PRS-2", "khana nahi bana aaj, gas khatam", API_KEY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(containsString("Nothing was saved")))
                .andExpect(jsonPath("$.parsed.mealsServed").value(0))
                .andExpect(jsonPath("$.parsed.confidence").value("LOW"))
                .andExpect(jsonPath("$.parsed.parseMethod").value("RULES"));

        mvc.perform(get("/api/reports").param("centreCode", "PRS-2"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/alerts").param("district", "PrsD2").param("type", "PARSE_UNCERTAIN"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("MEDIUM"))
                .andExpect(jsonPath("$[0].message").value(containsString("khana nahi bana")));
    }

    @Test
    void repeatedUncertainMessagesDoNotDuplicateTheAlert() throws Exception {
        centres.save(new Centre("PRS-3", "Parse test 3", CentreType.SCHOOL, "PrsD3", "B", 50));
        parse("PRS-3", "hello", API_KEY).andExpect(status().isUnprocessableEntity());
        parse("PRS-3", "hi again", API_KEY).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/alerts").param("district", "PrsD3"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void missingApiKeyIs401() throws Exception {
        parse("PRS-1", "served 10", null).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownCentreIs400() throws Exception {
        parse("NOPE", "served 10", API_KEY).andExpect(status().isBadRequest());
    }

    @Test
    void blankMessageIs400() throws Exception {
        parse("PRS-1", "  ", API_KEY).andExpect(status().isBadRequest());
    }

    @Test
    void overlongMessageIs400() throws Exception {
        parse("PRS-1", "x".repeat(1001), API_KEY).andExpect(status().isBadRequest());
    }

    @Test
    void confidentButImpossibleNumberIsRejectedByTheSameValidation() throws Exception {
        parse("PRS-1", "served 9999 kids", API_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("probable typo")));
    }

    @Test
    void messageForTooOldADateIsRejected() throws Exception {
        parse("PRS-1", "served 100 meals on " + TODAY.minusDays(30), API_KEY).andExpect(status().isBadRequest());
    }
}
