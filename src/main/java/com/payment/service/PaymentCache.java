package com.payment.service;

import com.payment.controller.dto.PaymentPayload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentCache {
    private final Map<String, PaymentPayload> paymentCache = new ConcurrentHashMap<>();

    public PaymentPayload getPayment(String paymentKey) {
        return paymentCache.get(paymentKey);
    }

    public boolean putPayment(String paymentKey, PaymentPayload paymentPayload) {
        return paymentCache.putIfAbsent(paymentKey, paymentPayload) == null;
    }
}
