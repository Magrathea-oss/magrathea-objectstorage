package com.example.magrathea.storageengine.domain.valueobject;

import java.util.List;

public record ChunkPersistenceTrace(
        ChunkId chunkId,
        Fingerprint fingerprint,
        long originalSize,
        List<StepExecutionRecord> steps) {

    public ChunkPersistenceTrace {
        java.util.Objects.requireNonNull(chunkId, "chunkId must not be null");
        java.util.Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        if (originalSize < 0) {
            throw new IllegalArgumentException("originalSize must be >= 0: " + originalSize);
        }
        java.util.Objects.requireNonNull(steps, "steps must not be null");
        if (steps.size() != 6) {
            throw new IllegalArgumentException("steps must have exactly 6 entries: " + steps.size());
        }
    }

    public ContentHash finalChecksum() {
        // The final checksum is the output checksum of the last step (STORE)
        if (steps.isEmpty()) {
            throw new IllegalStateException("No steps available");
        }
        StepExecutionRecord lastStep = steps.get(steps.size() - 1);
        return lastStep.outputChecksum()
                .orElseThrow(() -> new IllegalStateException("Last step missing output checksum"));
    }
}
