package com.payment.service;

import com.payment.config.PaymentCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class PaymentCache {
    private static final long FNV_64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_64_PRIME = 0x100000001b3L;

    private final PaymentCacheProperties properties;
    private final ConcurrentHashMap<Long, PaymentState> states = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<CacheEntry> evictionQueue = new ConcurrentLinkedQueue<>();

    public boolean isProcessed(String paymentKey) {
        PaymentState state = getIfPresent(paymentKey);
        return state != null && state.processed();
    }

    public int nextAttempt(String paymentKey) {
        return getOrCreate(paymentKey).nextAttempt();
    }

    public boolean markProcessed(String paymentKey) {
        return getOrCreate(paymentKey).markProcessed();
    }

    private PaymentState getIfPresent(String paymentKey) {
        long now = System.nanoTime();
        long key = fingerprint(paymentKey);
        PaymentState state = states.get(key);
        if (state == null || !state.expired(now)) {
            return state;
        }
        states.remove(key, state);
        return null;
    }

    private PaymentState getOrCreate(String paymentKey) {
        long now = System.nanoTime();
        long expiresAtNanos = now + TimeUnit.SECONDS.toNanos(properties.expireAfterSeconds());
        long key = fingerprint(paymentKey);
        PaymentState state = states.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expired(now)) {
                PaymentState created = new PaymentState(expiresAtNanos);
                evictionQueue.add(new CacheEntry(key, created));
                return created;
            }
            return existing;
        });
        evict(now);
        return state;
    }

    private void evict(long now) {
        while (states.size() > properties.maxSize()) {
            CacheEntry entry = evictionQueue.poll();
            if (entry == null) {
                return;
            }
            states.remove(entry.key(), entry.state());
        }

        CacheEntry entry;
        while ((entry = evictionQueue.peek()) != null && entry.state().expired(now)) {
            evictionQueue.poll();
            states.remove(entry.key(), entry.state());
        }
    }

    private long fingerprint(String value) {
        long hash = FNV_64_OFFSET_BASIS;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash ^= b & 0xffL;
            hash *= FNV_64_PRIME;
        }
        return hash;
    }

    private record CacheEntry(long key, PaymentState state) {
    }

    private static final class PaymentState {
        private final long expiresAtNanos;
        private final AtomicInteger attempts = new AtomicInteger(0);
        private final AtomicBoolean processed = new AtomicBoolean(false);

        private PaymentState(long expiresAtNanos) {
            this.expiresAtNanos = expiresAtNanos;
        }

        private int nextAttempt() {
            return attempts.incrementAndGet();
        }

        private boolean processed() {
            return processed.get();
        }

        private boolean markProcessed() {
            return processed.compareAndSet(false, true);
        }

        private boolean expired(long now) {
            return now >= expiresAtNanos;
        }
    }
}
