package com.example.magrathea.storageengine.domain.valueobject;

public record EcShardLayout(
        long stripeIndex,
        int shardIndex,
        int dataBlocks,
        int parityBlocks,
        boolean parity,
        long stripeLogicalLength) {

    /** Fixed shard size for the currently supported EC stripe geometry. */
    public static final long SHARD_SIZE_BYTES = 1024L * 1024;
    private static final int MAX_METADATA_BLOCKS = 32;

    public EcShardLayout {
        if (stripeIndex < 0) {
            throw new IllegalArgumentException("stripeIndex must be >= 0: " + stripeIndex);
        }
        if (dataBlocks < 1) {
            throw new IllegalArgumentException("dataBlocks (k) must be >= 1: " + dataBlocks);
        }
        if (parityBlocks < 1) {
            throw new IllegalArgumentException("parityBlocks (m) must be >= 1: " + parityBlocks);
        }

        long totalBlocks = (long) dataBlocks + parityBlocks;
        if (totalBlocks > MAX_METADATA_BLOCKS) {
            throw new IllegalArgumentException(
                    "k + m metadata must be <= " + MAX_METADATA_BLOCKS + ": " + totalBlocks);
        }
        if (shardIndex < 0 || shardIndex >= totalBlocks) {
            throw new IllegalArgumentException(
                    "shardIndex must be between 0 and " + (totalBlocks - 1) + ": " + shardIndex);
        }

        boolean indexIdentifiesParity = shardIndex >= dataBlocks;
        if (parity != indexIdentifiesParity) {
            throw new IllegalArgumentException(
                    "parity must identify shard indices greater than or equal to k=" + dataBlocks
                            + ": shardIndex=" + shardIndex + ", parity=" + parity);
        }

        long maximumLogicalLength = dataBlocks * SHARD_SIZE_BYTES;
        if (stripeLogicalLength < 0 || stripeLogicalLength > maximumLogicalLength) {
            throw new IllegalArgumentException(
                    "stripeLogicalLength must be between 0 and " + maximumLogicalLength
                            + ": " + stripeLogicalLength);
        }
    }
}
