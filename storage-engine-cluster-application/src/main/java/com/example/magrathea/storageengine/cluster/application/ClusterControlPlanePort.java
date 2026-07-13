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

    /** Ensures one canonical job after consensus validates the exact current reference and target. */
    default Mono<RepairCommandResult> ensureRepair(RepairCommands.Ensure command) {
        return Mono.error(new UnsupportedOperationException("repair control is not implemented"));
    }

    default Mono<RepairJob> repairJob(RepairJobId jobId) {
        return Mono.error(new UnsupportedOperationException("repair control is not implemented"));
    }

    default Flux<RepairJob> repairJobs(RepairJobQuery query) {
        return Flux.error(new UnsupportedOperationException("repair control is not implemented"));
    }

    default Mono<RepairCommandResult> claimRepair(RepairCommands.Claim command) {
        return Mono.error(new UnsupportedOperationException("repair control is not implemented"));
    }

    default Mono<RepairCommandResult> renewRepair(RepairCommands.Renew command) {
        return Mono.error(new UnsupportedOperationException("repair control is not implemented"));
    }

    default Mono<RepairCommandResult> retryRepair(RepairCommands.Retry command) {
        return Mono.error(new UnsupportedOperationException("repair control is not implemented"));
    }

    default Mono<RepairCommandResult> blockRepair(RepairCommands.Block command) {
        return Mono.error(new UnsupportedOperationException("repair control is not implemented"));
    }

    default Mono<RepairCommandResult> succeedRepair(RepairCommands.Succeed command) {
        return Mono.error(new UnsupportedOperationException("repair control is not implemented"));
    }

    default Mono<RepairCommandResult> obsoleteRepair(RepairCommands.Obsolete command) {
        return Mono.error(new UnsupportedOperationException("repair control is not implemented"));
    }

    default Mono<RepairCommandResult> reevaluateRepair(RepairCommands.Reevaluate command) {
        return Mono.error(new UnsupportedOperationException("repair control is not implemented"));
    }
}
