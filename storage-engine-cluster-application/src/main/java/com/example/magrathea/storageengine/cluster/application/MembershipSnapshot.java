package com.example.magrathea.storageengine.cluster.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable authority snapshot used to fence control-plane publication. */
public record MembershipSnapshot(List<ClusterMember> voters, String topologyEpoch, String policyEpoch) {
    public MembershipSnapshot {
        voters = List.copyOf(voters);
        if (voters.size() != 3) throw new IllegalArgumentException("the first slice requires exactly three voters");
        Set<NodeIdentity> identities = voters.stream().map(ClusterMember::identity).collect(Collectors.toSet());
        if (identities.size() != voters.size()) throw new IllegalArgumentException("voter identities must be unique");
        Set<String> names = voters.stream().map(ClusterMember::name).collect(Collectors.toSet());
        if (names.size() != voters.size()) throw new IllegalArgumentException("voter names must be unique");
        if (topologyEpoch == null || topologyEpoch.isBlank()) throw new IllegalArgumentException("topology epoch is required");
        if (policyEpoch == null || policyEpoch.isBlank()) throw new IllegalArgumentException("policy epoch is required");
    }

    public Set<NodeIdentity> voterIdentities() {
        return voters.stream().map(ClusterMember::identity).collect(Collectors.toUnmodifiableSet());
    }

    public ClusterMember member(NodeIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return voters.stream().filter(v -> v.identity().equals(identity)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown voter " + identity));
    }
}
