package com.example.magrathea.storageengine.domain.valueobject;

import java.util.List;

public record ChunkReferenceDescriptor(
        ChunkId chunkId,
        Fingerprint fingerprint,
        long originalSize,
        long storedSize,
        List<StepChecksumDescriptor> stepChecksums,
        ContentHash finalChecksum,
        List<NodeId> locations) {

    public ChunkReferenceDescriptor {
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
        // Defensive copies — public API must never expose mutable collections.
        stepChecksums = List.copyOf(stepChecksums);
        locations = List.copyOf(locations);
    }
}
