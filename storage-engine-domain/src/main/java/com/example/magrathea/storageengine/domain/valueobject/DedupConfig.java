package com.example.magrathea.storageengine.domain.valueobject;

public record DedupConfig(
        DedupScope scope,
        FingerprintAlgorithm algorithm,
        long chunkSize,
        ChunkAlignment alignment) {
    public DedupConfig {
        java.util.Objects.requireNonNull(scope, "scope must not be null");
        java.util.Objects.requireNonNull(algorithm, "algorithm must not be null");
        java.util.Objects.requireNonNull(alignment, "alignment must not be null");
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
    }

    public static DedupConfig of(DedupScope scope, FingerprintAlgorithm algorithm) {
        return new DedupConfig(scope, algorithm, 1048576L, ChunkAlignment.NONE);
    }

    public static DedupConfig of(DedupScope scope, FingerprintAlgorithm algorithm, long chunkSize, ChunkAlignment alignment) {
        return new DedupConfig(scope, algorithm, chunkSize, alignment);
    }
}
