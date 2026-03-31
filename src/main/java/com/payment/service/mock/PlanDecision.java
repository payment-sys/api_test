package com.payment.service.mock;

import com.payment.config.FailedScenario;

public sealed interface PlanDecision permits PlanDecision.Success, PlanDecision.Failure {
    long latencyMs();

    record Success(long latencyMs) implements PlanDecision {
    }

    record Failure(long latencyMs, FailedScenario scenario) implements PlanDecision {
    }
}
