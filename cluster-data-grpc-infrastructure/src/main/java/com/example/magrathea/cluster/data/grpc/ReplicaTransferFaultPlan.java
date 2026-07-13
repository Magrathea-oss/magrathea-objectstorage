package com.example.magrathea.cluster.data.grpc;

import com.example.magrathea.storageengine.cluster.application.TransferRequest;

/**
 * Optional replica-server interception seam.
 *
 * <p>Production composition uses {@link #none()}; deterministic fault behavior is supplied only by
 * the real-process acceptance application on its test classpath.
 */
public interface ReplicaTransferFaultPlan {
    /** May replace the expected transfer metadata before an unpublished sink is opened. */
    default TransferRequest rewrite(TransferRequest request) {
        return request;
    }

    /** Runs before a payload frame is accepted by the local unpublished sink. */
    default void beforePayload(TransferRequest request, long offset, int length)
            throws InterruptedException {
        // Production default deliberately has no effect.
    }

    static ReplicaTransferFaultPlan none() {
        return new ReplicaTransferFaultPlan() { };
    }
}
