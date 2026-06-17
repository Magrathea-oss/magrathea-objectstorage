package com.example.magrathea.storageengine.application.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Testable tracing abstraction used until a concrete OpenTelemetry bridge is introduced.
 */
public record StorageSpan(
        String name,
        Instant occurredAt,
        Duration duration,
        Map<String, String> attributes) {

    public StorageSpan {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
    }
}
