package com.parth.mealtrack.web;

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

@SpringBootTest
@AutoConfigureMockMvc
class ReportApiTest extends PostgresTestBase {
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Autowired MockMvc mvc;
    @Autowired CentreRepository centres;

    @BeforeEach
    void centre() {
        if (!centres.existsByCode("RPT-1")) {
            centres.save(new Centre("RPT-1", "Report test", CentreType.SCHOOL, "RptDistrict", "B", 100));
        }
        if (!centres.existsByCode("RPT-OFF")) {
            Centre off = new Centre("RPT-OFF", "Inactive", CentreType.SCHOOL, "RptDistrict", "B", 100);
            off.setActive(false);
            centres.save(off);
        }
    }

    private ResultActions submit(String code, Object date, Object meals, String key) throws Exception {
        String body = "{\"centreCode\":\"%s\",\"date\":%s,\"mealsServed\":%s}".formatted(code,
                date == null ? "null" : "\"" + date + "\"", meals);
        var req = post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) req = req.header("X-API-Key", key);
        return mvc.perform(req);
    }

    @Test
    void newReportIs201AndResubmitIs200WithSameId() throws Exception {
        LocalDate d = TODAY.minusDays(2);
        String first = submit("RPT-1", d, 90, API_KEY).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealsServed").value(90)).andReturn().getResponse().getContentAsString();
        String second = submit("RPT-1", d, 95, API_KEY).andExpect(status().isOk())
                .andExpect(jsonPath("$.mealsServed").value(95)).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(
                com.jayway.jsonpath.JsonPath.read(first, "$.id").toString(),
                com.jayway.jsonpath.JsonPath.read(second, "$.id").toString());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.jayway.jsonpath.JsonPath.read(first, "$.createdAt").toString(),
                com.jayway.jsonpath.JsonPath.read(second, "$.createdAt").toString());
    }

    @Test
    void missingApiKeyIs401() throws Exception {
        submit("RPT-1", TODAY, 10, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void wrongApiKeyIs401() throws Exception {
        submit("RPT-1", TODAY, 10, "nope").andExpect(status().isUnauthorized());
    }

    @Test
    void negativeMealsIs400() throws Exception {
        submit("RPT-1", TODAY, -1, API_KEY).andExpect(status().isBadRequest());
    }

    @Test
    void futureDateIs400() throws Exception {
        submit("RPT-1", TODAY.plusDays(1), 10, API_KEY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("future")));
    }

    @Test
    void reportOlderThanSevenDaysIs400() throws Exception {
        submit("RPT-1", TODAY.minusDays(8), 10, API_KEY).andExpect(status().isBadRequest());
    }

    @Test
    void sevenDaysOldIsAccepted() throws Exception {
        submit("RPT-1", TODAY.minusDays(7), 10, API_KEY).andExpect(status().is2xxSuccessful());
    }

    @Test
    void unknownCentreIs400() throws Exception {
        submit("NOPE", TODAY, 10, API_KEY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unknown centre")));
    }

    @Test
    void inactiveCentreIs400() throws Exception {
        submit("RPT-OFF", TODAY, 10, API_KEY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("inactive")));
    }

    @Test
    void exactlyTwiceExpectedIsAcceptedButAboveIsRejected() throws Exception {
        submit("RPT-1", TODAY.minusDays(3), 200, API_KEY).andExpect(status().is2xxSuccessful());
        submit("RPT-1", TODAY.minusDays(3), 201, API_KEY).andExpect(status().isBadRequest());
    }

    @Test
    void missingFieldsIs400() throws Exception {
        submit("RPT-1", null, 10, API_KEY).andExpect(status().isBadRequest());
    }

    @Test
    void historyReturnsReportsInRange() throws Exception {
        submit("RPT-1", TODAY.minusDays(4), 50, API_KEY).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/reports").param("centreCode", "RPT-1")
                        .param("from", TODAY.minusDays(4).toString()).param("to", TODAY.minusDays(4).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mealsServed").value(50));
    }
}
