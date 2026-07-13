package com.example.magrathea.storageengine.cluster.application;

import java.util.Arrays;
import java.util.Objects;

/** Durable, checksum-verified result returned by a replica data-plane adapter. */
public record TransferResult(String operationId, String artifactId, NodeIdentity node, long durableLength,
                             byte[] durableSha256, String topologyEpoch, String policyEpoch, boolean idempotentRetry) {
    public TransferResult {
        Objects.requireNonNull(operationId); Objects.requireNonNull(artifactId); Objects.requireNonNull(node);
        Objects.requireNonNull(durableSha256); Objects.requireNonNull(topologyEpoch); Objects.requireNonNull(policyEpoch);
        durableSha256 = Arrays.copyOf(durableSha256, durableSha256.length);
    }
    @Override public byte[] durableSha256() { return Arrays.copyOf(durableSha256, durableSha256.length); }
}
