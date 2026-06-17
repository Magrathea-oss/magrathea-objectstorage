package com.example.magrathea.storageengine.domain.distributed;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure deterministic placement planner for Phase 6 distributed-readiness simulation.
 */
public class DistributedPlacementPlanner {

    public PlacementDecision plan(String bucket, String key, int replicationFactor, DistributedTopology topology) {
        requireNonBlank(bucket, "bucket");
        requireNonBlank(key, "key");
        Objects.requireNonNull(topology, "topology must not be null");
        if (replicationFactor < 1) {
            throw new IllegalArgumentException("replicationFactor must be positive");
        }

        Map<String, String> exclusions = new LinkedHashMap<>();
        Map<String, String> risks = new LinkedHashMap<>();
        topology.nodesById().stream()
                .filter(DistributedNode::isDown)
                .forEach(node -> exclusions.put(node.nodeId(), PlacementDecision.NODE_HEALTH_DOWN));

        long availableHealthyFailureDomains = topology.nodesById().stream()
                .filter(DistributedNode::isHealthy)
                .map(DistributedNode::failureDomain)
                .distinct()
                .count();

        if (availableHealthyFailureDomains == 0) {
            return new PlacementDecision(
                    PlacementDecision.NO_HEALTHY_NODES,
                    List.of(),
                    exclusions,
                    risks,
                    replicationFactor,
                    0,
                    false);
        }

        LinkedHashSet<String> usedFailureDomains = new LinkedHashSet<>();
        List<ReplicaTarget> healthyTargets = topology.nodesById().stream()
                .filter(DistributedNode::isHealthy)
                .filter(node -> usedFailureDomains.add(node.failureDomain()))
                .map(ReplicaTarget::from)
                .limit(replicationFactor)
                .toList();

        if (healthyTargets.size() == replicationFactor) {
            return new PlacementDecision(
                    PlacementDecision.PLACEMENT_PLAN_CREATED,
                    healthyTargets,
                    exclusions,
                    risks,
                    replicationFactor,
                    Math.toIntExact(availableHealthyFailureDomains),
                    true);
        }

        List<ReplicaTarget> selected = new java.util.ArrayList<>(healthyTargets);
        Set<String> selectedFailureDomains = new LinkedHashSet<>(
                healthyTargets.stream().map(ReplicaTarget::failureDomain).toList());
        for (DistributedNode node : topology.nodesById()) {
            if (selected.size() == replicationFactor) {
                break;
            }
            if (node.isDegraded() && selectedFailureDomains.add(node.failureDomain())) {
                selected.add(ReplicaTarget.from(node));
                risks.put(node.nodeId(), PlacementDecision.DEGRADED_FALLBACK_RISK);
            }
        }

        if (selected.size() == replicationFactor) {
            return new PlacementDecision(
                    PlacementDecision.PLACEMENT_PLAN_CREATED_WITH_RISK,
                    selected,
                    exclusions,
                    risks,
                    replicationFactor,
                    Math.toIntExact(availableHealthyFailureDomains),
                    true);
        }

        return new PlacementDecision(
                PlacementDecision.INSUFFICIENT_FAILURE_DOMAINS,
                List.of(),
                exclusions,
                risks,
                replicationFactor,
                Math.toIntExact(availableHealthyFailureDomains),
                false);
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
