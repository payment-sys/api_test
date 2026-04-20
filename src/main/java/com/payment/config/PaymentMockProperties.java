package com.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "payment.mock")
public record PaymentMockProperties(
        int totalCount,
        List<AttemptPlan> plans
) {
    public PaymentMockProperties {
        plans = plans == null ? List.of() : List.copyOf(plans);
    }

    public Map<Integer, AttemptPlan> planByAttempt() {
        return plans.stream()
                .collect(Collectors.toUnmodifiableMap(
                        AttemptPlan::attempt,
                        Function.identity(),
                        (left, right) -> right
                ));
    }

    public long defaultLatencyMs() {
        return plans.stream()
                .filter(plan -> plan.attempt() == 1)
                .findFirst()
                .map(AttemptPlan::latencyMs)
                .orElseGet(() -> plans.stream()
                        .findFirst()
                        .map(AttemptPlan::latencyMs)
                        .orElse(0L));
    }

    public record AttemptPlan(
            int attempt,
            long latencyMs,
            int successCount,
            int failCount,
            List<FailedScenario> failScenarios
    ) {
        public AttemptPlan {
            failScenarios = failScenarios == null ? List.of() : List.copyOf(failScenarios);
        }
    }
}
