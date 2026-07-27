package com.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.cache")
public record PaymentCacheProperties(
        long expireAfterSeconds,
        int maxSize
) {
    public PaymentCacheProperties {
        expireAfterSeconds = expireAfterSeconds <= 0L ? 60L : expireAfterSeconds;
        maxSize = maxSize <= 0 ? 100_000 : maxSize;
    }
}
