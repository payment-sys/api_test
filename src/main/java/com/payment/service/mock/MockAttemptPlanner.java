package com.payment.service.mock;

import com.payment.config.PaymentMockProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockAttemptPlanner {
    private final Map<Integer, PaymentMockProperties.AttemptPlan> planByAttempt;
    private final Map<Integer, AttemptPlanState> stateByAttempt = new ConcurrentHashMap<>();

    public MockAttemptPlanner(PaymentMockProperties mockProperties) {
        this.planByAttempt = mockProperties.planByAttempt();
    }

    public PlanDecision decide(int attemptNo) {
        PaymentMockProperties.AttemptPlan plan = planByAttempt.get(attemptNo);
        if (plan == null) {
            return new PlanDecision.Success(0L);
        }
        AttemptPlanState state = stateByAttempt.computeIfAbsent(attemptNo, key -> new AttemptPlanState());
        return state.decide(plan);
    }
}
