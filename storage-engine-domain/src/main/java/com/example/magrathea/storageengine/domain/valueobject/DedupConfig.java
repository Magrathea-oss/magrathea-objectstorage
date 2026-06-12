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
        if (chunkSize < 4096L) {
            throw new IllegalArgumentException("chunkSize must be >= 4096 (4KB): " + chunkSize);
        }
        if (chunkSize > 1_073_741_824L) {
            throw new IllegalArgumentException("chunkSize must be <= 1GB (1073741824): " + chunkSize);
        }
    }

    /** Default chunk size used by the no-arg factory: 1 MB. */
    public static final long DEFAULT_CHUNK_SIZE = 1_048_576L; // 1 MB

    public static DedupConfig of(DedupScope scope, FingerprintAlgorithm algorithm) {
        return new DedupConfig(scope, algorithm, DEFAULT_CHUNK_SIZE, ChunkAlignment.NONE);
    }

    public static DedupConfig of(DedupScope scope, FingerprintAlgorithm algorithm, long chunkSize, ChunkAlignment alignment) {
        return new DedupConfig(scope, algorithm, chunkSize, alignment);
    }
}
