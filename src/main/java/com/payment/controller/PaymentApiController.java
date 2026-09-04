package com.payment.controller;

import com.payment.controller.dto.PaymentPayload;
import com.payment.controller.dto.Result;
import com.payment.service.PaymentApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentApiController {
    private final PaymentApiService paymentApiService;

    @PostMapping("/v1/payments/confirm")
    public ResponseEntity<Result> confirmPayment(@RequestBody PaymentPayload paymentPayload) {
        return paymentApiService.confirmPayment(paymentPayload);
    }
}
