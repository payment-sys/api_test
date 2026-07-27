package com.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.webhook")
public record PaymentWebhookProperties(
        boolean enabled,
        String url,
        long initialDelayMs,
        long retryDelayMs,
        int maxAttempts,
        int maxScheduledEvents,
        int threadCount,
        int connectTimeoutMs,
        int readTimeoutMs
) {
    public PaymentWebhookProperties {
        initialDelayMs = Math.max(0L, initialDelayMs);
        retryDelayMs = retryDelayMs <= 0L ? 1_000L : retryDelayMs;
        maxAttempts = maxAttempts <= 0 ? 1 : maxAttempts;
        maxScheduledEvents = maxScheduledEvents <= 0 ? 1_000 : maxScheduledEvents;
        threadCount = threadCount <= 0 ? 1 : threadCount;
        connectTimeoutMs = connectTimeoutMs <= 0 ? 500 : connectTimeoutMs;
        readTimeoutMs = readTimeoutMs <= 0 ? 1_000 : readTimeoutMs;
    }

    public boolean usable() {
        return enabled && url != null && !url.isBlank();
    }
}
