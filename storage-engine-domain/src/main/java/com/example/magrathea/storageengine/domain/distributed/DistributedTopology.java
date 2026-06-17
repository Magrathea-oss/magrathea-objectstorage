package com.example.magrathea.storageengine.domain.distributed;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Deterministic modeled topology used by distributed-readiness planners.
 */
public record DistributedTopology(String name, List<DistributedNode> nodes) {

    public DistributedTopology {
        requireNonBlank(name, "name");
        Objects.requireNonNull(nodes, "nodes must not be null");
        nodes = nodes.stream()
                .map(node -> Objects.requireNonNull(node, "nodes must not contain null elements"))
                .sorted(Comparator.comparing(DistributedNode::nodeId))
                .toList();
        Map<String, Long> countsByNodeId = nodes.stream()
                .collect(Collectors.groupingBy(DistributedNode::nodeId, Collectors.counting()));
        countsByNodeId.forEach((nodeId, count) -> {
            if (count > 1) {
                throw new IllegalArgumentException("Duplicate distributed node id: " + nodeId);
            }
        });
        nodes = List.copyOf(nodes);
    }

    public static DistributedTopology of(String name, List<DistributedNode> nodes) {
        return new DistributedTopology(name, nodes);
    }

    public List<DistributedNode> nodesById() {
        return nodes;
    }

    public Map<String, DistributedNode> byNodeId() {
        LinkedHashMap<String, DistributedNode> result = new LinkedHashMap<>();
        nodes.forEach(node -> result.put(node.nodeId(), node));
        return Collections.unmodifiableMap(result);
    }

    public Optional<DistributedNode> node(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return nodes.stream().filter(node -> node.nodeId().equals(nodeId)).findFirst();
    }

    private static void requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
