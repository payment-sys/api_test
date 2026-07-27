package com.payment.webhook;

import com.payment.controller.dto.PaymentPayload;
import com.payment.controller.dto.SuccessResult;

import java.time.LocalDateTime;

public record PaymentStatusChangedWebhook(
        String eventType,
        LocalDateTime createdAt,
        Data data
) {
    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";

    public static PaymentStatusChangedWebhook done(PaymentPayload paymentPayload, SuccessResult successResult) {
        return new PaymentStatusChangedWebhook(
                PAYMENT_STATUS_CHANGED,
                LocalDateTime.now(),
                new Data(
                        paymentPayload.orderId(),
                        paymentPayload.paymentKey(),
                        successResult.status(),
                        paymentPayload.amount(),
                        successResult.approvedAt(),
                        successResult.receipt()
                )
        );
    }

    public record Data(
            String orderId,
            String paymentKey,
            String status,
            Long totalAmount,
            java.time.OffsetDateTime approvedAt,
            SuccessResult.Receipt receipt
    ) {
    }
}
