package com.payment.service.mock;

import com.payment.config.FailedScenario;
import com.payment.config.PaymentMockProperties;

import java.util.List;

final class AttemptPlanState {
    private int successUsed;
    private int failUsed;
    private int scenarioCursor;

    synchronized PlanDecision decide(PaymentMockProperties.AttemptPlan plan) {
        if (failUsed < Math.max(0, plan.failCount()) && !plan.failScenarios().isEmpty()) {
            FailedScenario scenario = selectScenario(plan.failScenarios());
            failUsed++;
            return new PlanDecision.Failure(plan.latencyMs(), scenario);
        }

        if (successUsed < Math.max(0, plan.successCount())) {
            successUsed++;
            return new PlanDecision.Success(plan.latencyMs());
        }

        return new PlanDecision.Success(plan.latencyMs());
    }

    private FailedScenario selectScenario(List<FailedScenario> scenarios) {
        int index = Math.floorMod(scenarioCursor++, scenarios.size());
        return scenarios.get(index);
    }
}
