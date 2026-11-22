package com.parth.mealtrack.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parth.mealtrack.PostgresTestBase;
import com.parth.mealtrack.domain.Centre;
import com.parth.mealtrack.domain.CentreType;
import com.parth.mealtrack.repo.AlertRepository;
import com.parth.mealtrack.repo.CentreRepository;
import com.parth.mealtrack.service.AnomalyService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end: report -> rules -> alerts -> list/resolve, against real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
class AlertFlowTest extends PostgresTestBase {
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Autowired MockMvc mvc;
    @Autowired CentreRepository centres;
    @Autowired AlertRepository alertRepo;
    @Autowired AnomalyService anomalyService;

    private void report(String code, LocalDate date, int meals) throws Exception {
        mvc.perform(post("/api/reports").header("X-API-Key", API_KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"centreCode\":\"%s\",\"date\":\"%s\",\"mealsServed\":%d}".formatted(code, date, meals)))
                .andExpect(status().is2xxSuccessful());
    }

    private Centre centre(String code, String district, int expected) {
        return centres.save(new Centre(code, "Flow " + code, CentreType.SCHOOL, district, "B", expected));
    }

    /** Four normal days ending the day before today, all at 100. */
    private void history(String code) throws Exception {
        for (int i = 5; i >= 2; i--) report(code, TODAY.minusDays(i), 100);
    }

    @Test
    void dropCreatesAlertWithNumbersAndCorrectionResolvesIt() throws Exception {
        centre("FLOW-1", "FlowD1", 150);
        history("FLOW-1");
        report("FLOW-1", TODAY, 55);

        mvc.perform(get("/api/alerts").param("district", "FlowD1").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("SUDDEN_DROP"))
                .andExpect(jsonPath("$[0].severity").value("MEDIUM"))
                .andExpect(jsonPath("$[0].message").value(containsString("Reported 55 meals; 4-day average is 100 (55%)")
                ));
    }

    @Test
    void resubmittingTheSameBadValueDoesNotDuplicateTheAlert() throws Exception {
        centre("FLOW-2", "FlowD2", 150);
        history("FLOW-2");
        report("FLOW-2", TODAY, 30);
        report("FLOW-2", TODAY, 30);
        report("FLOW-2", TODAY, 31);
        mvc.perform(get("/api/alerts").param("district", "FlowD2"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("HIGH"));
    }

    @Test
    void correctingTheValueAutoResolvesTheStaleAlert() throws Exception {
        centre("FLOW-3", "FlowD3", 150);
        history("FLOW-3");
        report("FLOW-3", TODAY, 20);
        report("FLOW-3", TODAY, 100);
        mvc.perform(get("/api/alerts").param("district", "FlowD3"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"));
    }

    @Test
    void spikeAndCapacityFireTogether() throws Exception {
        centre("FLOW-4", "FlowD4", 100);
        history("FLOW-4");
        report("FLOW-4", TODAY, 160); // >140% of avg 100 and >150% of expected 100
        mvc.perform(get("/api/alerts").param("district", "FlowD4").param("type", "SPIKE"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/alerts").param("district", "FlowD4").param("type", "EXCEEDS_CAPACITY"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void zeroMealsRaisesAlert() throws Exception {
        centre("FLOW-5", "FlowD5", 100);
        report("FLOW-5", TODAY, 0);
        mvc.perform(get("/api/alerts").param("district", "FlowD5").param("type", "ZERO_MEALS"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("MEDIUM"));
    }

    @Test
    void lateReportAutoResolvesMissingReportAlert() throws Exception {
        Centre c = centre("FLOW-6", "FlowD6", 100);
        alertRepo.insertIfAbsent(c.getId(), TODAY, "MISSING_REPORT", "LOW", "No report by cutoff");
        mvc.perform(get("/api/alerts").param("district", "FlowD6").param("status", "OPEN"))
                .andExpect(jsonPath("$.length()").value(1));
        report("FLOW-6", TODAY, 90);
        mvc.perform(get("/api/alerts").param("district", "FlowD6").param("status", "OPEN"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void resolveEndpointNeedsKeyAndIsIdempotent() throws Exception {
        centre("FLOW-7", "FlowD7", 100);
        report("FLOW-7", TODAY, 0);
        String id = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(get("/api/alerts").param("district", "FlowD7")).andReturn().getResponse().getContentAsString(),
                "$[0].id").toString();

        mvc.perform(patch("/api/alerts/" + id + "/resolve")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/alerts/" + id + "/resolve").header("X-API-Key", API_KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());
        mvc.perform(patch("/api/alerts/" + id + "/resolve").header("X-API-Key", API_KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void resolvingUnknownAlertIs404() throws Exception {
        mvc.perform(patch("/api/alerts/99999999/resolve").header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtersByDate() throws Exception {
        centre("FLOW-8", "FlowD8", 100);
        report("FLOW-8", TODAY.minusDays(1), 0);
        mvc.perform(get("/api/alerts").param("district", "FlowD8").param("date", TODAY.toString()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/alerts").param("district", "FlowD8").param("date", TODAY.minusDays(1).toString()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anAlertAResolvedByAHumanIsNotReopenedByARerun() throws Exception {
        centre("FLOW-9", "FlowD9", 100);
        report("FLOW-9", TODAY, 0);
        String id = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(get("/api/alerts").param("district", "FlowD9")).andReturn().getResponse().getContentAsString(),
                "$[0].id").toString();
        mvc.perform(patch("/api/alerts/" + id + "/resolve").header("X-API-Key", API_KEY)).andExpect(status().isOk());
        report("FLOW-9", TODAY, 0);
        mvc.perform(get("/api/alerts").param("district", "FlowD9").param("status", "OPEN"))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
