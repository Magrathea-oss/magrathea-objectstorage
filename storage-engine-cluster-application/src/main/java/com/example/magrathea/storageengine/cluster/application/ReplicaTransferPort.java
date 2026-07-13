package com.example.magrathea.storageengine.cluster.application;

import java.util.concurrent.CompletionStage;

/** Outbound application port for a bounded, cancellable direct replica transfer. */
public interface ReplicaTransferPort {
    CompletionStage<TransferResult> stage(TransferRequest request, LocalArtifactPort.Source source);

    /**
     * Supplies the committed member declaration to routing adapters without exposing a transport type
     * to the coordinator. Existing single-peer adapters remain compatible through this default method.
     */
    default CompletionStage<TransferResult> stage(
            ClusterMember target,
            TransferRequest request,
            LocalArtifactPort.Source source) {
        if (!target.identity().equals(request.targetNode())) {
            throw new IllegalArgumentException("transfer target and committed member identity differ");
        }
        return stage(request, source);
    }
}
