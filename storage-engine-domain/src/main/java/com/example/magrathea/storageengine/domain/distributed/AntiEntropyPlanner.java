package com.example.magrathea.storageengine.domain.distributed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure modeled anti-entropy planner. It observes and plans; it never copies bytes. */
public class AntiEntropyPlanner {

    public HealingPlan plan(
            String manifestId,
            String bucket,
            String key,
            String expectedChecksum,
            List<String> expectedNodeIds,
            List<ReplicaObservation> observations) {
        requireNonBlank(manifestId, "manifestId");
        requireNonBlank(bucket, "bucket");
        requireNonBlank(key, "key");
        requireNonBlank(expectedChecksum, "expectedChecksum");
        Objects.requireNonNull(expectedNodeIds, "expectedNodeIds must not be null");
        Objects.requireNonNull(observations, "observations must not be null");

        List<String> expectedNodes = expectedNodeIds.stream().sorted().toList();
        Map<String, ReplicaObservation> byNode = new LinkedHashMap<>();
        observations.stream()
                .map(observation -> Objects.requireNonNull(observation, "observations must not contain null elements"))
                .sorted(Comparator.comparing(ReplicaObservation::nodeId))
                .forEach(observation -> byNode.put(observation.nodeId(), observation));

        List<String> verifiedSources = expectedNodes.stream()
                .map(byNode::get)
                .filter(Objects::nonNull)
                .filter(observation -> observation.status() == ReplicaObservationStatus.VERIFIED)
                .filter(observation -> observation.checksum().filter(expectedChecksum::equals).isPresent())
                .map(ReplicaObservation::nodeId)
                .toList();

        List<AntiEntropyFinding> findings = new ArrayList<>();
        List<HealingTask> tasks = new ArrayList<>();

        for (String nodeId : expectedNodes) {
            ReplicaObservation observation = byNode.get(nodeId);
            if (observation == null || observation.status() == ReplicaObservationStatus.MISSING) {
                findings.add(AntiEntropyFinding.forNode(AntiEntropyFinding.MISSING_REPLICA, nodeId, bucket, key));
                chooseSource(verifiedSources, nodeId).ifPresent(source -> tasks.add(new HealingTask(
                        taskId(manifestId, nodeId),
                        HealingTask.COPY_VERIFIED_REPLICA,
                        nodeId,
                        Optional.of(source),
                        HealingTask.PLANNED,
                        HealingTask.PLANNED_NOT_EXECUTED)));
            } else if (isCorrupt(observation, expectedChecksum)) {
                findings.add(AntiEntropyFinding.forNode(AntiEntropyFinding.CORRUPT_REPLICA, nodeId, bucket, key));
                chooseSource(verifiedSources, nodeId).ifPresent(source -> tasks.add(new HealingTask(
                        taskId(manifestId, nodeId),
                        HealingTask.REPLACE_CORRUPT_REPLICA,
                        nodeId,
                        Optional.of(source),
                        HealingTask.PLANNED,
                        HealingTask.PLANNED_NOT_EXECUTED)));
            }
        }

        if (!findings.isEmpty() && verifiedSources.isEmpty()) {
            findings.add(AntiEntropyFinding.unrecoverable(bucket, key));
            return new HealingPlan(findings, List.of(), HealingPlan.SIMULATION_UNRECOVERABLE, true);
        }

        return new HealingPlan(
                findings,
                tasks,
                tasks.isEmpty() ? HealingPlan.NOT_IMPLEMENTED : HealingPlan.PLANNED_NOT_EXECUTED,
                !findings.isEmpty());
    }

    private static Optional<String> chooseSource(List<String> verifiedSources, String targetNodeId) {
        return verifiedSources.stream().filter(nodeId -> !nodeId.equals(targetNodeId)).findFirst();
    }

    private static boolean isCorrupt(ReplicaObservation observation, String expectedChecksum) {
        return observation.status() == ReplicaObservationStatus.CORRUPT
                || (observation.status() == ReplicaObservationStatus.VERIFIED
                && observation.checksum().filter(expectedChecksum::equals).isEmpty());
    }

    private static String taskId(String manifestId, String nodeId) {
        String normalized = manifestId.replace("-datasets-2026-06-report-parquet", "");
        return "heal-" + normalized + "-" + nodeId;
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
