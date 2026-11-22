package com.parth.mealtrack.seed;

import static org.junit.jupiter.api.Assertions.*;

import com.parth.mealtrack.PostgresTestBase;
import com.parth.mealtrack.domain.AlertStatus;
import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.service.AlertService;
import com.parth.mealtrack.service.SummaryService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Runs the real seed against PostgreSQL and checks what the dashboard will show. */
@SpringBootTest(properties = "mealtrack.seed.reset=true")
@ActiveProfiles("seed")
class SeedDataRunnerTest extends PostgresTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired SummaryService summary;
    @Autowired AlertService alerts;
    @Autowired Clock clock;

    private static JdbcTemplate staticJdbc;

    @Autowired
    void keep(JdbcTemplate j) { staticJdbc = j; }

    @AfterAll
    static void cleanUp() {
        // Do not leave 1,000 centres behind for the other test classes sharing this database.
        staticJdbc.execute("TRUNCATE alert, meal_report, centre RESTART IDENTITY CASCADE");
    }

    private LocalDate demoDay() { return SeedDataGenerator.demoDay(LocalDate.now(clock)); }

    private int count(AlertType type) {
        return alerts.search(AlertStatus.OPEN, type, null, demoDay()).size();
    }

    @Test
    void seedsOneThousandCentresAndThirtyDaysOfReports() {
        assertEquals(1000, jdbc.queryForObject("SELECT count(*) FROM centre", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM meal_report WHERE source <> 'SEED'", Integer.class));
        Integer days = jdbc.queryForObject("SELECT count(DISTINCT report_date) FROM meal_report", Integer.class);
        assertTrue(days >= 25 && days <= 31, "distinct report days: " + days);
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM meal_report WHERE extract(dow from report_date) = 0", Integer.class));
    }

    @Test
    void demoDayShowsTheInjectedAnomaliesThroughTheRealRules() {
        assertEquals(40, count(AlertType.MISSING_REPORT));
        assertEquals(5, count(AlertType.ZERO_MEALS));
        assertEquals(5, count(AlertType.SPIKE));
        // 10 injected drops, plus the 5 zero-meal centres which also read as a drop to 0% of their average
        assertEquals(15, count(AlertType.SUDDEN_DROP));
        assertTrue(count(AlertType.EXCEEDS_CAPACITY) <= 5);
        assertEquals(0, count(AlertType.PARSE_UNCERTAIN));
    }

    @Test
    void someMissingReportAlertsAreHighBecauseThePreviousDayWasMissedToo() {
        List<com.parth.mealtrack.domain.Alert> missing = alerts.search(AlertStatus.OPEN, AlertType.MISSING_REPORT, null, demoDay());
        long high = missing.stream().filter(a -> a.getSeverity() == com.parth.mealtrack.domain.Severity.HIGH).count();
        assertTrue(high >= SeedDataGenerator.DEMO_MISSING_REPEAT, "HIGH missing alerts: " + high);
        assertTrue(high < missing.size());
    }

    @Test
    void demoDaySummaryMatchesTheDashboardNumbers() {
        var s = summary.daily(demoDay());
        assertEquals(1000, s.centresActive());
        assertEquals(960, s.centresReported());
        assertEquals(96.0, s.coveragePercent());
        assertEquals(8, s.byDistrict().size());
        assertTrue(s.openAlerts() >= 40 + 5 + 5 + 15);
    }

    @Test
    void aNormalWeekdayTotalIsNearOneLakhFortyThousand() {
        LocalDate d = demoDay().minusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SUNDAY || d.getDayOfWeek() == DayOfWeek.SATURDAY) d = d.minusDays(1);
        long total = summary.daily(d).totalMeals();
        System.out.println("SEED-MEASURED weekday total for " + d + " (" + d.getDayOfWeek() + "): " + total);
        assertTrue(total > 130_000 && total < 150_000, "weekday total was " + total);
    }
}
