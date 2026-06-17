package com.example.magrathea.storageengine.domain.distributed;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Explicit write or read quorum outcome for the modeled distributed domain. */
public record QuorumDecision(
        String decision,
        List<String> acknowledgedNodes,
        List<String> missingNodes,
        int requiredQuorum,
        int acknowledgementCount,
        boolean mayPublishManifest,
        Optional<String> failureReason,
        int validReplicaCount,
        List<String> corruptedNodes,
        boolean objectBytesMayBeReturned,
        List<IntegrityFinding> integrityFindings) {

    public static final String QUORUM_MET = "quorum-met";
    public static final String QUORUM_NOT_MET = "quorum-not-met";
    public static final String INTEGRITY_QUORUM_NOT_MET = "integrity-quorum-not-met";

    public QuorumDecision {
        requireNonBlank(decision, "decision");
        Objects.requireNonNull(acknowledgedNodes, "acknowledgedNodes must not be null");
        Objects.requireNonNull(missingNodes, "missingNodes must not be null");
        Objects.requireNonNull(failureReason, "failureReason must not be null");
        Objects.requireNonNull(corruptedNodes, "corruptedNodes must not be null");
        Objects.requireNonNull(integrityFindings, "integrityFindings must not be null");
        if (requiredQuorum < 1) {
            throw new IllegalArgumentException("requiredQuorum must be positive");
        }
        if (acknowledgementCount < 0 || validReplicaCount < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
        acknowledgedNodes = List.copyOf(acknowledgedNodes);
        missingNodes = List.copyOf(missingNodes);
        corruptedNodes = List.copyOf(corruptedNodes);
        integrityFindings = List.copyOf(integrityFindings);
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
