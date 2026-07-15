package com.example.magrathea.storageengine.domain.valueobject;

public record ErasureCodingConfig(int dataBlocks, int parityBlocks) {
    public static final int FIXED_DATA_BLOCKS = 4;
    public static final int FIXED_PARITY_BLOCKS = 2;
    public static final int FIXED_TOTAL_BLOCKS = FIXED_DATA_BLOCKS + FIXED_PARITY_BLOCKS;

    public ErasureCodingConfig {
        if (dataBlocks != FIXED_DATA_BLOCKS || parityBlocks != FIXED_PARITY_BLOCKS) {
            throw new IllegalArgumentException(
                    "only fixed EC 4+2 is supported before parameterized EC validation: k="
                            + dataBlocks + ", m=" + parityBlocks);
        }
    }

    public static ErasureCodingConfig of(int dataBlocks, int parityBlocks) {
        return new ErasureCodingConfig(dataBlocks, parityBlocks);
    }
}
