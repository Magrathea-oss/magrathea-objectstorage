package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Mono;

/** Test seam checked immediately before a worker proposes a claim; production is always open. */
@FunctionalInterface
public interface RepairExecutionGate {
    Mono<Void> awaitClaimPermission();

    static RepairExecutionGate open() {
        return Mono::empty;
    }
}
