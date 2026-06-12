package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple replication adapter that copies data to the requested number of nodes.
 * Returns the list of NodeIds where data was replicated.
 */
public class SimpleReplicationAdapter {

    /**
     * Replicates data to {@code factor} nodes.
     *
     * @param data   the data to replicate
     * @param factor the replication factor (number of nodes)
     * @return Mono of list of NodeIds where data was written
     */
    public Mono<List<NodeId>> replicate(byte[] data, int factor) {
        return Mono.fromCallable(() -> {
            List<NodeId> nodes = new ArrayList<>();
            for (int i = 1; i <= factor; i++) {
                nodes.add(NodeId.of("replica-node-" + i));
            }
            // In a real implementation, this would write data to each node
            return nodes;
        });
    }
}
