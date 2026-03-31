package com.payment.controller.dto;

public record PaymentPayload(
        String orderId,
        String paymentKey,
        Long amount
) {
}
