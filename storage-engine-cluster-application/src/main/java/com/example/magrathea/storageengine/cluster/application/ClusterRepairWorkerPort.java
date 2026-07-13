package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Mono;

/** Transport-neutral execution boundary used by request coordination. */
public interface ClusterRepairWorkerPort {
    Mono<RepairJob> repairNow(RepairJobId jobId);
    void signalCommittedWork();
}
