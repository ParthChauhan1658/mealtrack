package com.parth.mealtrack.service;

import com.parth.mealtrack.config.MealtrackProperties;
import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.domain.MealReport;
import com.parth.mealtrack.repo.AlertRepository;
import com.parth.mealtrack.repo.MealReportRepository;
import com.parth.mealtrack.service.anomaly.AnomalyContext;
import com.parth.mealtrack.service.anomaly.AnomalyFinding;
import com.parth.mealtrack.service.anomaly.AnomalyRule;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnomalyService {
    /** Alert types produced by the per-report rules (as opposed to the missing-report job). */
    private static final Set<AlertType> RULE_TYPES =
            EnumSet.of(AlertType.ZERO_MEALS, AlertType.SUDDEN_DROP, AlertType.SPIKE, AlertType.EXCEEDS_CAPACITY);

    private final List<AnomalyRule> rules;
    private final MealReportRepository reports;
    private final AlertRepository alerts;
    private final int windowDays;

    public AnomalyService(List<AnomalyRule> rules, MealReportRepository reports, AlertRepository alerts,
                          MealtrackProperties props) {
        this.rules = rules;
        this.reports = reports;
        this.alerts = alerts;
        this.windowDays = props.anomaly().windowDays();
    }

    /**
     * Re-evaluates all rules for a saved report. Safe to call again after the value changes:
     * alerts that no longer apply are resolved, current ones are refreshed, and a late report
     * resolves the MISSING_REPORT alert for that day.
     */
    @Transactional
    public List<AnomalyFinding> evaluate(MealReport report) {
        Long centreId = report.getCentre().getId();
        var history = reports.recentMeals(centreId, report.getReportDate(), PageRequest.of(0, windowDays));
        var ctx = new AnomalyContext(report.getCentre().getExpectedMeals(), report.getMealsServed(), history);

        List<AnomalyFinding> findings = rules.stream().map(r -> r.evaluate(ctx)).flatMap(java.util.Optional::stream).toList();

        Set<AlertType> firing = EnumSet.noneOf(AlertType.class);
        for (AnomalyFinding f : findings) {
            firing.add(f.type());
            alerts.upsertOpen(centreId, report.getReportDate(), f.type().name(), f.severity().name(), f.message());
        }
        Set<AlertType> stale = EnumSet.copyOf(RULE_TYPES);
        stale.removeAll(firing);
        alerts.resolveOpen(centreId, report.getReportDate(), stale);
        alerts.resolveOpen(centreId, report.getReportDate(), Set.of(AlertType.MISSING_REPORT));
        return findings;
    }
}
