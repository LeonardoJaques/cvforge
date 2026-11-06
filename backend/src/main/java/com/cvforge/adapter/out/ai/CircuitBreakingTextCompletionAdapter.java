package com.cvforge.adapter.out.ai;

import com.cvforge.application.port.out.TextCompletionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Decorator that bounds concurrent provider calls and opens after repeated failures. */
@Component
@Primary
public class CircuitBreakingTextCompletionAdapter implements TextCompletionPort {
    private final TextCompletionPort delegate;
    private final int failureThreshold;
    private final long openMillis;
    private final Semaphore bulkhead;
    private final Clock clock;
    private final AtomicBoolean halfOpenProbe = new AtomicBoolean();

    private int consecutiveFailures;
    private long openedAtMillis;
    private State state = State.CLOSED;

    public CircuitBreakingTextCompletionAdapter(OpenRouterAdapter delegate,
            @Value("${openrouter.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${openrouter.circuit-breaker.open-seconds:30}") long openSeconds,
            @Value("${openrouter.bulkhead.max-concurrent:4}") int maxConcurrent) {
        this(delegate, failureThreshold, Duration.ofSeconds(openSeconds), maxConcurrent, Clock.systemUTC());
    }

    CircuitBreakingTextCompletionAdapter(TextCompletionPort delegate, int failureThreshold,
            Duration openDuration, int maxConcurrent, Clock clock) {
        if (failureThreshold < 1 || maxConcurrent < 1 || openDuration.isNegative()) {
            throw new IllegalArgumentException("Circuit breaker and bulkhead limits must be positive.");
        }
        this.delegate = delegate;
        this.failureThreshold = failureThreshold;
        this.openMillis = openDuration.toMillis();
        this.bulkhead = new Semaphore(maxConcurrent);
        this.clock = clock;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        return invoke(() -> delegate.complete(systemPrompt, userPrompt, maxTokens));
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, int maxTokens, String model) {
        return invoke(() -> delegate.complete(systemPrompt, userPrompt, maxTokens, model));
    }

    private String invoke(CompletionCall call) {
        if (!bulkhead.tryAcquire()) {
            throw new IllegalStateException("O serviço de análise está ocupado. Tente novamente em instantes.");
        }
        boolean probe = false;
        try {
            synchronized (this) {
                if (state == State.OPEN) {
                    if (clock.millis() - openedAtMillis < openMillis) throw circuitOpen();
                    state = State.HALF_OPEN;
                }
                if (state == State.HALF_OPEN) {
                    if (!halfOpenProbe.compareAndSet(false, true)) throw circuitOpen();
                    probe = true;
                }
            }
            String result = call.execute();
            synchronized (this) {
                consecutiveFailures = 0;
                state = State.CLOSED;
            }
            return result;
        } catch (CircuitOpenException e) {
            throw e;
        } catch (RuntimeException e) {
            synchronized (this) {
                consecutiveFailures++;
                if (probe || consecutiveFailures >= failureThreshold) openCircuit();
            }
            throw e;
        } finally {
            if (probe) halfOpenProbe.set(false);
            bulkhead.release();
        }
    }

    private synchronized void openCircuit() {
        state = State.OPEN;
        openedAtMillis = clock.millis();
    }

    private CircuitOpenException circuitOpen() {
        return new CircuitOpenException("A análise está temporariamente indisponível. Tente novamente em instantes.");
    }

    synchronized String stateName() { return state.name(); }

    private enum State { CLOSED, OPEN, HALF_OPEN }
    private static final class CircuitOpenException extends IllegalStateException {
        private CircuitOpenException(String message) { super(message); }
    }
    @FunctionalInterface private interface CompletionCall { String execute(); }
}
