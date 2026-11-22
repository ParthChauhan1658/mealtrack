package com.parth.mealtrack.service.anomaly;

import com.parth.mealtrack.domain.AlertType;
import com.parth.mealtrack.domain.Severity;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ZeroMealsRule implements AnomalyRule {
    @Override
    public Optional<AnomalyFinding> evaluate(AnomalyContext ctx) {
        if (ctx.mealsServed() != 0) return Optional.empty();
        return Optional.of(new AnomalyFinding(AlertType.ZERO_MEALS, Severity.MEDIUM,
                "Reported 0 meals; expected about " + ctx.expectedMeals() + "."));
    }
}
