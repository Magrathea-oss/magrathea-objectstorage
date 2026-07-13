package com.example.magrathea.storageengine.cluster.application;

import java.util.concurrent.CompletionStage;

/** Outbound port for a bounded mutually authenticated replica read into verified repair staging. */
public interface ReplicaReadPort {
    CompletionStage<TransferResult> fetch(
            ClusterMember source,
            TransferRequest request,
            LocalArtifactPort.RepairSink localSink);
}
