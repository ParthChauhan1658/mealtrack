package com.parth.mealtrack.service;

import com.parth.mealtrack.domain.AlertStatus;
import com.parth.mealtrack.repo.AlertRepository;
import com.parth.mealtrack.repo.CentreRepository;
import com.parth.mealtrack.repo.DistrictRow;
import com.parth.mealtrack.repo.MealReportRepository;
import com.parth.mealtrack.repo.TrendRow;
import com.parth.mealtrack.web.ApiException;
import com.parth.mealtrack.web.SummaryDtos.DailySummary;
import com.parth.mealtrack.web.SummaryDtos.DistrictSummary;
import com.parth.mealtrack.web.SummaryDtos.TrendPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SummaryService {
    static final int MAX_TREND_DAYS = 365;

    private final CentreRepository centres;
    private final MealReportRepository reports;
    private final AlertRepository alerts;
    private final Clock clock;

    public SummaryService(CentreRepository centres, MealReportRepository reports, AlertRepository alerts, Clock clock) {
        this.centres = centres;
        this.reports = reports;
        this.alerts = alerts;
        this.clock = clock;
    }

    /** Totals are over active centres. openAlerts counts every alert still open, whatever its date. */
    public DailySummary daily(LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock);
        List<DistrictRow> rows = centres.districtSummary(day);
        long meals = 0, reported = 0, active = 0;
        List<DistrictSummary> byDistrict = new ArrayList<>();
        for (DistrictRow r : rows) {
            meals += r.getMeals();
            reported += r.getReported();
            active += r.getTotal();
            byDistrict.add(new DistrictSummary(r.getDistrict(), r.getMeals(), r.getReported(), r.getTotal()));
        }
        return new DailySummary(day, meals, active, reported, coveragePercent(reported, active),
                alerts.countByStatus(AlertStatus.OPEN), byDistrict);
    }

    /** One point per calendar day ending today, oldest first. Days without reports (Sundays) are zero. */
    public List<TrendPoint> trend(int days) {
        if (days < 1 || days > MAX_TREND_DAYS) {
            throw ApiException.badRequest("days must be between 1 and " + MAX_TREND_DAYS);
        }
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays(days - 1L);
        Map<LocalDate, TrendRow> byDate = new HashMap<>();
        for (TrendRow r : reports.dailyTotals(from, to)) byDate.put(r.getDate(), r);

        List<TrendPoint> points = new ArrayList<>(days);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            TrendRow r = byDate.get(d);
            points.add(new TrendPoint(d, r == null ? 0 : r.getMeals(), r == null ? 0 : r.getReported()));
        }
        return points;
    }

    /** Percentage to one decimal place; 0 when there are no active centres. */
    static double coveragePercent(long reported, long active) {
        if (active == 0) return 0.0;
        return BigDecimal.valueOf(reported * 100.0 / active).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
