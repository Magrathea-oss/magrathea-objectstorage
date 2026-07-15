package com.example.magrathea.storageengine.cluster.application;

/** One authoritative schema-3 EC 4+2 shard obligation and transport-neutral location. */
public record EcShardReference(
        long stripeIndex,
        int shardIndex,
        boolean parity,
        long stripeLogicalLength,
        long logicalDataLength,
        String artifactId,
        long storedLength,
        String sha256,
        NodeIdentity location) implements Comparable<EcShardReference> {

    public static final int DATA_SHARDS = 4;
    public static final int PARITY_SHARDS = 2;
    public static final int TOTAL_SHARDS = DATA_SHARDS + PARITY_SHARDS;
    public static final long SHARD_BYTES = 1024L * 1024;
    public static final long STRIPE_BYTES = DATA_SHARDS * SHARD_BYTES;

    public EcShardReference {
        if (stripeIndex < 0) throw new IllegalArgumentException("stripe index must be non-negative");
        if (shardIndex < 0 || shardIndex >= TOTAL_SHARDS) {
            throw new IllegalArgumentException("fixed EC 4+2 shard index must be between 0 and 5");
        }
        if (parity != (shardIndex >= DATA_SHARDS)) {
            throw new IllegalArgumentException("parity role contradicts fixed EC 4+2 shard index");
        }
        if (stripeLogicalLength < 1 || stripeLogicalLength > STRIPE_BYTES) {
            throw new IllegalArgumentException("stripe logical length must be between 1 and 4 MiB");
        }
        long expectedLogicalLength = parity ? 0 : Math.max(0L, Math.min(
                SHARD_BYTES, stripeLogicalLength - (long) shardIndex * SHARD_BYTES));
        if (logicalDataLength != expectedLogicalLength) {
            throw new IllegalArgumentException("shard logical data length contradicts fixed EC 4+2 layout");
        }
        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("artifact ID is required");
        }
        if (storedLength != SHARD_BYTES) {
            throw new IllegalArgumentException("fixed EC 4+2 stored shard length must be 1 MiB");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("lowercase SHA-256 is required");
        }
        if (location == null) throw new IllegalArgumentException("shard location is required");
    }

    @Override
    public int compareTo(EcShardReference other) {
        int stripe = Long.compare(stripeIndex, other.stripeIndex);
        return stripe != 0 ? stripe : Integer.compare(shardIndex, other.shardIndex);
    }
}
