package com.parth.mealtrack.service;

import com.parth.mealtrack.config.MealtrackProperties;
import com.parth.mealtrack.repo.AlertRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class MissingReportService {
    private final AlertRepository alerts;
    private final String cutoff;

    public MissingReportService(AlertRepository alerts, MealtrackProperties props) {
        this.alerts = alerts;
        this.cutoff = props.anomaly().cutoffTime();
    }

    /**
     * Raises MISSING_REPORT for every active centre without a report on {@code date}.
     * Idempotent: running it again creates nothing new.
     *
     * @return number of alerts created by this run
     */
    public int raiseFor(LocalDate date) {
        LocalDate previous = previousWorkingDay(date);
        String first = "No report received by %s IST for %s.".formatted(cutoff, date);
        String repeat = first + " Also missing for the previous working day (%s).".formatted(previous);
        return alerts.insertMissingReportAlerts(date, previous, first, repeat);
    }

    /** Centres do not report on Sundays, so a Monday's "previous day" is the Saturday. */
    static LocalDate previousWorkingDay(LocalDate date) {
        LocalDate d = date.minusDays(1);
        return d.getDayOfWeek() == DayOfWeek.SUNDAY ? d.minusDays(1) : d;
    }
}
