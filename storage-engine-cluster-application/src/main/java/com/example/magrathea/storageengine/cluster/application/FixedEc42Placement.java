package com.example.magrathea.storageengine.cluster.application;

import java.util.List;

/** Deterministic fixed A/B/C placement: shard index modulo three selects the failure domain. */
public final class FixedEc42Placement {

    public List<EcShardReference> plan(
            MembershipSnapshot membership, List<PreparedEcShard> preparedShards) {
        if (membership == null) throw new IllegalArgumentException("membership is required");
        if (preparedShards == null || preparedShards.isEmpty()) {
            throw new IllegalArgumentException("prepared shards are required");
        }
        List<ClusterMember> members = membership.voters().stream()
                .sorted(java.util.Comparator.comparing(ClusterMember::identity)).toList();
        if (members.size() != 3 || members.stream().map(ClusterMember::failureDomain).distinct().count() != 3) {
            throw new ControlPlaneException(ControlPlaneException.Code.INVALID_MEMBERSHIP,
                    "fixed distributed EC 4+2 requires exactly three distinct failure domains");
        }
        return preparedShards.stream().sorted()
                .map(shard -> shard.placedAt(
                        members.get(shard.shardIndex() % members.size()).identity()))
                .toList();
    }
}
