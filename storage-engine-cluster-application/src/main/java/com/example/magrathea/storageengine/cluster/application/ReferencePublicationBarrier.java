package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Mono;

/**
 * Optional observation barrier after durable acknowledgements and before control-plane publication.
 *
 * <p>The production default is non-blocking. The real-process acceptance application supplies a
 * controllable implementation to make control-quorum loss deterministic without faking replicas.
 */
@FunctionalInterface
public interface ReferencePublicationBarrier {
    Mono<Void> await(PublicationProposal proposal);

    /** Separate bounded observation point for complete EC shard evidence. */
    default Mono<Void> awaitEc(EcPublicationProposal proposal) {
        return Mono.empty();
    }

    static ReferencePublicationBarrier none() {
        return ignored -> Mono.empty();
    }
}
