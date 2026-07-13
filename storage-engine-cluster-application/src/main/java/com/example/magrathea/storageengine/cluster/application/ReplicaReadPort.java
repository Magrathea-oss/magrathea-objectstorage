package com.example.magrathea.storageengine.cluster.application;

import java.util.concurrent.CompletionStage;

/** Outbound port for the single bounded failover fetch into the local immutable store. */
public interface ReplicaReadPort {
    CompletionStage<TransferResult> fetch(
            ClusterMember source,
            TransferRequest request,
            LocalArtifactPort.Sink localSink);
}
