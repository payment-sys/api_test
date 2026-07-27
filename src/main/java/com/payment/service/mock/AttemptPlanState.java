package com.payment.service.mock;

import com.payment.config.FailedScenario;
import com.payment.config.PaymentMockProperties;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class AttemptPlanState {
    private final AtomicInteger successUsed = new AtomicInteger(0);
    private final AtomicInteger failUsed = new AtomicInteger(0);
    private final AtomicInteger scenarioCursor = new AtomicInteger(0);

    PlanDecision decide(PaymentMockProperties.AttemptPlan plan) {
        if (!plan.failScenarios().isEmpty() && claim(failUsed, Math.max(0, plan.failCount()))) {
            FailedScenario scenario = selectScenario(plan.failScenarios());
            return new PlanDecision.Failure(plan.latencyMs(), scenario);
        }

        if (claim(successUsed, Math.max(0, plan.successCount()))) {
            return new PlanDecision.Success(plan.latencyMs());
        }

        return new PlanDecision.Success(plan.latencyMs());
    }

    private boolean claim(AtomicInteger counter, int limit) {
        while (true) {
            int current = counter.get();
            if (current >= limit) {
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private FailedScenario selectScenario(List<FailedScenario> scenarios) {
        int index = Math.floorMod(scenarioCursor.getAndIncrement(), scenarios.size());
        return scenarios.get(index);
    }
}
