package com.payment.service;

import com.payment.config.FailedScenario;
import com.payment.config.PaymentMockProperties;
import com.payment.controller.dto.FailedResult;
import com.payment.controller.dto.PaymentPayload;
import com.payment.controller.dto.Result;
import com.payment.controller.dto.SuccessResult;
import com.payment.service.mock.MockAttemptPlanner;
import com.payment.service.mock.PlanDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class PaymentApiService {
    private final PaymentCache paymentCache;
    private final PaymentMockProperties mockProperties;
    private final MockAttemptPlanner attemptPlanner;

    private final AtomicInteger totalRequestCounter = new AtomicInteger(0);
    private final Map<String, Integer> attemptCounterByPaymentKey = new ConcurrentHashMap<>();

    public ResponseEntity<Result> confirmPayment(PaymentPayload paymentPayload) {
        if (!validatePayment(paymentPayload)) {
            return fail(HttpStatus.BAD_REQUEST, paymentPayload == null ? null : paymentPayload.orderId(),
                    "INVALID_REQUEST", "orderId, paymentKey and amount are required.");
        }

        int requestNo = totalRequestCounter.incrementAndGet();
        if (requestNo > mockProperties.totalCount()) {
            return fail(HttpStatus.TOO_MANY_REQUESTS, paymentPayload.orderId(),
                    "TEST_LIMIT_EXCEEDED", "Configured total-count has been exceeded.");
        }

        PaymentPayload existingPayment = paymentCache.getPayment(paymentPayload.paymentKey());
        if (existingPayment != null) {
            return fail(HttpStatus.CONFLICT, paymentPayload.orderId(),
                    "ALREADY_PROCESSED_PAYMENT", "This payment has already been processed.");
        }

        int attemptNo = attemptCounterByPaymentKey.merge(paymentPayload.paymentKey(), 1, Integer::sum);
        PlanDecision planDecision = attemptPlanner.decide(attemptNo);

        if (planDecision instanceof PlanDecision.Failure failureDecision) {
            sleep(failureDecision.latencyMs());
            if (failureDecision.scenario() == FailedScenario.NO_REQUEST) {
                waitWithoutResponse();
            }
            if (failureDecision.scenario() == FailedScenario.NO_RESPONSE) {
                paymentCache.putPayment(paymentPayload.paymentKey(), paymentPayload);
                waitWithoutResponse();
            }
            return toFailureResponse(paymentPayload, failureDecision.scenario());
        }

        sleep(planDecision.latencyMs());
        paymentCache.putPayment(paymentPayload.paymentKey(), paymentPayload);
        return ResponseEntity.ok(SuccessResult.create(paymentPayload));
    }

    private ResponseEntity<Result> toFailureResponse(PaymentPayload paymentPayload, FailedScenario failedScenario) {
        return switch (failedScenario) {
            case NO_REQUEST -> fail(HttpStatus.SERVICE_UNAVAILABLE, paymentPayload.orderId(),
                    "NO_REQUEST", "Request could not be sent.");
            case NO_RESPONSE -> fail(HttpStatus.GATEWAY_TIMEOUT, paymentPayload.orderId(),
                    "NO_RESPONSE", "No response from upstream.");
            case UPSTREAM_429 -> fail(HttpStatus.TOO_MANY_REQUESTS, paymentPayload.orderId(),
                    "UPSTREAM_429", "Too many requests to upstream.");
            case UPSTREAM_5XX -> fail(HttpStatus.INTERNAL_SERVER_ERROR, paymentPayload.orderId(),
                    "UPSTREAM_5XX", "Upstream internal server error.");
        };
    }

    private boolean validatePayment(PaymentPayload paymentPayload) {
        if (paymentPayload == null) {
            return false;
        }
        if (paymentPayload.orderId() == null || paymentPayload.orderId().isBlank()) {
            return false;
        }
        if (paymentPayload.paymentKey() == null || paymentPayload.paymentKey().isBlank()) {
            return false;
        }
        return paymentPayload.amount() != null && paymentPayload.amount() > 0;
    }

    private ResponseEntity<Result> fail(HttpStatus status, String orderId, String code, String message) {
        return ResponseEntity.status(status).body(new FailedResult(orderId, code, message));
    }

    private void sleep(long latencyMs) {
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {}
    }

    private void waitWithoutResponse() {
        try {
            while (true) {
                Thread.sleep(60_000L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("No-response wait interrupted.", e);
        }
    }
}
