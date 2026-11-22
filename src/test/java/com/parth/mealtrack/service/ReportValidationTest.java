package com.parth.mealtrack.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.parth.mealtrack.config.MealtrackProperties;
import com.parth.mealtrack.domain.Centre;
import com.parth.mealtrack.domain.CentreType;
import com.parth.mealtrack.repo.CentreRepository;
import com.parth.mealtrack.repo.MealReportRepository;
import com.parth.mealtrack.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class ReportValidationTest {
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    final ReportService service = new ReportService(mock(CentreRepository.class), mock(MealReportRepository.class),
            new MealtrackProperties("k", "http://x", new MealtrackProperties.Validation(7, 2.0), MealtrackProperties.Anomaly.defaults(), MealtrackProperties.Llm.defaults()),
            Clock.fixed(Instant.parse("2026-11-22T06:00:00Z"), IST), mock(AnomalyService.class), mock(PlatformTransactionManager.class));

    final Centre centre = new Centre("C-1", "c", CentreType.SCHOOL, "D", "B", 100);

    @Test void acceptsTodayWithNormalMeals() { assertDoesNotThrow(() -> service.validate(centre, TODAY, 90)); }
    @Test void acceptsZeroMeals() { assertDoesNotThrow(() -> service.validate(centre, TODAY, 0)); }
    @Test void acceptsExactlyDoubleExpected() { assertDoesNotThrow(() -> service.validate(centre, TODAY, 200)); }
    @Test void rejectsAboveDoubleExpected() { assertThrows(ApiException.class, () -> service.validate(centre, TODAY, 201)); }
    @Test void rejectsNegative() { assertThrows(ApiException.class, () -> service.validate(centre, TODAY, -1)); }
    @Test void rejectsTomorrow() { assertThrows(ApiException.class, () -> service.validate(centre, TODAY.plusDays(1), 10)); }
    @Test void acceptsSevenDaysAgo() { assertDoesNotThrow(() -> service.validate(centre, TODAY.minusDays(7), 10)); }
    @Test void rejectsEightDaysAgo() { assertThrows(ApiException.class, () -> service.validate(centre, TODAY.minusDays(8), 10)); }

    @Test
    void rejectsInactiveCentre() {
        centre.setActive(false);
        assertThrows(ApiException.class, () -> service.validate(centre, TODAY, 10));
    }
}
