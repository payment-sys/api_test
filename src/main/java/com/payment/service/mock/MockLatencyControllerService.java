package com.payment.service.mock;

import com.payment.config.PaymentMockProperties;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MockLatencyControllerService {
    private static final long NO_OVERRIDE = -1L;

    private final long startedAtNanos = System.nanoTime();
    private final List<PaymentMockProperties.LatencyPhase> phases;
    private final AtomicLong overrideLatencyMs = new AtomicLong(NO_OVERRIDE);

    public MockLatencyControllerService(PaymentMockProperties mockProperties) {
        this.phases = mockProperties.latencyPhases().stream()
                .sorted(Comparator.comparingLong(PaymentMockProperties.LatencyPhase::afterSeconds))
                .toList();
    }

    public long resolve(long plannedLatencyMs) {
        long override = overrideLatencyMs.get();
        if (override >= 0L) {
            return override;
        }

        long elapsedSeconds = (System.nanoTime() - startedAtNanos) / 1_000_000_000L;
        long resolved = plannedLatencyMs;
        for (PaymentMockProperties.LatencyPhase phase : phases) {
            if (elapsedSeconds < phase.afterSeconds()) {
                break;
            }
            resolved = phase.latencyMs();
        }
        return Math.max(0L, resolved);
    }

    public long override(long latencyMs) {
        long sanitized = Math.max(0L, latencyMs);
        overrideLatencyMs.set(sanitized);
        return sanitized;
    }

    public long clearOverride() {
        overrideLatencyMs.set(NO_OVERRIDE);
        return resolve(0L);
    }
}
