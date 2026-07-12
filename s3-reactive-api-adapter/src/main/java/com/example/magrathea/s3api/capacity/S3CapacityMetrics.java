package com.example.magrathea.s3api.capacity;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicInteger;

/** Capacity telemetry with operation/reason tags from fixed vocabularies only. */
public final class S3CapacityMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger active = new AtomicInteger();

    public S3CapacityMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("magrathea.s3.requests.active", active, AtomicInteger::get)
            .description("Currently admitted S3 requests")
            .register(registry);
    }

    public void admitted(String operation) {
        active.incrementAndGet();
        counter("magrathea.s3.requests", operation, "accepted").increment();
    }

    public void completed() { active.decrementAndGet(); }
    public void concurrencyRejected(String operation) { counter("magrathea.s3.rejections", operation, "concurrency").increment(); }
    public void rateRejected(String operation) { counter("magrathea.s3.rejections", operation, "rate_limit").increment(); }
    public void entityTooLarge(String operation) { counter("magrathea.s3.rejections", operation, "entity_too_large").increment(); }
    public void timedOut(String operation) { counter("magrathea.s3.requests", operation, "timeout").increment(); }

    private Counter counter(String name, String operation, String outcome) {
        return Counter.builder(name)
            .tag("operation", operation)
            .tag("outcome", outcome)
            .register(registry);
    }
}
