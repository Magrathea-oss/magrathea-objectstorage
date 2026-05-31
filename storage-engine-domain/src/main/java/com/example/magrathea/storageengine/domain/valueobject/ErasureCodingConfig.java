package com.example.magrathea.storageengine.domain.valueobject;

public record ErasureCodingConfig(int dataBlocks, int parityBlocks) {
    public ErasureCodingConfig {
        if (dataBlocks < 2) {
            throw new IllegalArgumentException("dataBlocks (k) must be >= 2: " + dataBlocks);
        }
        if (parityBlocks < 1) {
            throw new IllegalArgumentException("parityBlocks (m) must be >= 1: " + parityBlocks);
        }
        if (dataBlocks + parityBlocks > 32) {
            throw new IllegalArgumentException("k + m must be <= 32: " + (dataBlocks + parityBlocks));
        }
    }

    public static ErasureCodingConfig of(int dataBlocks, int parityBlocks) {
        return new ErasureCodingConfig(dataBlocks, parityBlocks);
    }
}
