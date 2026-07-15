package com.example.magrathea.storageengine.domain.valueobject;

public record ErasureCodingConfig(int dataBlocks, int parityBlocks) {
    static final int MAX_TOTAL_BLOCKS = 32;

    public ErasureCodingConfig {
        if (dataBlocks < 2) {
            throw new IllegalArgumentException("dataBlocks (k) must be >= 2: " + dataBlocks);
        }
        if (parityBlocks < 1) {
            throw new IllegalArgumentException("parityBlocks (m) must be >= 1: " + parityBlocks);
        }
        if (dataBlocks + parityBlocks > MAX_TOTAL_BLOCKS) {
            throw new IllegalArgumentException(
                    "k + m must be <= " + MAX_TOTAL_BLOCKS + ": " + (dataBlocks + parityBlocks));
        }
    }

    public static ErasureCodingConfig of(int dataBlocks, int parityBlocks) {
        return new ErasureCodingConfig(dataBlocks, parityBlocks);
    }
}
