package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Reactive application port for consensus-owned cluster metadata. */
public interface ClusterControlPlanePort {
    Mono<MembershipSnapshot> membership();

    Mono<BucketNamespace> createBucket(String bucket);

    Mono<BucketNamespace> bucket(String bucket);

    default Flux<BucketNamespace> buckets() {
        return Flux.error(new UnsupportedOperationException("bucket listing is not implemented"));
    }

    Mono<ObjectReferenceGeneration> compareAndPublish(PublicationProposal proposal);

    Mono<ObjectReferenceGeneration> objectReference(String bucket, String objectKey);
}
