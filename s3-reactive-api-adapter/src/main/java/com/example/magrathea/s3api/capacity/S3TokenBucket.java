package com.example.magrathea.s3api.capacity;

import java.time.Clock;

/** Lock-protected, queue-free token bucket with injectable time for deterministic tests. */
public final class S3TokenBucket {
    private final double refillPerNano;
    private final int capacity;
    private final Clock clock;
    private double tokens;
    private long lastRefillMillis;

    public S3TokenBucket(double refillPerSecond, int capacity, Clock clock) {
        this.refillPerNano = refillPerSecond / 1_000_000_000.0;
        this.capacity = capacity;
        this.clock = clock;
        this.tokens = capacity;
        this.lastRefillMillis = clock.millis();
    }

    public synchronized boolean tryConsume() {
        long now = clock.millis();
        long elapsedNanos = Math.max(0, now - lastRefillMillis) * 1_000_000L;
        tokens = Math.min(capacity, tokens + elapsedNanos * refillPerNano);
        lastRefillMillis = now;
        if (tokens < 1.0) return false;
        tokens -= 1.0;
        return true;
    }
}
