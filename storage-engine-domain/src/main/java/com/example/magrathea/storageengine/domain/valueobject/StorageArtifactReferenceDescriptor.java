package com.example.magrathea.storageengine.domain.valueobject;

import java.util.List;
import java.util.Optional;

/** A typed reference to one persisted unit in an object manifest. */
public record StorageArtifactReferenceDescriptor(
        StorageArtifactKind artifactKind,
        ChunkId chunkId,
        Fingerprint fingerprint,
        long originalSize,
        long storedSize,
        List<StepChecksumDescriptor> stepChecksums,
        ContentHash finalChecksum,
        List<NodeId> locations,
        Optional<EcShardLayout> ecShardLayout) {

    public StorageArtifactReferenceDescriptor {
        java.util.Objects.requireNonNull(artifactKind, "artifactKind must not be null");
        java.util.Objects.requireNonNull(chunkId, "chunkId must not be null");
        java.util.Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        if (originalSize < 0) {
            throw new IllegalArgumentException("originalSize must be >= 0: " + originalSize);
        }
        if (storedSize < 0) {
            throw new IllegalArgumentException("storedSize must be >= 0: " + storedSize);
        }
        java.util.Objects.requireNonNull(stepChecksums, "stepChecksums must not be null");
        java.util.Objects.requireNonNull(finalChecksum, "finalChecksum must not be null");
        java.util.Objects.requireNonNull(locations, "locations must not be null");
        java.util.Objects.requireNonNull(ecShardLayout, "ecShardLayout must not be null");
        ecShardLayout.ifPresent(layout -> {
            switch (artifactKind) {
                case EC_DATA_SHARD -> {
                    if (layout.parity()) {
                        throw new IllegalArgumentException(
                                "EC_DATA_SHARD requires a data shard layout");
                    }
                }
                case EC_PARITY_SHARD -> {
                    if (!layout.parity()) {
                        throw new IllegalArgumentException(
                                "EC_PARITY_SHARD requires a parity shard layout");
                    }
                }
                default -> throw new IllegalArgumentException(
                        "ecShardLayout is only valid for EC data or parity shard artifacts: "
                                + artifactKind);
            }
        });
        stepChecksums = List.copyOf(stepChecksums);
        locations = List.copyOf(locations);
    }

    /** Compatibility constructor for schema-2 typed references without EC layout metadata. */
    public StorageArtifactReferenceDescriptor(
            StorageArtifactKind artifactKind,
            ChunkId chunkId,
            Fingerprint fingerprint,
            long originalSize,
            long storedSize,
            List<StepChecksumDescriptor> stepChecksums,
            ContentHash finalChecksum,
            List<NodeId> locations) {
        this(artifactKind, chunkId, fingerprint, originalSize, storedSize,
                stepChecksums, finalChecksum, locations, Optional.empty());
    }

    /**
     * Compatibility constructor for callers that construct historical, untyped references.
     * Schema-2 typed references use the compatibility constructor with an explicit kind.
     */
    public StorageArtifactReferenceDescriptor(
            ChunkId chunkId,
            Fingerprint fingerprint,
            long originalSize,
            long storedSize,
            List<StepChecksumDescriptor> stepChecksums,
            ContentHash finalChecksum,
            List<NodeId> locations) {
        this(StorageArtifactKind.LEGACY_CHUNK, chunkId, fingerprint, originalSize, storedSize,
                stepChecksums, finalChecksum, locations);
    }
}
