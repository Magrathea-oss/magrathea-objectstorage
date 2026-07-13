package com.example.magrathea.cluster.data.grpc;

import java.time.Duration;

/** Finite resource and retry limits for one internal replica-data endpoint. */
public record ReplicaTransferLimits(int queueCapacity, int inFlightFrames, int maxFrameBytes,
                                    int maxInboundMessageBytes, int maxRetryCount,
                                    Duration maximumRpcDeadline, Duration idleTimeout) {
    public ReplicaTransferLimits {
        if (queueCapacity < 1 || inFlightFrames < 1 || maxFrameBytes < 1 || maxInboundMessageBytes < maxFrameBytes
                || maxRetryCount < 0 || maximumRpcDeadline.isNegative() || maximumRpcDeadline.isZero()
                || idleTimeout.isNegative() || idleTimeout.isZero()) throw new IllegalArgumentException("all transfer limits must be finite and positive");
    }
    public static ReplicaTransferLimits defaults() {
        return new ReplicaTransferLimits(1,1,65_536,1_048_576,1,Duration.ofMinutes(5),Duration.ofSeconds(30));
    }
}
