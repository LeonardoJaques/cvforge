package com.cvforge.security;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, process-local token bucket keyed by authenticated principal and route. */
final class PerKeyTokenBucket {
    private final int capacity;
    private final int refillTokens;
    private final long refillPeriodNanos;
    private final Stripe[] stripes = new Stripe[32];

    PerKeyTokenBucket(int capacity, int refillTokens, Duration refillPeriod, int maxKeys) {
        if (capacity < 1 || refillTokens < 1 || refillPeriod.isZero() || refillPeriod.isNegative() || maxKeys < 1) {
            throw new IllegalArgumentException("Rate limit values must be positive.");
        }
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = refillPeriod.toNanos();
        int perStripeLimit = (maxKeys + stripes.length - 1) / stripes.length;
        for (int i = 0; i < stripes.length; i++) stripes[i] = new Stripe(perStripeLimit);
    }

    boolean tryAcquire(String key, long nowNanos) {
        Stripe stripe = stripeFor(key);
        synchronized (stripe) {
            Bucket bucket = stripe.buckets.get(key);
            if (bucket == null) {
                if (stripe.buckets.size() >= stripe.maxKeys) stripe.buckets.remove(stripe.buckets.keySet().iterator().next());
                bucket = new Bucket(capacity, nowNanos);
                stripe.buckets.put(key, bucket);
            }
            refill(bucket, nowNanos);
            if (bucket.tokens == 0) return false;
            bucket.tokens--;
            return true;
        }
    }

    long retryAfterSeconds(String key, long nowNanos) {
        Stripe stripe = stripeFor(key);
        synchronized (stripe) {
            Bucket bucket = stripe.buckets.get(key);
            if (bucket == null || bucket.tokens > 0) return 1;
            long elapsed = Math.max(0, nowNanos - bucket.lastRefillNanos);
            long remainder = elapsed % refillPeriodNanos;
            long waitNanos = remainder == 0 ? refillPeriodNanos : refillPeriodNanos - remainder;
            return Math.max(1, 1 + (waitNanos - 1) / 1_000_000_000L);
        }
    }

    private Stripe stripeFor(String key) {
        int hash = key.hashCode();
        hash ^= hash >>> 16;
        return stripes[hash & (stripes.length - 1)];
    }

    private void refill(Bucket bucket, long nowNanos) {
        long elapsed = Math.max(0, nowNanos - bucket.lastRefillNanos);
        if (elapsed < refillPeriodNanos) return;
        long periods = elapsed / refillPeriodNanos;
        long tokensUntilFull = capacity - bucket.tokens;
        long periodsUntilFull = (tokensUntilFull + (long) refillTokens - 1) / refillTokens;
        if (periods >= periodsUntilFull) bucket.tokens = capacity;
        else bucket.tokens += (int) (periods * refillTokens);
        bucket.lastRefillNanos += periods * refillPeriodNanos;
    }

    private static final class Bucket {
        private int tokens;
        private long lastRefillNanos;
        private Bucket(int tokens, long now) { this.tokens = tokens; this.lastRefillNanos = now; }
    }

    private static final class Stripe {
        private final int maxKeys;
        private final Map<String, Bucket> buckets = new LinkedHashMap<>(128, 0.75f, true);
        private Stripe(int maxKeys) { this.maxKeys = maxKeys; }
    }
}
