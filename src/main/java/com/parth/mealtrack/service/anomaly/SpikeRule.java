package com.parth.mealtrack.service.anomaly;

import com.parth.mealtrack.config.MealtrackProperties;
import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.domain.Severity;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SpikeRule implements AnomalyRule {
    private final MealtrackProperties.Anomaly cfg;

    public SpikeRule(MealtrackProperties props) {
        this.cfg = props.anomaly();
    }

    @Override
    public Optional<AnomalyFinding> evaluate(AnomalyContext ctx) {
        if (ctx.history().size() < cfg.minHistoryDays()) return Optional.empty();
        double avg = ctx.historyAverage().orElse(0);
        if (avg <= 0 || !Thresholds.above(ctx.mealsServed(), avg * cfg.spikeRatio())) return Optional.empty();
        return Optional.of(new AnomalyFinding(AlertType.SPIKE, Severity.MEDIUM,
                "Reported %d meals; %d-day average is %d (%d%%).".formatted(ctx.mealsServed(), ctx.history().size(),
                        Math.round(avg), Thresholds.percent(ctx.mealsServed(), avg))));
    }
}
