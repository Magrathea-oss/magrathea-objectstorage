package com.example.magrathea.storageengine.cluster.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Locally durable EC 4+2 object artifacts before distributed publication. */
public record PreparedEcObject(
        String operationId,
        long logicalLength,
        String sha256,
        List<PreparedEcShard> shards,
        ClusterObjectMetadata metadata) {

    public PreparedEcObject {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operation ID is required");
        }
        if (logicalLength < 1) throw new IllegalArgumentException("logical length must be positive");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("lowercase object SHA-256 is required");
        }
        if (shards == null || shards.isEmpty()) throw new IllegalArgumentException("EC shards are required");
        shards = shards.stream().sorted().toList();
        if (shards.size() != EcShardReference.TOTAL_SHARDS
                || shards.get(0).stripeIndex() != 0
                || shards.get(shards.size() - 1).stripeIndex() != 0) {
            throw new IllegalArgumentException(
                    "bounded distributed EC publication requires exactly one stripe");
        }
        if (metadata == null) metadata = ClusterObjectMetadata.EMPTY;

        Set<String> artifactIds = new HashSet<>();
        long stripeCount = 1;
        long declaredLength = 0;
        for (long stripeIndex = 0; stripeIndex < stripeCount; stripeIndex++) {
            final long expectedStripe = stripeIndex;
            List<PreparedEcShard> stripe = shards.stream()
                    .filter(shard -> shard.stripeIndex() == expectedStripe).toList();
            if (stripe.size() != EcShardReference.TOTAL_SHARDS) {
                throw new IllegalArgumentException("every EC 4+2 stripe requires exactly six shards");
            }
            long stripeLogicalLength = stripe.get(0).stripeLogicalLength();
            Set<Integer> indices = new HashSet<>();
            for (PreparedEcShard shard : stripe) {
                if (!operationId.equals(shard.artifact().operationId())) {
                    throw new IllegalArgumentException("every shard must share the object operation ID");
                }
                if (!artifactIds.add(shard.artifact().artifactId())) {
                    throw new IllegalArgumentException("EC artifact IDs must be unique");
                }
                if (!indices.add(shard.shardIndex())
                        || shard.stripeLogicalLength() != stripeLogicalLength) {
                    throw new IllegalArgumentException("EC stripe layout is duplicate or contradictory");
                }
            }
            if (indices.size() != EcShardReference.TOTAL_SHARDS) {
                throw new IllegalArgumentException("EC stripe must bind shard indices 0 through 5");
            }
            declaredLength = Math.addExact(declaredLength, stripeLogicalLength);
        }
        if (declaredLength != logicalLength) {
            throw new IllegalArgumentException("object logical length contradicts its EC stripes");
        }
    }

    public PreparedEcObject(
            String operationId, long logicalLength, String sha256,
            List<PreparedEcShard> shards) {
        this(operationId, logicalLength, sha256, shards, ClusterObjectMetadata.EMPTY);
    }
}
