package com.example.magrathea.storageengine.domain.distributed;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure quorum policy for modeled write and read decisions. */
public class QuorumPolicy {

    public QuorumDecision evaluateWriteQuorum(
            String manifestId,
            List<String> plannedNodeIds,
            List<String> acknowledgedNodeIds,
            int writeQuorum) {
        requireNonBlank(manifestId, "manifestId");
        Objects.requireNonNull(plannedNodeIds, "plannedNodeIds must not be null");
        Objects.requireNonNull(acknowledgedNodeIds, "acknowledgedNodeIds must not be null");
        requirePositive(writeQuorum, "writeQuorum");

        List<String> planned = plannedNodeIds.stream().sorted().toList();
        Set<String> acknowledgedSet = new HashSet<>(acknowledgedNodeIds);
        List<String> acknowledged = planned.stream().filter(acknowledgedSet::contains).toList();
        List<String> missing = planned.stream().filter(nodeId -> !acknowledgedSet.contains(nodeId)).toList();
        boolean met = acknowledged.size() >= writeQuorum;

        return new QuorumDecision(
                met ? QuorumDecision.QUORUM_MET : QuorumDecision.QUORUM_NOT_MET,
                acknowledged,
                missing,
                writeQuorum,
                acknowledged.size(),
                met,
                met ? Optional.empty() : Optional.of(
                        "required write quorum " + writeQuorum + " but acknowledgement count " + acknowledged.size()),
                0,
                List.of(),
                false,
                List.of());
    }

    public QuorumDecision evaluateReadQuorum(
            String bucket,
            String key,
            String manifestId,
            String expectedChecksum,
            List<ReplicaObservation> observations,
            int readQuorum) {
        requireNonBlank(bucket, "bucket");
        requireNonBlank(key, "key");
        requireNonBlank(manifestId, "manifestId");
        requireNonBlank(expectedChecksum, "expectedChecksum");
        Objects.requireNonNull(observations, "observations must not be null");
        requirePositive(readQuorum, "readQuorum");

        List<ReplicaObservation> ordered = observations.stream()
                .map(observation -> Objects.requireNonNull(observation, "observations must not contain null elements"))
                .sorted(Comparator.comparing(ReplicaObservation::nodeId))
                .toList();

        List<String> valid = ordered.stream()
                .filter(observation -> observation.status() == ReplicaObservationStatus.VERIFIED)
                .filter(observation -> observation.checksum().filter(expectedChecksum::equals).isPresent())
                .map(ReplicaObservation::nodeId)
                .toList();
        List<String> corrupted = ordered.stream()
                .filter(observation -> isCorrupt(observation, expectedChecksum))
                .map(ReplicaObservation::nodeId)
                .toList();
        List<String> missing = ordered.stream()
                .filter(observation -> observation.status() == ReplicaObservationStatus.MISSING
                        || observation.status() == ReplicaObservationStatus.UNAVAILABLE)
                .map(ReplicaObservation::nodeId)
                .toList();
        List<IntegrityFinding> findings = ordered.stream()
                .filter(observation -> isCorrupt(observation, expectedChecksum))
                .map(observation -> IntegrityFinding.checksumMismatch(
                        bucket,
                        key,
                        observation.nodeId(),
                        expectedChecksum,
                        observation.checksum().orElse("unavailable")))
                .toList();
        boolean met = valid.size() >= readQuorum;

        return new QuorumDecision(
                met ? QuorumDecision.QUORUM_MET : QuorumDecision.INTEGRITY_QUORUM_NOT_MET,
                valid,
                missing,
                readQuorum,
                valid.size(),
                false,
                met ? Optional.empty() : Optional.of(
                        "required read quorum " + readQuorum + " but valid replica count " + valid.size()),
                valid.size(),
                corrupted,
                met,
                findings);
    }

    private static boolean isCorrupt(ReplicaObservation observation, String expectedChecksum) {
        return observation.status() == ReplicaObservationStatus.CORRUPT
                || (observation.status() == ReplicaObservationStatus.VERIFIED
                && observation.checksum().filter(expectedChecksum::equals).isEmpty());
    }

    private static void requirePositive(int value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
