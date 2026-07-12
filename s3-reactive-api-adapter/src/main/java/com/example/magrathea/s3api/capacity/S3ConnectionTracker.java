package com.example.magrathea.s3api.capacity;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.netty.Connection;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Process-local physical TCP connection admission for the main S3 listener. */
public final class S3ConnectionTracker {

    public static final String ACTIVE_METRIC = "magrathea.s3.connections.active";
    public static final String REJECTED_METRIC = "magrathea.s3.connections.rejected";

    private static final Logger LOGGER = LoggerFactory.getLogger(S3ConnectionTracker.class);

    private final int limit;
    private final AtomicInteger active = new AtomicInteger();
    private final Counter rejected;

    public S3ConnectionTracker(S3CapacityProperties properties, MeterRegistry registry) {
        this.limit = properties.getMaxTcpConnections();
        this.rejected = Counter.builder(REJECTED_METRIC)
            .description("S3 TCP connections closed because the process-local cap was full")
            .register(registry);
        Gauge.builder(ACTIVE_METRIC, active, AtomicInteger::get)
            .description("Active physical TCP connections on the main S3 listener")
            .register(registry);
    }

    public void connected(Connection connection) {
        if (!tryAcquire()) {
            rejected.increment();
            LOGGER.warn("S3 TCP connection rejected because the process-local cap is full: active={}, limit={}",
                active.get(), limit);
            connection.dispose();
            return;
        }

        AtomicBoolean released = new AtomicBoolean();
        connection.onDispose(() -> {
            if (released.compareAndSet(false, true)) {
                active.decrementAndGet();
            }
        });
    }

    private boolean tryAcquire() {
        while (true) {
            int current = active.get();
            if (current >= limit) {
                return false;
            }
            if (active.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
