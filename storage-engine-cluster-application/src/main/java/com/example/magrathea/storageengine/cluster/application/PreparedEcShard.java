package com.example.magrathea.storageengine.cluster.application;

/** One locally prepared schema-3 shard awaiting distributed placement. */
public record PreparedEcShard(
        PreparedArtifact artifact,
        long stripeIndex,
        int shardIndex,
        boolean parity,
        long stripeLogicalLength,
        long logicalDataLength) implements Comparable<PreparedEcShard> {

    public PreparedEcShard {
        if (artifact == null) throw new IllegalArgumentException("prepared artifact is required");
        new EcShardReference(stripeIndex, shardIndex, parity, stripeLogicalLength,
                logicalDataLength, artifact.artifactId(), artifact.length(), artifact.sha256(),
                artifact.localNode());
    }

    public EcShardReference placedAt(NodeIdentity location) {
        return new EcShardReference(stripeIndex, shardIndex, parity, stripeLogicalLength,
                logicalDataLength, artifact.artifactId(), artifact.length(), artifact.sha256(),
                location);
    }

    @Override
    public int compareTo(PreparedEcShard other) {
        int stripe = Long.compare(stripeIndex, other.stripeIndex);
        return stripe != 0 ? stripe : Integer.compare(shardIndex, other.shardIndex);
    }
}
