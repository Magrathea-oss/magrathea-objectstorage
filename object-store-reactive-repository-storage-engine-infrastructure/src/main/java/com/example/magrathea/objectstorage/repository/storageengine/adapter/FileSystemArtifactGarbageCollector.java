package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.storageengine.domain.valueobject.StorageArtifactKind;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Durable, idempotent garbage collection for typed filesystem artifacts.
 *
 * <p>A pending marker is committed before physical deletion. A process that stops in the
 * middle of a run can call {@link #resume(Set)} after restart. Artifacts are deleted only
 * when no other committed manifest references their identifier. This is deliberately a
 * manifest reachability calculation rather than a chunk counter: it preserves INV-6 by
 * treating whole objects, dedup windows, multipart parts, and EC shards as distinct typed
 * storage units.</p>
 */
public final class FileSystemArtifactGarbageCollector {

    private final Path root;
    private final Path manifestsRoot;
    private final Path pendingRoot;

    public FileSystemArtifactGarbageCollector(Path root) {
        this.root = java.util.Objects.requireNonNull(root, "root must not be null");
        this.manifestsRoot = root.resolve("metadata/manifests");
        this.pendingRoot = root.resolve("metadata/gc/pending");
        try {
            Files.createDirectories(manifestsRoot);
            Files.createDirectories(pendingRoot);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to initialize garbage collector", error);
        }
    }

    /** Durably records intent before the owning S3 reference is removed or replaced. */
    public synchronized void prepare(UUID manifestId) {
        writeMarker(manifestId);
    }

    /** Reclaims one obsolete manifest unless it is still an S3-visible live reference. */
    public synchronized ReclamationReport reclaim(UUID manifestId, Set<UUID> liveManifestIds) {
        if (liveManifestIds.contains(manifestId)) {
            return ReclamationReport.empty();
        }
        Path manifest = manifestPath(manifestId);
        if (!Files.isRegularFile(manifest)) {
            deleteMarker(manifestId);
            return ReclamationReport.empty();
        }
        writeMarker(manifestId);
        ManifestArtifacts obsolete = readArtifacts(manifest);
        Set<UUID> referencedElsewhere = referencedArtifactsExcept(manifestId);
        int artifactsDeleted = 0;
        int sidecarsDeleted = 0;
        int indexEntriesDeleted = 0;
        for (Artifact artifact : obsolete.artifacts()) {
            if (referencedElsewhere.contains(artifact.id())) {
                continue;
            }
            DeletionCount count = deleteArtifact(artifact);
            artifactsDeleted += count.artifacts();
            sidecarsDeleted += count.sidecars();
            if (artifact.kind() == StorageArtifactKind.DEDUP_CHUNK) {
                indexEntriesDeleted += deleteContentAddressEntries(artifact.id());
            }
        }
        int manifestsDeleted = deleteManifestFiles(manifestId);
        deleteMarker(manifestId);
        return new ReclamationReport(artifactsDeleted, sidecarsDeleted, indexEntriesDeleted, manifestsDeleted);
    }

    /** Completes every durable pending reclamation marker after a process restart. */
    public synchronized ReclamationReport resume(Set<UUID> liveManifestIds) {
        ReclamationReport total = ReclamationReport.empty();
        try (var paths = Files.list(pendingRoot)) {
            for (Path marker : paths.filter(Files::isRegularFile).toList()) {
                String name = marker.getFileName().toString();
                if (name.endsWith(".pending")) {
                    total = total.plus(reclaim(UUID.fromString(name.substring(0, name.length() - 8)), liveManifestIds));
                }
            }
            return total;
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to resume pending reclamation", error);
        }
    }

    private Set<UUID> referencedArtifactsExcept(UUID excludedManifestId) {
        Set<UUID> referenced = new HashSet<>();
        try (var paths = Files.list(manifestsRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> isManifest(path) && !path.getFileName().toString().startsWith(excludedManifestId + "."))
                    .map(this::readArtifacts)
                    .flatMap(manifest -> manifest.artifacts().stream())
                    .map(Artifact::id)
                    .forEach(referenced::add);
            return referenced;
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to calculate manifest reachability", error);
        }
    }

    private ManifestArtifacts readArtifacts(Path path) {
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(Files.readString(path)));
            int schema = Integer.parseInt(properties.getProperty("manifest.schemaVersion", "0"));
            String countKey = schema >= 2 ? "artifactCount" : "chunkCount";
            int count = Integer.parseInt(properties.getProperty(countKey, "0"));
            List<Artifact> artifacts = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                String prefix = schema >= 2 ? "artifact." + index + "." : "chunk." + index + ".";
                String idKey = schema >= 2 ? "artifactId" : "chunkId";
                StorageArtifactKind kind = schema >= 2
                        ? StorageArtifactKind.valueOf(required(properties, prefix + "kind"))
                        : StorageArtifactKind.LEGACY_CHUNK;
                artifacts.add(new Artifact(kind, UUID.fromString(required(properties, prefix + idKey))));
            }
            return new ManifestArtifacts(List.copyOf(artifacts));
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to read manifest for reclamation: " + path, error);
        }
    }

    private DeletionCount deleteArtifact(Artifact artifact) {
        int deleted = 0;
        int sidecars = 0;
        String namespace = artifact.kind() == StorageArtifactKind.WHOLE_OBJECT ? "whole-objects" : "chunks";
        Path nodes = root.resolve("nodes");
        if (!Files.isDirectory(nodes)) {
            return new DeletionCount(0, 0);
        }
        try (var nodePaths = Files.list(nodes)) {
            for (Path node : nodePaths.filter(Files::isDirectory).toList()) {
                Path data = node.resolve(namespace).resolve(artifact.id().toString());
                Path checksum = Path.of(data + ".sha256");
                if (Files.deleteIfExists(data)) {
                    deleted++;
                }
                if (Files.deleteIfExists(checksum)) {
                    sidecars++;
                }
            }
            return new DeletionCount(deleted, sidecars);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to delete typed storage artifact " + artifact.id(), error);
        }
    }

    private int deleteContentAddressEntries(UUID artifactId) {
        Path indexRoot = root.resolve("metadata/content-address-index");
        if (!Files.isDirectory(indexRoot)) {
            return 0;
        }
        int deleted = 0;
        try (var paths = Files.walk(indexRoot)) {
            for (Path entry : paths.filter(Files::isRegularFile).toList()) {
                if (Files.readString(entry).trim().equals(artifactId.toString()) && Files.deleteIfExists(entry)) {
                    deleted++;
                }
            }
            return deleted;
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to remove content-address entries", error);
        }
    }

    private int deleteManifestFiles(UUID manifestId) {
        try {
            int deleted = Files.deleteIfExists(manifestsRoot.resolve(manifestId + ".properties")) ? 1 : 0;
            if (Files.deleteIfExists(manifestsRoot.resolve(manifestId + ".json"))) {
                deleted++;
            }
            return deleted;
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to delete obsolete manifest " + manifestId, error);
        }
    }

    private Path manifestPath(UUID manifestId) {
        Path properties = manifestsRoot.resolve(manifestId + ".properties");
        return Files.exists(properties) ? properties : manifestsRoot.resolve(manifestId + ".json");
    }

    private void writeMarker(UUID manifestId) {
        Path marker = pendingRoot.resolve(manifestId + ".pending");
        if (Files.exists(marker)) {
            return;
        }
        try {
            Files.createDirectories(pendingRoot);
            Path temporary = Files.createTempFile(pendingRoot, manifestId + ".", ".tmp");
            Files.writeString(temporary, manifestId.toString());
            try (FileChannel channel = FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, marker);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to commit reclamation marker", error);
        }
    }

    private void deleteMarker(UUID manifestId) {
        try {
            Files.deleteIfExists(pendingRoot.resolve(manifestId + ".pending"));
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to clear reclamation marker", error);
        }
    }

    private static boolean isManifest(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".properties") || name.endsWith(".json");
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Manifest is missing required property: " + key);
        }
        return value;
    }

    private record Artifact(StorageArtifactKind kind, UUID id) { }
    private record ManifestArtifacts(List<Artifact> artifacts) { }
    private record DeletionCount(int artifacts, int sidecars) { }

    public record ReclamationReport(int artifactsDeleted, int sidecarsDeleted,
                                    int contentAddressEntriesDeleted, int manifestsDeleted) {
        public static ReclamationReport empty() {
            return new ReclamationReport(0, 0, 0, 0);
        }

        public ReclamationReport plus(ReclamationReport other) {
            return new ReclamationReport(
                    artifactsDeleted + other.artifactsDeleted,
                    sidecarsDeleted + other.sidecarsDeleted,
                    contentAddressEntriesDeleted + other.contentAddressEntriesDeleted,
                    manifestsDeleted + other.manifestsDeleted);
        }

        public int totalDeleted() {
            return artifactsDeleted + sidecarsDeleted + contentAddressEntriesDeleted + manifestsDeleted;
        }
    }
}
