package com.example.magrathea.storageengine.domain.distributed;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Result of deterministic modeled replica placement. */
public record PlacementDecision(
        String decisionCode,
        List<ReplicaTarget> selectedTargets,
        Map<String, String> exclusions,
        Map<String, String> degradedRisks,
        int requestedFailureDomainCount,
        int availableHealthyFailureDomainCount,
        boolean readyForCommit) {

    public static final String PLACEMENT_PLAN_CREATED = "placement-plan-created";
    public static final String PLACEMENT_PLAN_CREATED_WITH_RISK = "placement-plan-created-with-risk";
    public static final String INSUFFICIENT_FAILURE_DOMAINS = "insufficient-failure-domains";
    public static final String NO_HEALTHY_NODES = "no-healthy-nodes";
    public static final String NODE_HEALTH_DOWN = "node-health-down";
    public static final String DEGRADED_FALLBACK_RISK = "degraded-node-used-after-healthy-candidates-exhausted";

    public PlacementDecision {
        requireNonBlank(decisionCode, "decisionCode");
        Objects.requireNonNull(selectedTargets, "selectedTargets must not be null");
        Objects.requireNonNull(exclusions, "exclusions must not be null");
        Objects.requireNonNull(degradedRisks, "degradedRisks must not be null");
        if (requestedFailureDomainCount < 1) {
            throw new IllegalArgumentException("requestedFailureDomainCount must be positive");
        }
        if (availableHealthyFailureDomainCount < 0) {
            throw new IllegalArgumentException("availableHealthyFailureDomainCount must not be negative");
        }
        selectedTargets = List.copyOf(selectedTargets);
        exclusions = Collections.unmodifiableMap(new LinkedHashMap<>(exclusions));
        degradedRisks = Collections.unmodifiableMap(new LinkedHashMap<>(degradedRisks));
    }

    public List<String> selectedNodeIds() {
        return selectedTargets.stream().map(ReplicaTarget::nodeId).toList();
    }

    public List<String> selectedFailureDomains() {
        return selectedTargets.stream().map(ReplicaTarget::failureDomain).toList();
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
