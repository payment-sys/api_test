package com.payment.service;

import com.payment.config.FailedScenario;
import com.payment.config.PaymentMockProperties;
import com.payment.controller.dto.FailedResult;
import com.payment.controller.dto.PaymentPayload;
import com.payment.controller.dto.Result;
import com.payment.controller.dto.SuccessResult;
import com.payment.service.mock.MockLatencyControllerService;
import com.payment.service.mock.MockAttemptPlanner;
import com.payment.service.mock.PlanDecision;
import com.payment.webhook.PaymentWebhookSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApiService {
    private final PaymentCache paymentCache;
    private final PaymentMockProperties mockProperties;
    private final MockAttemptPlanner attemptPlanner;
    private final MockLatencyControllerService mockLatencyControllerService;
    private final PaymentWebhookSender webhookSender;

    private final AtomicInteger totalRequestCounter = new AtomicInteger(0);

    public ResponseEntity<Result> confirmPayment(PaymentPayload paymentPayload) {
        if (!validatePayment(paymentPayload)) {
            log.debug("orderId, paymentKey, amount is invalid. {} {} {}",
                    paymentPayload == null ? null : paymentPayload.orderId(),
                    paymentPayload == null ? null : paymentPayload.paymentKey(),
                    paymentPayload == null ? null : paymentPayload.amount());
            return completedAfter(mockProperties.defaultLatencyMs(),
                    fail(HttpStatus.BAD_REQUEST, paymentPayload == null ? null : paymentPayload.orderId(),
                    "INVALID_REQUEST", "orderId, paymentKey and amount are required."));
        }

        int requestNo = totalRequestCounter.incrementAndGet();
        if (mockProperties.totalCount() > 0 && requestNo > mockProperties.totalCount()) {
            return completedAfter(mockProperties.defaultLatencyMs(),
                    fail(HttpStatus.TOO_MANY_REQUESTS, paymentPayload.orderId(),
                    "TEST_LIMIT_EXCEEDED", "Configured total-count has been exceeded."));
        }

        if (paymentCache.isProcessed(paymentPayload.paymentKey())) {
            log.debug("payment already processed.");
            return completedAfter(mockProperties.defaultLatencyMs(),
                    fail(HttpStatus.CONFLICT, paymentPayload.orderId(),
                    "ALREADY_PROCESSED_PAYMENT", "This payment has already been processed."));
        }

        int attemptNo = paymentCache.nextAttempt(paymentPayload.paymentKey());
        PlanDecision planDecision = attemptPlanner.decide(attemptNo);
        sleep(mockLatencyControllerService.resolve(planDecision.latencyMs()));

        if (planDecision instanceof PlanDecision.Failure failureDecision) {
            return completeFailure(paymentPayload, failureDecision.scenario());
        }

        return completeSuccess(paymentPayload);
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

    private ResponseEntity<Result> completedAfter(long latencyMs, ResponseEntity<Result> response) {
        sleep(mockLatencyControllerService.resolve(latencyMs));
        return response;
    }

    private void sleep(long latencyMs) {
        if (latencyMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment mock latency wait was interrupted.", e);
        }
    }

    private ResponseEntity<Result> completeSuccess(PaymentPayload paymentPayload) {
        paymentCache.markProcessed(paymentPayload.paymentKey());
        SuccessResult successResult = SuccessResult.create(paymentPayload);
        webhookSender.sendDone(paymentPayload, successResult);
        return ResponseEntity.ok(successResult);
    }

    private ResponseEntity<Result> completeFailure(PaymentPayload paymentPayload, FailedScenario failedScenario) {
        if (failedScenario == FailedScenario.NO_RESPONSE) {
            paymentCache.markProcessed(paymentPayload.paymentKey());
            webhookSender.sendDone(paymentPayload, SuccessResult.create(paymentPayload));
            waitIndefinitelyForNoResponse();
        }
        return toFailureResponse(paymentPayload, failedScenario);
    }

    private void waitIndefinitelyForNoResponse() {
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Payment mock no-response wait was interrupted.", e);
            }
        }
    }
}
