package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.domain.valueobject.NodeId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages multiple FileSystemStorageNode instances and creates the directory structure:
 * <pre>
 * clusterRoot/
 *   nodes/node-001..N/
 *   devices/bucket/
 *   devices/dedup/
 *   metadata/manifests/
 *   metadata/content-address-index/
 * </pre>
 */
public class FileSystemStorageCluster {

    private final Path clusterRoot;
    private final List<FileSystemStorageNode> nodes;

    public FileSystemStorageCluster(Path clusterRoot, int nodeCount) {
        this.clusterRoot = java.util.Objects.requireNonNull(clusterRoot, "clusterRoot must not be null");
        if (nodeCount < 1) {
            throw new IllegalArgumentException("nodeCount must be >= 1: " + nodeCount);
        }

        // Create directory structure
        try {
            Files.createDirectories(clusterRoot.resolve("nodes"));
            Files.createDirectories(clusterRoot.resolve("devices").resolve("bucket"));
            Files.createDirectories(clusterRoot.resolve("devices").resolve("dedup"));
            Files.createDirectories(clusterRoot.resolve("metadata").resolve("manifests"));
            Files.createDirectories(clusterRoot.resolve("metadata").resolve("content-address-index"));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create cluster directory structure", e);
        }

        // Create nodes
        List<FileSystemStorageNode> nodeList = new ArrayList<>();
        for (int i = 1; i <= nodeCount; i++) {
            Path nodePath = clusterRoot.resolve("nodes").resolve(String.format("node-%03d", i));
            NodeId nodeId = NodeId.of(String.format("node-%03d", i));
            try {
                Files.createDirectories(nodePath);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create node directory: " + nodePath, e);
            }
            nodeList.add(new FileSystemStorageNode(nodePath, nodeId));
        }
        this.nodes = List.copyOf(nodeList);
    }

    public List<FileSystemStorageNode> nodes() {
        return nodes;
    }

    public Path clusterRoot() {
        return clusterRoot;
    }

    /**
     * Returns the path to the devices directory.
     */
    public Path devicesRoot() {
        return clusterRoot.resolve("devices");
    }

    /**
     * Returns a FileSystemVirtualDeviceMapper rooted at devices/.
     */
    public FileSystemVirtualDeviceMapper deviceMapper() {
        return new FileSystemVirtualDeviceMapper(devicesRoot());
    }

    /**
     * Returns a FileSystemContentAddressIndex rooted at metadata/content-address-index/.
     */
    public FileSystemContentAddressIndex addressIndex() {
        return new FileSystemContentAddressIndex(
                clusterRoot.resolve("metadata").resolve("content-address-index"));
    }

    /**
     * Returns a FileSystemManifestRepository rooted at metadata/manifests/.
     */
    public FileSystemManifestRepository manifestRepository() {
        return new FileSystemManifestRepository(
                clusterRoot.resolve("metadata").resolve("manifests"));
    }

    /**
     * Returns a FileSystemStoredObjectRepository rooted at metadata/.
     */
    public FileSystemStoredObjectRepository storedObjectRepository() {
        return new FileSystemStoredObjectRepository(
                clusterRoot.resolve("metadata"));
    }
}
