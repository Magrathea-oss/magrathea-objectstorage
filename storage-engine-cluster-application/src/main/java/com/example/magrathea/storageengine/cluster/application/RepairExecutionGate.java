package com.example.magrathea.storageengine.cluster.application;

import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Transport-neutral repair checkpoint seam; production composition is open at every checkpoint.
 *
 * <p>The single abstract method preserves the original before-claim gate for existing composition.
 * Focused acceptance wiring may additionally pause at a typed side-effect boundary without placing
 * transport, filesystem, consensus, or fault decisions in the worker.
 */
@FunctionalInterface
public interface RepairExecutionGate {
    enum Checkpoint {
        BEFORE_CLAIM_PROPOSED,
        CLAIM_COMMITTED_BEFORE_TRANSFER,
        PAYLOAD_BYTES_STAGED,
        BEFORE_TARGET_PUBLICATION,
        TARGET_DURABLY_PUBLISHED_BEFORE_COMPLETION,
        COMPLETION_COMMITTED_BEFORE_ACKNOWLEDGEMENT
    }

    record Observation(RepairJobId jobId, long claimGeneration, long stagedBytes) {
        public Observation {
            Objects.requireNonNull(jobId, "jobId");
            if (claimGeneration < 0 || stagedBytes < 0) {
                throw new IllegalArgumentException("repair checkpoint counters must not be negative");
            }
        }
    }

    Mono<Void> awaitClaimPermission();

    default Mono<Void> await(Checkpoint checkpoint, Observation observation) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(observation, "observation");
        return checkpoint == Checkpoint.BEFORE_CLAIM_PROPOSED
                ? awaitClaimPermission()
                : Mono.empty();
    }

    default void awaitSynchronously(Checkpoint checkpoint, Observation observation) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(observation, "observation");
        // Production default is explicit no-op; focused tests may override for deterministic pauses.
    }

    static RepairExecutionGate open() {
        return Mono::empty;
    }
}
