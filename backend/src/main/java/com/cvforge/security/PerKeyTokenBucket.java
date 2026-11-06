package com.cvforge.security;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, process-local token bucket keyed by authenticated principal and route. */
final class PerKeyTokenBucket {
    private final int capacity;
    private final long refillPeriodNanos;
    private final int maxKeys;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(128, 0.75f, true);

    PerKeyTokenBucket(int capacity, Duration refillPeriod, int maxKeys) {
        if (capacity < 1 || refillPeriod.isZero() || refillPeriod.isNegative() || maxKeys < 1) {
            throw new IllegalArgumentException("Rate limit values must be positive.");
        }
        this.capacity = capacity;
        this.refillPeriodNanos = refillPeriod.toNanos();
        this.maxKeys = maxKeys;
    }

    synchronized boolean tryAcquire(String key, long nowNanos) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxKeys) buckets.remove(buckets.keySet().iterator().next());
            bucket = new Bucket(capacity, nowNanos);
            buckets.put(key, bucket);
        }
        long elapsed = Math.max(0, nowNanos - bucket.lastRefillNanos);
        if (elapsed >= refillPeriodNanos) {
            long periods = elapsed / refillPeriodNanos;
            bucket.tokens = (int) Math.min(capacity, bucket.tokens + periods);
            bucket.lastRefillNanos += periods * refillPeriodNanos;
        }
        if (bucket.tokens == 0) return false;
        bucket.tokens--;
        return true;
    }

    private static final class Bucket {
        private int tokens;
        private long lastRefillNanos;
        private Bucket(int tokens, long now) { this.tokens = tokens; this.lastRefillNanos = now; }
    }
}
