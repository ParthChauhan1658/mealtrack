package com.parth.mealtrack.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parth.mealtrack.PostgresTestBase;
import com.parth.mealtrack.domain.*;
import com.parth.mealtrack.repo.AlertRepository;
import com.parth.mealtrack.repo.CentreRepository;
import com.parth.mealtrack.repo.MealReportRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Aggregation checks against real PostgreSQL. Tables are emptied first so totals can be asserted exactly. */
@SpringBootTest
@AutoConfigureMockMvc
class SummaryApiTest extends PostgresTestBase {
    static final LocalDate DAY = LocalDate.of(2025, 6, 2);
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CentreRepository centres;
    @Autowired MealReportRepository reports;
    @Autowired AlertRepository alerts;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE alert, meal_report, centre RESTART IDENTITY CASCADE");
    }

    private Centre centre(String code, String district, boolean active) {
        Centre c = new Centre(code, code, CentreType.SCHOOL, district, "B", 100);
        c.setActive(active);
        return centres.save(c);
    }

    private void report(Centre c, LocalDate date, int meals) {
        MealReport r = new MealReport(c, date);
        r.setMealsServed(meals);
        r.setSource(ReportSource.SEED);
        reports.save(r);
    }

    @Test
    void dailySummaryAggregatesPerDistrictOverActiveCentres() throws Exception {
        Centre a1 = centre("A1", "Alpha", true), a2 = centre("A2", "Alpha", true);
        Centre b1 = centre("B1", "Beta", true), b2 = centre("B2", "Beta", false);
        centre("C1", "Gamma", true);
        report(a1, DAY, 100);
        report(a2, DAY, 50);
        report(b1, DAY, 70);
        report(b2, DAY, 999); // inactive centre: excluded
        report(a1, DAY.minusDays(1), 5000); // other day: excluded

        mvc.perform(get("/api/summary/daily").param("date", DAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(DAY.toString()))
                .andExpect(jsonPath("$.totalMeals").value(220))
                .andExpect(jsonPath("$.centresActive").value(4))
                .andExpect(jsonPath("$.centresReported").value(3))
                .andExpect(jsonPath("$.coveragePercent").value(75.0))
                .andExpect(jsonPath("$.byDistrict.length()").value(3))
                .andExpect(jsonPath("$.byDistrict[0].district").value("Alpha"))
                .andExpect(jsonPath("$.byDistrict[0].meals").value(150))
                .andExpect(jsonPath("$.byDistrict[0].reported").value(2))
                .andExpect(jsonPath("$.byDistrict[0].total").value(2))
                .andExpect(jsonPath("$.byDistrict[1].district").value("Beta"))
                .andExpect(jsonPath("$.byDistrict[1].meals").value(70))
                .andExpect(jsonPath("$.byDistrict[1].total").value(1))
                .andExpect(jsonPath("$.byDistrict[2].district").value("Gamma"))
                .andExpect(jsonPath("$.byDistrict[2].meals").value(0))
                .andExpect(jsonPath("$.byDistrict[2].reported").value(0))
                .andExpect(jsonPath("$.byDistrict[2].total").value(1));
    }

    @Test
    void openAlertsCountsAllOpenAlertsAndIgnoresResolvedOnes() throws Exception {
        Centre c = centre("A1", "Alpha", true);
        alerts.upsertOpen(c.getId(), DAY, "ZERO_MEALS", "MEDIUM", "m");
        alerts.upsertOpen(c.getId(), DAY.minusDays(1), "SPIKE", "MEDIUM", "m");
        alerts.upsertOpen(c.getId(), DAY, "SUDDEN_DROP", "HIGH", "m");
        alerts.resolveOpen(c.getId(), DAY, java.util.Set.of(AlertType.SUDDEN_DROP));

        mvc.perform(get("/api/summary/daily").param("date", DAY.toString()))
                .andExpect(jsonPath("$.openAlerts").value(2));
    }

    @Test
    void dayWithNoReportsHasZeroTotalsAndZeroCoverage() throws Exception {
        centre("A1", "Alpha", true);
        mvc.perform(get("/api/summary/daily").param("date", DAY.toString()))
                .andExpect(jsonPath("$.totalMeals").value(0))
                .andExpect(jsonPath("$.centresReported").value(0))
                .andExpect(jsonPath("$.coveragePercent").value(0.0));
    }

    @Test
    void emptyDatabaseDoesNotDivideByZero() throws Exception {
        mvc.perform(get("/api/summary/daily").param("date", DAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centresActive").value(0))
                .andExpect(jsonPath("$.coveragePercent").value(0.0))
                .andExpect(jsonPath("$.byDistrict.length()").value(0));
    }

    @Test
    void dateDefaultsToToday() throws Exception {
        mvc.perform(get("/api/summary/daily")).andExpect(jsonPath("$.date").value(TODAY.toString()));
    }

    @Test
    void invalidDateIs400() throws Exception {
        mvc.perform(get("/api/summary/daily").param("date", "22-11-2026")).andExpect(status().isBadRequest());
    }

    @Test
    void trendReturnsOnePointPerDayOldestFirstWithZeroFill() throws Exception {
        Centre a = centre("A1", "Alpha", true), b = centre("B1", "Beta", true);
        centre("X1", "Gone", false);
        report(a, TODAY.minusDays(1), 100);
        report(b, TODAY.minusDays(1), 40);
        report(a, TODAY.minusDays(3), 90);
        report(centres.findByCode("X1").orElseThrow(), TODAY.minusDays(3), 777); // inactive: excluded

        mvc.perform(get("/api/summary/trend").param("days", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].date").value(TODAY.minusDays(4).toString()))
                .andExpect(jsonPath("$[0].totalMeals").value(0))
                .andExpect(jsonPath("$[1].date").value(TODAY.minusDays(3).toString()))
                .andExpect(jsonPath("$[1].totalMeals").value(90))
                .andExpect(jsonPath("$[1].centresReported").value(1))
                .andExpect(jsonPath("$[2].totalMeals").value(0))
                .andExpect(jsonPath("$[3].totalMeals").value(140))
                .andExpect(jsonPath("$[3].centresReported").value(2))
                .andExpect(jsonPath("$[4].date").value(TODAY.toString()))
                .andExpect(jsonPath("$[4].totalMeals").value(0));
    }

    @Test
    void trendDefaultsToThirtyDays() throws Exception {
        mvc.perform(get("/api/summary/trend")).andExpect(jsonPath("$.length()").value(30));
    }

    @Test
    void trendRejectsOutOfRangeDays() throws Exception {
        mvc.perform(get("/api/summary/trend").param("days", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/summary/trend").param("days", "366")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/summary/trend").param("days", "abc")).andExpect(status().isBadRequest());
    }
}
