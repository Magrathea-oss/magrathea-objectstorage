package com.example.magrathea.storageengine.cluster.application;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/** Transport-neutral command for REQ-CLUSTER-011 direct immutable-artifact transfer. */
public record TransferRequest(
        String operationId,
        String artifactId,
        NodeIdentity targetNode,
        long expectedLength,
        byte[] expectedSha256,
        String topologyEpoch,
        String policyEpoch,
        Duration deadline) {
    public TransferRequest {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(targetNode);
        Objects.requireNonNull(expectedSha256);
        Objects.requireNonNull(topologyEpoch);
        Objects.requireNonNull(policyEpoch);
        Objects.requireNonNull(deadline);
        if (expectedLength < 0 || expectedSha256.length != 32 || deadline.isNegative() || deadline.isZero()) {
            throw new IllegalArgumentException("Invalid transfer bounds");
        }
        expectedSha256 = Arrays.copyOf(expectedSha256, expectedSha256.length);
    }

    @Override public byte[] expectedSha256() { return Arrays.copyOf(expectedSha256, expectedSha256.length); }
}
