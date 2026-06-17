package com.example.magrathea.storageengine.domain.distributed;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedPlacementPlannerTest {

    private static final String BUCKET = "distributed-bucket";
    private static final String KEY = "datasets/2026-06/report.parquet";

    private final DistributedPlacementPlanner planner = new DistributedPlacementPlanner();

    @Test
    void replicatedPlacementUsesThreeDistinctNodesAndFailureDomainsDeterministically() {
        DistributedTopology topology = DistributedTopology.of("phase-6-three-rack-topology", List.of(
                node("node-c", "rack-3", DistributedNodeHealth.HEALTHY),
                node("node-a", "rack-1", DistributedNodeHealth.HEALTHY),
                node("node-b", "rack-2", DistributedNodeHealth.HEALTHY)));

        PlacementDecision decision = planner.plan(BUCKET, KEY, 3, topology);
        PlacementDecision repeated = planner.plan(BUCKET, KEY, 3, topology);

        assertEquals(PlacementDecision.PLACEMENT_PLAN_CREATED, decision.decisionCode());
        assertTrue(decision.readyForCommit());
        assertEquals(3, decision.selectedTargets().size());
        assertIterableEquals(List.of("node-a", "node-b", "node-c"), decision.selectedNodeIds());
        assertIterableEquals(List.of("rack-1", "rack-2", "rack-3"), decision.selectedFailureDomains());
        assertIterableEquals(decision.selectedTargets(), repeated.selectedTargets());
        assertTrue(decision.selectedTargets().stream().allMatch(target -> target.storageRoot().endsWith(target.nodeId() + "/objects")));
    }

    @Test
    void placementFailsClearlyWhenTooFewHealthyFailureDomainsExistForReplicationFactorThree() {
        DistributedTopology topology = DistributedTopology.of("phase-6-two-healthy-racks", List.of(
                node("node-a", "rack-1", DistributedNodeHealth.HEALTHY),
                node("node-b", "rack-2", DistributedNodeHealth.HEALTHY),
                node("node-c", "rack-3", DistributedNodeHealth.DOWN)));

        PlacementDecision decision = planner.plan(BUCKET, KEY, 3, topology);

        assertEquals(PlacementDecision.INSUFFICIENT_FAILURE_DOMAINS, decision.decisionCode());
        assertFalse(decision.readyForCommit());
        assertEquals(3, decision.requestedFailureDomainCount());
        assertEquals(2, decision.availableHealthyFailureDomainCount());
        assertTrue(decision.selectedTargets().isEmpty(), "Planner must not silently duplicate failure domains");
        assertEquals(PlacementDecision.NODE_HEALTH_DOWN, decision.exclusions().get("node-c"));
    }

    @Test
    void placementExcludesDownNodeAndRecordsHealthSnapshot() {
        DistributedTopology topology = DistributedTopology.of("phase-6-health-aware-topology", List.of(
                node("node-a", "rack-1", DistributedNodeHealth.HEALTHY),
                node("node-b", "rack-2", DistributedNodeHealth.DOWN),
                node("node-c", "rack-3", DistributedNodeHealth.HEALTHY)));

        PlacementDecision decision = planner.plan(BUCKET, KEY, 2, topology);

        assertEquals(PlacementDecision.PLACEMENT_PLAN_CREATED, decision.decisionCode());
        assertIterableEquals(List.of("node-a", "node-c"), decision.selectedNodeIds());
        assertEquals(PlacementDecision.NODE_HEALTH_DOWN, decision.exclusions().get("node-b"));
        assertTrue(decision.selectedTargets().stream().allMatch(target -> target.health() == DistributedNodeHealth.HEALTHY));
    }

    @Test
    void placementUsesDegradedNodeOnlyAfterHealthyCandidatesAreExhaustedAndRecordsRisk() {
        DistributedTopology topology = DistributedTopology.of("phase-6-degraded-fallback-topology", List.of(
                node("node-a", "rack-1", DistributedNodeHealth.HEALTHY),
                DistributedNode.withReason(
                        "node-b",
                        "rack-2",
                        "disk-set-hot-b-01",
                        "/var/lib/magrathea/distributed/node-b/objects",
                        DistributedNodeHealth.DEGRADED,
                        "HIGH_LATENCY"),
                node("node-c", "rack-3", DistributedNodeHealth.DOWN)));

        PlacementDecision decision = planner.plan(BUCKET, KEY, 2, topology);

        assertEquals(PlacementDecision.PLACEMENT_PLAN_CREATED_WITH_RISK, decision.decisionCode());
        assertTrue(decision.readyForCommit());
        assertIterableEquals(List.of("node-a", "node-b"), decision.selectedNodeIds());
        assertEquals(DistributedNodeHealth.DEGRADED, decision.selectedTargets().get(1).health());
        assertEquals(PlacementDecision.NODE_HEALTH_DOWN, decision.exclusions().get("node-c"));
        assertEquals(PlacementDecision.DEGRADED_FALLBACK_RISK, decision.degradedRisks().get("node-b"));
    }

    @Test
    void placementFailsWhenAllCandidateNodesAreDown() {
        DistributedTopology topology = DistributedTopology.of("phase-6-all-down-topology", List.of(
                node("node-a", "rack-1", DistributedNodeHealth.DOWN),
                node("node-b", "rack-2", DistributedNodeHealth.DOWN),
                node("node-c", "rack-3", DistributedNodeHealth.DOWN)));

        PlacementDecision decision = planner.plan(BUCKET, KEY, 1, topology);

        assertEquals(PlacementDecision.NO_HEALTHY_NODES, decision.decisionCode());
        assertFalse(decision.readyForCommit());
        assertTrue(decision.selectedTargets().isEmpty());
        assertEquals(3, decision.exclusions().size());
        assertTrue(decision.exclusions().values().stream().allMatch(PlacementDecision.NODE_HEALTH_DOWN::equals));
    }

    private static DistributedNode node(String nodeId, String failureDomain, DistributedNodeHealth health) {
        return DistributedNode.of(
                nodeId,
                failureDomain,
                "disk-set-hot-" + nodeId.substring("node-".length()) + "-01",
                "/var/lib/magrathea/distributed/" + nodeId + "/objects",
                health);
    }
}
