package com.example.magrathea.storageengine.domain.distributed;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiEntropyPlannerTest {

    private static final String BUCKET = "distributed-bucket";
    private static final String KEY = "datasets/2026-06/report.parquet";
    private static final String MANIFEST = "manifest-distributed-bucket-datasets-2026-06-report-parquet";
    private static final String CHECKSUM = "sha256:4c81f0bfe0c4f7c9d8f971a5de89b372176f2cdd1f6e8ee4b316f8f94b35f987";

    private final AntiEntropyPlanner planner = new AntiEntropyPlanner();

    @Test
    void antiEntropyCreatesHealingTaskForMissingReplica() {
        HealingPlan plan = planner.plan(
                MANIFEST,
                BUCKET,
                KEY,
                CHECKSUM,
                List.of("node-a", "node-b", "node-c"),
                List.of(
                        ReplicaObservation.verified("node-a", CHECKSUM),
                        ReplicaObservation.missing("node-b"),
                        ReplicaObservation.verified("node-c", CHECKSUM)));

        assertTrue(plan.findings().stream().anyMatch(finding -> finding.findingType().equals(AntiEntropyFinding.MISSING_REPLICA)
                && finding.nodeId().orElseThrow().equals("node-b")));
        assertEquals(HealingPlan.PLANNED_NOT_EXECUTED, plan.readinessStatus());
        assertEquals(1, plan.tasks().size());
        HealingTask task = plan.tasks().get(0);
        assertEquals("heal-manifest-distributed-bucket-node-b", task.taskId());
        assertEquals(HealingTask.COPY_VERIFIED_REPLICA, task.action());
        assertEquals("node-b", task.targetNodeId());
        assertTrue(List.of("node-a", "node-c").contains(task.sourceNodeId().orElseThrow()));
        assertEquals(HealingTask.PLANNED, task.status());
        assertEquals(HealingTask.PLANNED_NOT_EXECUTED, task.readiness());
    }

    @Test
    void antiEntropyExcludesCorruptReplicaAsSourceAndSchedulesReplacementFromVerifiedReplica() {
        HealingPlan plan = planner.plan(
                MANIFEST,
                BUCKET,
                KEY,
                CHECKSUM,
                List.of("node-a", "node-b", "node-c"),
                List.of(
                        ReplicaObservation.verified("node-a", CHECKSUM),
                        ReplicaObservation.verified("node-b", CHECKSUM),
                        ReplicaObservation.verified("node-c", "sha256:1111111111111111111111111111111111111111111111111111111111111111")));

        assertTrue(plan.findings().stream().anyMatch(finding -> finding.findingType().equals(AntiEntropyFinding.CORRUPT_REPLICA)
                && finding.nodeId().orElseThrow().equals("node-c")));
        assertEquals(1, plan.tasks().size());
        HealingTask task = plan.tasks().get(0);
        assertEquals(HealingTask.REPLACE_CORRUPT_REPLICA, task.action());
        assertEquals("node-c", task.targetNodeId());
        assertTrue(List.of("node-a", "node-b").contains(task.sourceNodeId().orElseThrow()));
        assertFalse(task.sourceNodeId().orElseThrow().equals("node-c"), "A corrupt replica must not be a healing source");
        assertEquals(HealingTask.PLANNED_NOT_EXECUTED, task.readiness());
        assertTrue(plan.observableIntegrityAlert());
    }

    @Test
    void antiEntropyReportsUnrecoverableStateWhenNoVerifiedSourceReplicaExists() {
        HealingPlan plan = planner.plan(
                MANIFEST,
                BUCKET,
                KEY,
                CHECKSUM,
                List.of("node-a", "node-b", "node-c"),
                List.of(
                        ReplicaObservation.missing("node-a"),
                        ReplicaObservation.verified("node-b", "sha256:2222222222222222222222222222222222222222222222222222222222222222"),
                        ReplicaObservation.unavailable("node-c")));

        assertTrue(plan.findings().stream().anyMatch(finding -> finding.findingType().equals(AntiEntropyFinding.UNRECOVERABLE_NO_VERIFIED_SOURCE)));
        assertTrue(plan.tasks().isEmpty());
        assertEquals(HealingPlan.SIMULATION_UNRECOVERABLE, plan.readinessStatus());
        assertTrue(plan.observableIntegrityAlert());
    }
}
