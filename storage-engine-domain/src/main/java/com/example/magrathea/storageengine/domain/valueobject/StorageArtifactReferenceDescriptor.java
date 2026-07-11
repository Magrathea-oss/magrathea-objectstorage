package com.example.magrathea.storageengine.domain.valueobject;

import java.util.List;

/** A typed reference to one persisted unit in an object manifest. */
public record StorageArtifactReferenceDescriptor(
        StorageArtifactKind artifactKind,
        ChunkId chunkId,
        Fingerprint fingerprint,
        long originalSize,
        long storedSize,
        List<StepChecksumDescriptor> stepChecksums,
        ContentHash finalChecksum,
        List<NodeId> locations) {

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
        stepChecksums = List.copyOf(stepChecksums);
        locations = List.copyOf(locations);
    }

    /**
     * Compatibility constructor for callers that construct historical, untyped references.
     * Production schema-2 writes must use the canonical constructor with an explicit kind.
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
