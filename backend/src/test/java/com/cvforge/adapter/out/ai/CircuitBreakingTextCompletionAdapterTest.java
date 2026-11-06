package com.cvforge.adapter.out.ai;

import com.cvforge.application.port.out.TextCompletionPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakingTextCompletionAdapterTest {
    @Test
    void opensAfterThresholdAndAllowsOnlyOneHalfOpenProbe() {
        AtomicInteger calls = new AtomicInteger();
        MutableClock clock = new MutableClock();
        TextCompletionPort failing = new TextCompletionPort() {
            @Override public String complete(String system, String user, int tokens) {
                calls.incrementAndGet();
                throw new IllegalStateException("provider unavailable");
            }
            @Override public String complete(String system, String user, int tokens, String model) {
                return complete(system, user, tokens);
            }
        };
        CircuitBreakingTextCompletionAdapter breaker = new CircuitBreakingTextCompletionAdapter(failing, 2,
                Duration.ofSeconds(30), 1, clock);

        assertThrows(IllegalStateException.class, () -> breaker.complete("s", "u", 10));
        assertThrows(IllegalStateException.class, () -> breaker.complete("s", "u", 10));
        assertEquals("OPEN", breaker.stateName());
        assertThrows(IllegalStateException.class, () -> breaker.complete("s", "u", 10));
        assertEquals(2, calls.get());

        clock.advance(Duration.ofSeconds(31));
        assertThrows(IllegalStateException.class, () -> breaker.complete("s", "u", 10));
        assertEquals("OPEN", breaker.stateName());
        assertEquals(3, calls.get());
    }

    @Test
    void successResetsTheCircuit() {
        AtomicInteger calls = new AtomicInteger();
        TextCompletionPort eventuallyHealthy = new TextCompletionPort() {
            @Override public String complete(String system, String user, int tokens) {
                if (calls.incrementAndGet() == 1) throw new IllegalStateException("temporary");
                return "ok";
            }
            @Override public String complete(String system, String user, int tokens, String model) {
                return complete(system, user, tokens);
            }
        };
        CircuitBreakingTextCompletionAdapter breaker = new CircuitBreakingTextCompletionAdapter(eventuallyHealthy,
                1, Duration.ZERO, 1, Clock.systemUTC());

        assertThrows(IllegalStateException.class, () -> breaker.complete("s", "u", 10));
        assertEquals("ok", breaker.complete("s", "u", 10));
        assertEquals("CLOSED", breaker.stateName());
    }

    @Test
    void staleInFlightSuccessCannotCloseCircuitOpenedByAnotherCall() throws Exception {
        CountDownLatch slowCallStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowCall = new CountDownLatch(1);
        TextCompletionPort provider = new TextCompletionPort() {
            @Override public String complete(String system, String user, int tokens) {
                if ("slow".equals(user)) {
                    slowCallStarted.countDown();
                    try {
                        if (!releaseSlowCall.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timed out");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return "ok";
                }
                throw new IllegalStateException("provider unavailable");
            }
            @Override public String complete(String system, String user, int tokens, String model) {
                return complete(system, user, tokens);
            }
        };
        CircuitBreakingTextCompletionAdapter breaker = new CircuitBreakingTextCompletionAdapter(provider, 1,
                Duration.ofSeconds(30), 2, Clock.systemUTC());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> slowSuccess = executor.submit(() -> breaker.complete("s", "slow", 10));
            assertTrue(slowCallStarted.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> breaker.complete("s", "fail", 10));
            assertEquals("OPEN", breaker.stateName());

            releaseSlowCall.countDown();
            assertEquals("ok", slowSuccess.get(5, TimeUnit.SECONDS));
            assertEquals("OPEN", breaker.stateName());
        } finally {
            releaseSlowCall.countDown();
            executor.shutdownNow();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
