package com.example.magrathea.storageengine.domain.distributed;

import java.util.Objects;
import java.util.Optional;

/** Replica observation used by pure quorum and anti-entropy decisions. */
public record ReplicaObservation(String nodeId, ReplicaObservationStatus status, Optional<String> checksum) {

    public ReplicaObservation {
        requireNonBlank(nodeId, "nodeId");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(checksum, "checksum must not be null");
        checksum = checksum.filter(value -> !value.isBlank());
    }

    public static ReplicaObservation verified(String nodeId, String checksum) {
        return new ReplicaObservation(nodeId, ReplicaObservationStatus.VERIFIED, Optional.of(checksum));
    }

    public static ReplicaObservation corrupt(String nodeId, String checksum) {
        return new ReplicaObservation(nodeId, ReplicaObservationStatus.CORRUPT, Optional.of(checksum));
    }

    public static ReplicaObservation missing(String nodeId) {
        return new ReplicaObservation(nodeId, ReplicaObservationStatus.MISSING, Optional.empty());
    }

    public static ReplicaObservation unavailable(String nodeId) {
        return new ReplicaObservation(nodeId, ReplicaObservationStatus.UNAVAILABLE, Optional.empty());
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
