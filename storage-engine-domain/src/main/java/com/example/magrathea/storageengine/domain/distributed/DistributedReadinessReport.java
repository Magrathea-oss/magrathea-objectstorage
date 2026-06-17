package com.example.magrathea.storageengine.domain.distributed;

import java.util.List;
import java.util.Objects;

/** Honest status report for modeled distributed-readiness scope. */
public record DistributedReadinessReport(
        String classification,
        List<String> missingCapabilities,
        String objectApiBoundary) {

    public static final String SINGLE_NODE = "single-node";
    public static final String DISTRIBUTED_SIMULATION = "distributed-simulation";
    public static final String DISTRIBUTED_SIMULATION_NOT_READY = "distributed-simulation-not-ready";
    public static final String NETWORKED_MEMBERSHIP = "networked membership";
    public static final String REAL_REPLICATION_JOB_EXECUTION = "real replication job execution";
    public static final String MULTI_NODE_END_TO_END_VALIDATION = "multi-node end-to-end validation";
    public static final String S3_OBJECT_API_BOUNDARY =
            "S3 object behavior exposed only through the S3-compatible API";

    public DistributedReadinessReport {
        requireNonBlank(classification, "classification");
        Objects.requireNonNull(missingCapabilities, "missingCapabilities must not be null");
        requireNonBlank(objectApiBoundary, "objectApiBoundary");
        if ("distributed-production-ready".equals(classification)) {
            throw new IllegalArgumentException("Modeled Phase 6 report must not claim distributed production readiness");
        }
        missingCapabilities = List.copyOf(missingCapabilities);
    }

    public boolean distributedProductionReady() {
        return false;
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
