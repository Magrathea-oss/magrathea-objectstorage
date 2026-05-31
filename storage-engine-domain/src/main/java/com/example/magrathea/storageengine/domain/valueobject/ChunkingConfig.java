package com.example.magrathea.storageengine.domain.valueobject;

public record ChunkingConfig(int chunkSize, ChunkAlignment alignment) {
    public ChunkingConfig {
        java.util.Objects.requireNonNull(alignment, "alignment must not be null");
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
        }
    }

    public static ChunkingConfig of(int chunkSize, ChunkAlignment alignment) {
        return new ChunkingConfig(chunkSize, alignment);
    }
}
