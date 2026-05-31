package com.example.magrathea.storageengine.domain.valueobject;

public record CompressionConfig(CompressionAlgorithm algorithm, int level) {
    public CompressionConfig {
        java.util.Objects.requireNonNull(algorithm, "algorithm must not be null");
        if (level < 0 || level > 22) {
            throw new IllegalArgumentException("compression level must be between 0 and 22: " + level);
        }
    }

    public static CompressionConfig of(CompressionAlgorithm algorithm, int level) {
        return new CompressionConfig(algorithm, level);
    }
}
