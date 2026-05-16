package com.payment.controller;

import com.payment.controller.dto.PaymentPayload;
import com.payment.controller.dto.Result;
import com.payment.service.PaymentApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentApiController {
    private final PaymentApiService paymentApiService;

    @PostMapping("/v1/payments/confirm")
    public ResponseEntity<Result> confirmPayment(@RequestBody PaymentPayload paymentPayload) {
        long startedAt = System.nanoTime();
        try {
            return paymentApiService.confirmPayment(paymentPayload);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            log.info("Payment confirm completed. orderId={}, paymentKey={}, elapsedMs={}",
                    paymentPayload == null ? null : paymentPayload.orderId(),
                    paymentPayload == null ? null : paymentPayload.paymentKey(),
                    elapsedMs);
        }
    }
}
