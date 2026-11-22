package com.parth.mealtrack.service.anomaly;

import com.parth.mealtrack.config.MealtrackProperties;
import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.domain.Severity;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ExceedsCapacityRule implements AnomalyRule {
    private final MealtrackProperties.Anomaly cfg;

    public ExceedsCapacityRule(MealtrackProperties props) {
        this.cfg = props.anomaly();
    }

    @Override
    public Optional<AnomalyFinding> evaluate(AnomalyContext ctx) {
        double limit = ctx.expectedMeals() * cfg.capacityFactor();
        if (!Thresholds.above(ctx.mealsServed(), limit)) return Optional.empty();
        return Optional.of(new AnomalyFinding(AlertType.EXCEEDS_CAPACITY, Severity.MEDIUM,
                "Reported %d meals; expected is %d (%d%%), limit is %d.".formatted(ctx.mealsServed(),
                        ctx.expectedMeals(), Thresholds.percent(ctx.mealsServed(), ctx.expectedMeals()),
                        Math.round(limit))));
    }
}
