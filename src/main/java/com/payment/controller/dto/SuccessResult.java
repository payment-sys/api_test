package com.payment.controller.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record SuccessResult(
        String orderId,
        String paymentKey,
        String status,
        Long totalAmount,
        OffsetDateTime approvedAt,
        Receipt receipt
) implements Result {
    public static Result create(PaymentPayload paymentPayload) {
        return new SuccessResult(
                paymentPayload.orderId(),
                paymentPayload.paymentKey(),
                "OK",
                paymentPayload.amount(),
                LocalDateTime.now().atOffset(ZoneOffset.UTC),
                new Receipt("test")
        );
    }

    public record Receipt(String url) {
    }
}
