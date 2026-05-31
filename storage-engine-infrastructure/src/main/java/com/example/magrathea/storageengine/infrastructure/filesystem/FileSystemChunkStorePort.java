package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.ChunkStorePort;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * Filesystem-backed chunk store port.
 * Persists chunk data to storage nodes according to the persistence plan.
 * Uses FileSystemStorageCluster nodes for actual storage.
 */
public class FileSystemChunkStorePort implements ChunkStorePort {

    private final FileSystemStorageCluster cluster;

    public FileSystemChunkStorePort(FileSystemStorageCluster cluster) {
        this.cluster = java.util.Objects.requireNonNull(cluster, "cluster must not be null");
    }

    @Override
    public Mono<List<NodeId>> store(byte[] data, PersistencePlan plan) {
        // Generate a chunk ID for this data
        ChunkId chunkId = ChunkId.generate();

        // Distribute data across cluster nodes based on plan
        // Simple strategy: round-robin across available nodes
        List<FileSystemStorageNode> nodes = cluster.nodes();
        int replicationFactor = plan.effectivePolicy().replication().factor();
        int nodesToUse = Math.min(replicationFactor, nodes.size());

        List<NodeId> storedNodeIds = new ArrayList<>();

        // Use boundedElastic scheduler for blocking I/O
        return Mono.fromCallable(() -> {
                    for (int i = 0; i < nodesToUse; i++) {
                        FileSystemStorageNode node = nodes.get(i);
                        node.write(chunkId, data).block(); // block is acceptable here (I/O scheduler)
                        storedNodeIds.add(node.nodeId());
                    }
                    return storedNodeIds;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
