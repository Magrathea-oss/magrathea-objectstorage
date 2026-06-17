package com.example.magrathea.storageengine.domain.distributed;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RebalancePlannerTest {

    private static final String BUCKET = "distributed-bucket";
    private static final String KEY = "datasets/2026-06/report.parquet";
    private static final String MANIFEST = "manifest-distributed-bucket-datasets-2026-06-report-parquet";

    private final RebalancePlanner planner = new RebalancePlanner();

    @Test
    void rebalancingPlansSafeReplicaMovesWhenNewHealthyNodeJoinsNewRack() {
        RebalancePlan plan = planner.planNewNodeMoves(
                BUCKET,
                KEY,
                MANIFEST,
                List.of(
                        target("node-a", "rack-1"),
                        target("node-b", "rack-2"),
                        target("node-c", "rack-3")),
                target("node-d", "rack-4"),
                2);

        assertEquals(RebalancePlan.REBALANCE_PLAN_CREATED, plan.decision());
        assertTrue(plan.observableOnly());
        assertFalse(plan.moves().isEmpty());
        RebalanceMove move = plan.moves().get(0);
        assertEquals("node-d", move.targetNodeId());
        assertEquals("rack-4", move.targetFailureDomain());
        assertEquals(MANIFEST, move.manifestId());
        assertTrue(move.committedReplicasKept() >= 2);
        assertFalse(move.sourceNodeId().isBlank());
        assertFalse(move.sourceFailureDomain().isBlank());
    }

    @Test
    void failedRebalanceCopyKeepsOriginalReplicasCommittedAndRecordsRetryEligibility() {
        RebalanceTaskResult result = planner.evaluateFailedCopyTask(
                "rebalance-manifest-distributed-bucket-node-d",
                MANIFEST,
                List.of("node-c", "node-a", "node-b"),
                2,
                "target-write-timeout");

        assertEquals(RebalanceTaskResult.FAILED, result.status());
        assertEquals(RebalanceTaskResult.RETRYABLE, result.retryEligibility());
        assertEquals("target-write-timeout", result.failureReason());
        assertEquals(MANIFEST, result.manifestId());
        assertIterableEquals(List.of("node-a", "node-b", "node-c"), result.originalReplicasCommitted());
        assertTrue(result.aboveWriteQuorum());
    }

    private static ReplicaTarget target(String nodeId, String failureDomain) {
        return new ReplicaTarget(
                nodeId,
                failureDomain,
                "disk-set-hot-" + nodeId.substring("node-".length()) + "-01",
                "/var/lib/magrathea/distributed/" + nodeId + "/objects",
                DistributedNodeHealth.HEALTHY);
    }
}
