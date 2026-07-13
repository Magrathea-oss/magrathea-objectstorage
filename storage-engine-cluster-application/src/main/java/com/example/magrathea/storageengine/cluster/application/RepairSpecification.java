package com.example.magrathea.storageengine.cluster.application;

import java.util.Locale;
import java.util.Objects;

/** Immutable committed facts for one promised whole-object target. */
public record RepairSpecification(
        RepairJobId jobId,
        String bucket,
        String objectKey,
        long referenceGeneration,
        String artifactId,
        NodeIdentity target,
        long length,
        String sha256,
        String topologyEpoch,
        String policyEpoch,
        RepairRetryPolicy retryPolicy) {

    public RepairSpecification {
        Objects.requireNonNull(jobId, "jobId");
        requireText(bucket, "bucket");
        requireText(objectKey, "object key");
        if (referenceGeneration < 1) throw new IllegalArgumentException("reference generation must be positive");
        requireText(artifactId, "artifact ID");
        Objects.requireNonNull(target, "target");
        if (length < 0) throw new IllegalArgumentException("length must not be negative");
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("SHA-256 must contain 64 hexadecimal characters");
        sha256 = sha256.toLowerCase(Locale.ROOT);
        requireText(topologyEpoch, "topology epoch");
        requireText(policyEpoch, "policy epoch");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        RepairJobId expected = RepairJobId.canonical(bucket, objectKey, referenceGeneration, artifactId, target);
        if (!expected.equals(jobId)) throw new IllegalArgumentException("repair job ID is not canonical for its immutable specification");
    }

    public RepairSpecification(
            String bucket, String objectKey, long referenceGeneration, String artifactId,
            NodeIdentity target, long length, String sha256, String topologyEpoch,
            String policyEpoch, RepairRetryPolicy retryPolicy) {
        this(RepairJobId.canonical(bucket, objectKey, referenceGeneration, artifactId, target),
                bucket, objectKey, referenceGeneration, artifactId, target, length, sha256,
                topologyEpoch, policyEpoch, retryPolicy);
    }

    public String namespaceKey() { return bucket + "\u0000" + objectKey; }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
