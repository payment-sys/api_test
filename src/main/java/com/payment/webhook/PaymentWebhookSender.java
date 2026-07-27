package com.payment.webhook;

import com.payment.config.PaymentWebhookProperties;
import com.payment.controller.dto.PaymentPayload;
import com.payment.controller.dto.SuccessResult;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PaymentWebhookSender {
    private static final String TRANSMISSION_TIME_HEADER = "tosspayments-webhook-transmission-time";
    private static final String TRANSMISSION_RETRIED_COUNT_HEADER = "tosspayments-webhook-transmission-retried-count";
    private static final String TRANSMISSION_ID_HEADER = "tosspayments-webhook-transmission-id";

    private final PaymentWebhookProperties properties;
    private final RestClient restClient;
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicInteger scheduledEvents = new AtomicInteger(0);

    public PaymentWebhookSender(PaymentWebhookProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(properties))
                .build();
        this.executor = new ScheduledThreadPoolExecutor(
                properties.threadCount(),
                new WebhookThreadFactory()
        );
        this.executor.setRemoveOnCancelPolicy(true);
    }

    public void sendDone(PaymentPayload paymentPayload, SuccessResult successResult) {
        if (!properties.usable()) {
            return;
        }
        PaymentStatusChangedWebhook webhook = PaymentStatusChangedWebhook.done(paymentPayload, successResult);
        if (!reserveSlot(paymentPayload)) {
            return;
        }
        schedule(webhook, 1, properties.initialDelayMs(), UUID.randomUUID().toString());
    }

    private boolean reserveSlot(PaymentPayload paymentPayload) {
        int current = scheduledEvents.incrementAndGet();
        if (current <= properties.maxScheduledEvents()) {
            return true;
        }

        scheduledEvents.decrementAndGet();
        log.warn("Payment webhook queue is full. orderId={}, paymentKey={}, maxScheduledEvents={}",
                paymentPayload.orderId(), paymentPayload.paymentKey(), properties.maxScheduledEvents());
        return false;
    }

    private void schedule(PaymentStatusChangedWebhook webhook, int attempt, long delayMs, String transmissionId) {
        try {
            executor.schedule(
                    () -> deliver(webhook, attempt, transmissionId),
                    delayMs,
                    TimeUnit.MILLISECONDS
            );
        } catch (RejectedExecutionException e) {
            scheduledEvents.decrementAndGet();
            log.warn("Payment webhook scheduling rejected. orderId={}, paymentKey={}, attempt={}",
                    webhook.data().orderId(), webhook.data().paymentKey(), attempt, e);
        }
    }

    private void deliver(PaymentStatusChangedWebhook webhook, int attempt, String transmissionId) {
        try {
            restClient.post()
                    .uri(properties.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(TRANSMISSION_TIME_HEADER, OffsetDateTime.now().toString())
                    .header(TRANSMISSION_RETRIED_COUNT_HEADER, String.valueOf(attempt - 1))
                    .header(TRANSMISSION_ID_HEADER, transmissionId)
                    .body(webhook)
                    .retrieve()
                    .toBodilessEntity();

            scheduledEvents.decrementAndGet();
            log.debug("Payment webhook sent. orderId={}, paymentKey={}, status={}, attempt={}",
                    webhook.data().orderId(), webhook.data().paymentKey(), webhook.data().status(), attempt);
        } catch (RuntimeException e) {
            retryOrGiveUp(webhook, attempt, transmissionId, e);
        }
    }

    private void retryOrGiveUp(PaymentStatusChangedWebhook webhook, int attempt, String transmissionId, RuntimeException e) {
        if (attempt >= properties.maxAttempts()) {
            scheduledEvents.decrementAndGet();
            log.warn("Payment webhook failed permanently. orderId={}, paymentKey={}, attempt={}",
                    webhook.data().orderId(), webhook.data().paymentKey(), attempt, e);
            return;
        }

        log.warn("Payment webhook failed. orderId={}, paymentKey={}, attempt={}, nextDelayMs={}",
                webhook.data().orderId(), webhook.data().paymentKey(), attempt, properties.retryDelayMs());
        schedule(webhook, attempt + 1, properties.retryDelayMs(), transmissionId);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    private SimpleClientHttpRequestFactory requestFactory(PaymentWebhookProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        return requestFactory;
    }

    private static final class WebhookThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("payment-webhook-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
