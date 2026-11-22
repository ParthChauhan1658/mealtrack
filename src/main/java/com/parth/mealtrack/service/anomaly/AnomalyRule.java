package com.parth.mealtrack.service.anomaly;

import java.util.Optional;

/** One anomaly check. Returns at most one finding for the report in the context. */
public interface AnomalyRule {
    Optional<AnomalyFinding> evaluate(AnomalyContext ctx);
}
