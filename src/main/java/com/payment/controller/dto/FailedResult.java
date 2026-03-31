package com.payment.controller.dto;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public record FailedResult(
        String orderId,
        String code,
        String message
) implements Result{

    public static FailedResult duplicatePayment(PaymentPayload paymentPayload) {
        return new FailedResult(
                paymentPayload.orderId(),
                "ALREADY_PROCESSED_PAYMENT",
                "이미 처리된 결제입니다."
        );
    }
}
