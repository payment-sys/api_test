package com.payment.service.mock;

import com.payment.config.PaymentMockProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class MockAttemptPlanner {
    private final PaymentMockProperties mockProperties;
    private final Map<Integer, AttemptPlanState> stateByAttempt = new ConcurrentHashMap<>();

    public PlanDecision decide(int attemptNo) {
        PaymentMockProperties.AttemptPlan plan = mockProperties.planByAttempt().get(attemptNo);
        if (plan == null) {
            return new PlanDecision.Success(0L);
        }
        AttemptPlanState state = stateByAttempt.computeIfAbsent(attemptNo, key -> new AttemptPlanState());
        return state.decide(plan);
    }
}
