package com.example.magrathea.storageengine.domain.distributed;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Pure rebalance planner for modeled distributed readiness. */
public class RebalancePlanner {

    public RebalancePlan planNewNodeMoves(
            String bucket,
            String key,
            String manifestId,
            List<ReplicaTarget> committedReplicas,
            ReplicaTarget newNode,
            int writeQuorum) {
        requireNonBlank(bucket, "bucket");
        requireNonBlank(key, "key");
        requireNonBlank(manifestId, "manifestId");
        Objects.requireNonNull(committedReplicas, "committedReplicas must not be null");
        Objects.requireNonNull(newNode, "newNode must not be null");
        if (writeQuorum < 1) {
            throw new IllegalArgumentException("writeQuorum must be positive");
        }

        List<ReplicaTarget> committed = committedReplicas.stream()
                .map(replica -> Objects.requireNonNull(replica, "committedReplicas must not contain null elements"))
                .sorted(Comparator.comparing(ReplicaTarget::nodeId))
                .toList();
        if (committed.isEmpty() || newNode.health() != DistributedNodeHealth.HEALTHY) {
            return new RebalancePlan(RebalancePlan.REBALANCE_PLAN_CREATED, List.of(), true);
        }

        ReplicaTarget source = committed.get(0);
        RebalanceMove move = new RebalanceMove(
                source.nodeId(),
                newNode.nodeId(),
                source.failureDomain(),
                newNode.failureDomain(),
                manifestId,
                committed.size());
        if (move.committedReplicasKept() < writeQuorum) {
            return new RebalancePlan(RebalancePlan.REBALANCE_PLAN_CREATED, List.of(), true);
        }
        return new RebalancePlan(RebalancePlan.REBALANCE_PLAN_CREATED, List.of(move), true);
    }

    public RebalanceTaskResult evaluateFailedCopyTask(
            String taskId,
            String manifestId,
            List<String> originalCommittedNodeIds,
            int writeQuorum,
            String failureReason) {
        requireNonBlank(taskId, "taskId");
        requireNonBlank(manifestId, "manifestId");
        Objects.requireNonNull(originalCommittedNodeIds, "originalCommittedNodeIds must not be null");
        if (writeQuorum < 1) {
            throw new IllegalArgumentException("writeQuorum must be positive");
        }
        requireNonBlank(failureReason, "failureReason");
        List<String> committed = originalCommittedNodeIds.stream().sorted().toList();
        return new RebalanceTaskResult(
                taskId,
                RebalanceTaskResult.FAILED,
                RebalanceTaskResult.RETRYABLE,
                failureReason,
                manifestId,
                committed,
                committed.size() >= writeQuorum);
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
