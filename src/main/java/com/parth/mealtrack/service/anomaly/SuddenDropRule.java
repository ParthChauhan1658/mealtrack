package com.parth.mealtrack.service.anomaly;

import com.parth.mealtrack.config.MealtrackProperties;
import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.domain.Severity;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SuddenDropRule implements AnomalyRule {
    private final MealtrackProperties.Anomaly cfg;

    public SuddenDropRule(MealtrackProperties props) {
        this.cfg = props.anomaly();
    }

    @Override
    public Optional<AnomalyFinding> evaluate(AnomalyContext ctx) {
        if (ctx.history().size() < cfg.minHistoryDays()) return Optional.empty();
        // Zero meals is reported by ZeroMealsRule; a second "drop to 0%" alert would only duplicate it.
        if (ctx.mealsServed() == 0) return Optional.empty();
        double avg = ctx.historyAverage().orElse(0);
        if (avg <= 0 || !Thresholds.below(ctx.mealsServed(), avg * cfg.dropRatio())) return Optional.empty();
        Severity severity = Thresholds.below(ctx.mealsServed(), avg * cfg.severeDropRatio()) ? Severity.HIGH : Severity.MEDIUM;
        return Optional.of(new AnomalyFinding(AlertType.SUDDEN_DROP, severity,
                "Reported %d meals; %d-day average is %d (%d%%).".formatted(ctx.mealsServed(), ctx.history().size(),
                        Math.round(avg), Thresholds.percent(ctx.mealsServed(), avg))));
    }
}
