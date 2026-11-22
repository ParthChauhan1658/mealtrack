package com.parth.mealtrack.seed;

import static org.junit.jupiter.api.Assertions.*;

import com.parth.mealtrack.domain.CentreType;
import com.parth.mealtrack.seed.SeedDataGenerator.Plan;
import com.parth.mealtrack.seed.SeedDataGenerator.ReportSpec;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SeedDataGeneratorTest {
    static final LocalDate TUESDAY = LocalDate.of(2026, 11, 24);
    static final Plan PLAN = SeedDataGenerator.generate(TUESDAY, 42);

    private long total(LocalDate day) {
        return PLAN.reports().stream().filter(r -> r.date().equals(day)).mapToLong(ReportSpec::meals).sum();
    }

    @Test
    void createsAThousandCentresSplit60To40WithUniqueCodes() {
        var centres = PLAN.centres();
        assertEquals(1000, centres.size());
        assertEquals(1000, centres.stream().map(c -> c.code()).distinct().count());
        assertEquals(600, centres.stream().filter(c -> c.type() == CentreType.SCHOOL).count());
        assertEquals(8, centres.stream().map(c -> c.district()).distinct().count());
    }

    @Test
    void expectedMealsStayInTheSpecifiedRanges() {
        for (var c : PLAN.centres()) {
            if (c.type() == CentreType.SCHOOL) assertTrue(c.expectedMeals() >= 80 && c.expectedMeals() <= 400, c.code());
            else assertTrue(c.expectedMeals() >= 25 && c.expectedMeals() <= 60, c.code());
        }
    }

    @Test
    void sameSeedGivesIdenticalDataAndAnotherSeedDoesNot() {
        assertEquals(PLAN, SeedDataGenerator.generate(TUESDAY, 42));
        assertNotEquals(PLAN.reports(), SeedDataGenerator.generate(TUESDAY, 43).reports());
    }

    @Test
    void thereAreNoSundayReports() {
        assertTrue(PLAN.reports().stream().noneMatch(r -> r.date().getDayOfWeek() == DayOfWeek.SUNDAY));
    }

    @Test
    void historyCoversThirtyDaysBeforeTheDemoDay() {
        var days = PLAN.reports().stream().map(ReportSpec::date).collect(Collectors.toSet());
        assertTrue(days.contains(TUESDAY.minusDays(29))); // day 30 back is a Sunday
        assertTrue(days.contains(TUESDAY));
        assertTrue(days.stream().noneMatch(d -> d.isAfter(TUESDAY) || d.isBefore(TUESDAY.minusDays(30))));
    }

    @Test
    void normalWeekdayTotalIsNearOneLakhFortyThousand() {
        LocalDate normalWednesday = TUESDAY.minusDays(6);
        assertEquals(DayOfWeek.WEDNESDAY, normalWednesday.getDayOfWeek());
        long total = total(normalWednesday);
        assertTrue(total > 130_000 && total < 150_000, "weekday total was " + total);
    }

    @Test
    void saturdaysAreLowerThanWeekdays() {
        LocalDate saturday = TUESDAY.minusDays(3);
        assertEquals(DayOfWeek.SATURDAY, saturday.getDayOfWeek());
        assertTrue(total(saturday) < total(TUESDAY.minusDays(6)) * 0.9);
    }

    @Test
    void aboutThreePercentOfHistoricalReportsAreSkipped() {
        LocalDate wed = TUESDAY.minusDays(6);
        long reported = PLAN.reports().stream().filter(r -> r.date().equals(wed)).count();
        double skipped = 1 - reported / 1000.0;
        assertTrue(skipped > 0.015 && skipped < 0.05, "skip rate was " + skipped);
    }

    @Test
    void demoDayHasTheInjectedAnomalyGroupsAndTheyAreDisjoint() {
        assertEquals(40, PLAN.missing().size());
        assertEquals(10, PLAN.drops().size());
        assertEquals(5, PLAN.spikes().size());
        assertEquals(5, PLAN.zeros().size());
        Set<Integer> all = new HashSet<>();
        for (var group : List.of(PLAN.missing(), PLAN.drops(), PLAN.spikes(), PLAN.zeros())) all.addAll(group);
        assertEquals(60, all.size());

        var today = PLAN.reports().stream().filter(r -> r.date().equals(TUESDAY)).toList();
        assertEquals(960, today.size()); // exactly the 40 missing centres have no report on the demo day
        assertTrue(today.stream().noneMatch(r -> PLAN.missing().contains(r.centreIdx())));
        for (int z : PLAN.zeros()) {
            assertEquals(0, today.stream().filter(r -> r.centreIdx() == z).findFirst().orElseThrow().meals());
        }
    }

    @Test
    void injectedDropsAndSpikesFallOutsideTheNormalBand() {
        var today = PLAN.reports().stream().filter(r -> r.date().equals(TUESDAY)).toList();
        for (int i : PLAN.drops()) {
            int meals = today.stream().filter(r -> r.centreIdx() == i).findFirst().orElseThrow().meals();
            assertTrue(meals < PLAN.centres().get(i).expectedMeals() * 0.65, "drop " + i + " = " + meals);
        }
        for (int i : PLAN.spikes()) {
            int meals = today.stream().filter(r -> r.centreIdx() == i).findFirst().orElseThrow().meals();
            assertTrue(meals > PLAN.centres().get(i).expectedMeals() * 1.2, "spike " + i + " = " + meals);
        }
    }

    @Test
    void someMissingCentresAlsoMissedThePreviousWorkingDay() {
        LocalDate monday = TUESDAY.minusDays(1);
        Set<Integer> reportedMonday = PLAN.reports().stream().filter(r -> r.date().equals(monday))
                .map(ReportSpec::centreIdx).collect(Collectors.toSet());
        long missingBoth = PLAN.missing().stream().filter(i -> !reportedMonday.contains(i)).count();
        assertTrue(missingBoth >= SeedDataGenerator.DEMO_MISSING_REPEAT);
    }

    @Test
    void sundayTodayUsesSaturdayAsTheDemoDay() {
        assertEquals(LocalDate.of(2026, 11, 21), SeedDataGenerator.demoDay(LocalDate.of(2026, 11, 22)));
        assertEquals(TUESDAY, SeedDataGenerator.demoDay(TUESDAY));
    }

    @Test
    void sundayTodayStillProducesADemoDayWithReports() {
        Plan p = SeedDataGenerator.generate(LocalDate.of(2026, 11, 22), 42);
        assertEquals(DayOfWeek.SATURDAY, p.demoDay().getDayOfWeek());
        assertEquals(960, p.reports().stream().filter(r -> r.date().equals(p.demoDay())).count());
    }
}
