package com.example.magrathea.storageengine.infrastructure.chaos;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemContentAddressIndex;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemManifestRepository;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageCluster;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageNode;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStoredObjectRepository;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemVirtualDeviceMapper;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageNode.WriteResult;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * Decorator over FileSystemStorageCluster that injects faults based on ChaosStrategy.
 * <p>
 * Supported faults:
 * - RANDOM_CORRUPTION: corrupts a random byte in written data
 * - NODE_OFFLINE: makes a random node unavailable
 * - SLOW_NODE: adds artificial delays to operations
 * - ALL: enables all fault types
 */
public class FaultInjectingStorageCluster {

    private final FileSystemStorageCluster delegate;
    private final ChaosStrategy strategy;
    private final Random random = new Random();

    public FaultInjectingStorageCluster(FileSystemStorageCluster delegate, ChaosStrategy strategy) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate must not be null");
        this.strategy = java.util.Objects.requireNonNull(strategy, "strategy must not be null");
    }

    public List<FileSystemStorageNode> nodes() {
        return delegate.nodes();
    }

    public Path clusterRoot() {
        return delegate.clusterRoot();
    }

    public FileSystemVirtualDeviceMapper deviceMapper() {
        return delegate.deviceMapper();
    }

    public FileSystemContentAddressIndex addressIndex() {
        return delegate.addressIndex();
    }

    public FileSystemManifestRepository manifestRepository() {
        return delegate.manifestRepository();
    }

    public FileSystemStoredObjectRepository storedObjectRepository() {
        return delegate.storedObjectRepository();
    }

    /**
     * Writes chunk data with potential fault injection.
     */
    public Mono<WriteResult> write(FileSystemStorageNode node, ChunkId chunkId, byte[] data) {
        Mono<WriteResult> base = node.write(chunkId, applyCorruption(data));
        Mono<WriteResult> faultInjected = injectFaults(base, node);
        return faultInjected;
    }

    /**
     * Reads chunk data with potential fault injection.
     */
    public Mono<byte[]> read(FileSystemStorageNode node, ChunkId chunkId) {
        Mono<byte[]> base = node.read(chunkId);
        Mono<byte[]> faultInjected = injectReadFaults(base, node);
        return faultInjected;
    }

    private byte[] applyCorruption(byte[] data) {
        if (strategy == ChaosStrategy.NONE) {
            return data;
        }
        if (strategy != ChaosStrategy.RANDOM_CORRUPTION && strategy != ChaosStrategy.ALL) {
            return data;
        }
        // Corrupt a random byte
        byte[] corrupted = new byte[data.length];
        System.arraycopy(data, 0, corrupted, 0, data.length);
        int index = random.nextInt(data.length);
        corrupted[index] = (byte) (corrupted[index] ^ 0xFF); // flip all bits
        return corrupted;
    }

    private Mono<WriteResult> injectFaults(Mono<WriteResult> base, FileSystemStorageNode node) {
        Mono<WriteResult> result = base;

        // Node offline
        if (strategy == ChaosStrategy.NODE_OFFLINE || strategy == ChaosStrategy.ALL) {
            if (random.nextFloat() < 0.3f) { // 30% chance of node offline
                result = Mono.error(new IOException(
                        "Simulated node offline: " + node.nodeId().value()));
            }
        }

        // Slow node
        if (strategy == ChaosStrategy.SLOW_NODE || strategy == ChaosStrategy.ALL) {
            if (random.nextFloat() < 0.5f) { // 50% chance of delay
                result = result.delayElement(Duration.ofMillis(
                        random.nextInt(500) + 100)); // 100-600ms delay
            }
        }

        return result.subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<byte[]> injectReadFaults(Mono<byte[]> base, FileSystemStorageNode node) {
        Mono<byte[]> result = base;

        // Node offline
        if (strategy == ChaosStrategy.NODE_OFFLINE || strategy == ChaosStrategy.ALL) {
            if (random.nextFloat() < 0.3f) {
                result = Mono.error(new IOException(
                        "Simulated node offline: " + node.nodeId().value()));
            }
        }

        // Slow node
        if (strategy == ChaosStrategy.SLOW_NODE || strategy == ChaosStrategy.ALL) {
            if (random.nextFloat() < 0.5f) {
                result = result.delayElement(Duration.ofMillis(
                        random.nextInt(500) + 100));
            }
        }

        return result.subscribeOn(Schedulers.boundedElastic());
    }
}
