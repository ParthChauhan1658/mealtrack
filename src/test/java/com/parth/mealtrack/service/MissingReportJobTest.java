package com.parth.mealtrack.service;

import static org.junit.jupiter.api.Assertions.*;

import com.parth.mealtrack.PostgresTestBase;
import com.parth.mealtrack.domain.*;
import com.parth.mealtrack.repo.CentreRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MissingReportJobTest extends PostgresTestBase {
    // A Monday far from "today", so other test classes sharing the database do not interfere.
    static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    @Autowired MissingReportService job;
    @Autowired ReportService reports;
    @Autowired AlertService alerts;
    @Autowired CentreRepository centres;

    private Centre centre(String code, String district, boolean active) {
        Centre c = new Centre(code, code, CentreType.SCHOOL, district, "B", 100);
        c.setActive(active);
        return centres.save(c);
    }

    private List<Alert> missing(String district, LocalDate date) {
        return alerts.search(null, AlertType.MISSING_REPORT, district, date);
    }

    /** Seed-style write that bypasses the 7-day rule, like the seed runner will. */
    private void seedReport(Centre c, LocalDate date) {
        var saved = new MealReport(c, date);
        saved.setMealsServed(90);
        saved.setSource(ReportSource.SEED);
        // saved via repository in the autowired field below
        repo.save(saved);
    }

    @Autowired com.parth.mealtrack.repo.MealReportRepository repo;

    @Test
    void mondayIsMondayAndSundayIsSkippedForPreviousDay() {
        assertEquals(DayOfWeek.MONDAY, MONDAY.getDayOfWeek());
        assertEquals(MONDAY.minusDays(2), MissingReportService.previousWorkingDay(MONDAY));
        assertEquals(MONDAY.plusDays(1).minusDays(1), MissingReportService.previousWorkingDay(MONDAY.plusDays(1)));
    }

    @Test
    void createsLowAlertOnlyForActiveCentresWithoutAReport() {
        Centre missingOne = centre("MJ-1", "MjD1", true);
        Centre reported = centre("MJ-2", "MjD1", true);
        centre("MJ-3", "MjD1", false);
        seedReport(reported, MONDAY);
        seedReport(missingOne, MONDAY.minusDays(2)); // reported the previous working day (Saturday)

        job.raiseFor(MONDAY);

        List<Alert> found = missing("MjD1", MONDAY);
        assertEquals(1, found.size());
        assertEquals("MJ-1", found.get(0).getCentre().getCode());
        assertEquals(Severity.LOW, found.get(0).getSeverity());
        assertEquals(AlertStatus.OPEN, found.get(0).getStatus());
        assertTrue(found.get(0).getMessage().contains("14:00"));
    }

    @Test
    void severityIsHighWhenThePreviousWorkingDayWasAlsoMissed() {
        centre("MJ-4", "MjD2", true); // no reports at all

        job.raiseFor(MONDAY);

        List<Alert> found = missing("MjD2", MONDAY);
        assertEquals(1, found.size());
        assertEquals(Severity.HIGH, found.get(0).getSeverity());
        assertTrue(found.get(0).getMessage().contains(MONDAY.minusDays(2).toString()));
    }

    @Test
    void runningTwiceCreatesNoDuplicates() {
        centre("MJ-5", "MjD3", true);

        job.raiseFor(MONDAY);
        int second = job.raiseFor(MONDAY);

        assertEquals(0, second);
        assertEquals(1, missing("MjD3", MONDAY).size());
    }

    @Test
    void rerunDoesNotReopenAnAlertThatWasResolved() {
        centre("MJ-6", "MjD4", true);
        job.raiseFor(MONDAY);
        alerts.resolve(missing("MjD4", MONDAY).get(0).getId());

        job.raiseFor(MONDAY);

        assertEquals(AlertStatus.RESOLVED, missing("MjD4", MONDAY).get(0).getStatus());
    }

    @Test
    void lateReportResolvesTheMissingAlert() {
        Centre c = centre("MJ-7", "MjD5", true);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        job.raiseFor(today);
        assertEquals(AlertStatus.OPEN, missing("MjD5", today).get(0).getStatus());

        reports.submit("MJ-7", today, 80, ReportSource.API, null, ParseMethod.NONE);

        assertEquals(AlertStatus.RESOLVED, missing("MjD5", today).get(0).getStatus());
    }
}
