package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.ChunkStorePort;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.PersistencePlan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.NoSuchFileException;
import java.util.List;

/**
 * Filesystem-backed chunk store port.
 * Persists chunk data under the exact ChunkId chosen by the application pipeline.
 */
public class FileSystemChunkStorePort implements ChunkStorePort {

    private final FileSystemStorageCluster cluster;

    public FileSystemChunkStorePort(FileSystemStorageCluster cluster) {
        this.cluster = java.util.Objects.requireNonNull(cluster, "cluster must not be null");
    }

    @Override
    public Mono<List<NodeId>> store(ChunkId chunkId, byte[] data, PersistencePlan plan) {
        List<FileSystemStorageNode> nodes = cluster.nodes();
        int replicationFactor = plan.effectivePolicy().replication().factor();
        int nodesToUse = Math.min(replicationFactor, nodes.size());

        return Flux.fromIterable(nodes.subList(0, nodesToUse))
                .concatMap(node -> node.write(chunkId, data)
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(FileSystemStorageNode.WriteResult::nodeId)
                .collectList();
    }

    @Override
    public Mono<byte[]> read(ChunkId chunkId) {
        return Flux.fromIterable(cluster.nodes())
                .concatMap(node -> node.read(chunkId)
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(NoSuchFileException.class, ignored -> Mono.empty()))
                .next()
                .switchIfEmpty(Mono.error(new NoSuchFileException("Chunk not found: " + chunkId.value())));
    }

    @Override
    public Mono<Void> delete(ChunkId chunkId) {
        return Flux.fromIterable(cluster.nodes())
                .concatMap(node -> Mono.fromRunnable(() -> {
                            try {
                                java.nio.file.Files.deleteIfExists(node.nodePath().resolve("chunks")
                                        .resolve(chunkId.value().toString()));
                                java.nio.file.Files.deleteIfExists(node.nodePath().resolve("chunks")
                                        .resolve(chunkId.value() + ".sha256"));
                            } catch (java.io.IOException error) {
                                throw new java.io.UncheckedIOException("Failed to delete unpublished chunk", error);
                            }
                        }).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }
}
