package com.example.magrathea.cluster.control.ratis;

import com.example.magrathea.storageengine.cluster.application.*;
import org.apache.ratis.RaftConfigKeys;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.grpc.GrpcTlsConfig;
import org.apache.ratis.protocol.*;
import org.apache.ratis.rpc.SupportedRpcType;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.util.TimeDuration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Lifecycle owner for the EP-10 fixed localhost A/B/C voter group. Dynamic membership is intentionally absent. */
public final class FixedThreeNodeRatisCluster implements AutoCloseable {
    private final MembershipSnapshot membership;
    private final Map<NodeIdentity, Path> identityRoots;
    private final Map<NodeIdentity, Path> ratisRoots;
    private final Map<NodeIdentity, RatisTlsConfig> tlsConfigs;
    private final Map<NodeIdentity, ControlSnapshotCheckpoint> snapshotCheckpoints;
    private final Map<NodeIdentity, SingleNodeRatisVoter> voters = new LinkedHashMap<>();
    private final RaftGroup group;
    private final RaftProperties clientProperties;
    private final Parameters clientParameters;

    public FixedThreeNodeRatisCluster(MembershipSnapshot membership, Map<NodeIdentity, Path> identityRoots,
                                      Map<NodeIdentity, Path> ratisRoots,
                                      Map<NodeIdentity, RatisTlsConfig> tlsConfigs,
                                      RatisTlsConfig clientTlsConfig) {
        this(membership, identityRoots, ratisRoots, tlsConfigs, clientTlsConfig,
                openSnapshotCheckpoints(membership));
    }

    public FixedThreeNodeRatisCluster(MembershipSnapshot membership, Map<NodeIdentity, Path> identityRoots,
                                      Map<NodeIdentity, Path> ratisRoots,
                                      Map<NodeIdentity, RatisTlsConfig> tlsConfigs,
                                      RatisTlsConfig clientTlsConfig,
                                      Map<NodeIdentity, ControlSnapshotCheckpoint> snapshotCheckpoints) {
        this.membership = Objects.requireNonNull(membership);
        this.identityRoots = Map.copyOf(identityRoots); this.ratisRoots = Map.copyOf(ratisRoots);
        this.tlsConfigs = Map.copyOf(Objects.requireNonNull(tlsConfigs,
                "cluster mode requires Ratis mTLS configuration for every voter"));
        this.snapshotCheckpoints = Map.copyOf(Objects.requireNonNull(
                snapshotCheckpoints, "snapshotCheckpoints"));
        Set<NodeIdentity> voters = membership.voterIdentities();
        if (!identityRoots.keySet().equals(voters) || !ratisRoots.keySet().equals(voters)
                || !this.tlsConfigs.keySet().equals(voters)
                || !this.snapshotCheckpoints.keySet().equals(voters))
            throw new ControlPlaneException(ControlPlaneException.Code.INVALID_MEMBERSHIP,
                    "every fixed voter requires identity, Ratis, mTLS, and snapshot checkpoint configuration");
        for (Map.Entry<NodeIdentity, RatisTlsConfig> entry : this.tlsConfigs.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().localIdentity())
                    || !entry.getValue().acceptedPeers().equals(voters)) {
                throw new IllegalArgumentException("Ratis mTLS identity policy must bind each certificate to its voter UUID and admit exactly fixed A/B/C");
            }
        }
        Objects.requireNonNull(clientTlsConfig, "cluster mode requires Ratis client/admin mTLS configuration");
        if (!voters.contains(clientTlsConfig.localIdentity()) || !clientTlsConfig.acceptedPeers().equals(voters)) {
            throw new IllegalArgumentException("Ratis client/admin mTLS identity must be a fixed voter and admit exactly fixed A/B/C");
        }
        this.group = SingleNodeRatisVoter.group(membership);
        this.clientProperties = SingleNodeRatisVoter.baseProperties();
        this.clientParameters = SingleNodeRatisVoter.tlsParameters(clientTlsConfig, false);
    }

    public Mono<Void> start(List<NodeIdentity> seedOrder) {
        return Mono.fromRunnable(() -> startBlocking(seedOrder)).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public synchronized void startBlocking(List<NodeIdentity> seedOrder) {
        if (!new HashSet<>(seedOrder).equals(membership.voterIdentities()) || seedOrder.size() != 3)
            throw new ControlPlaneException(ControlPlaneException.Code.INVALID_MEMBERSHIP, "seed order must name fixed A/B/C once");
        for (NodeIdentity id : seedOrder) {
            if (voters.containsKey(id)) continue;
            SingleNodeRatisVoter voter = new SingleNodeRatisVoter(
                    membership, id, identityRoots.get(id), ratisRoots.get(id), tlsConfigs.get(id),
                    snapshotCheckpoints.get(id));
            try {
                voter.startBlocking();
                voters.put(id, voter);
            } catch (RuntimeException failure) {
                voter.close();
                close();
                throw failure;
            }
        }
    }

    public RatisControlPlaneAdapter controlPlane() { return new RatisControlPlaneAdapter(group, clientProperties, clientParameters); }
    public RatisControlPlaneAdapter controlPlane(RatisTlsConfig clientTlsConfig) {
        Objects.requireNonNull(clientTlsConfig, "cluster mode forbids a plaintext Ratis client");
        return new RatisControlPlaneAdapter(group, clientProperties,
                SingleNodeRatisVoter.tlsParameters(clientTlsConfig, false));
    }
    public MembershipSnapshot configuredMembership() { return membership; }
    public Set<NodeIdentity> runningVoters() { return Set.copyOf(voters.keySet()); }
    public synchronized Optional<NodeIdentity> leaderIdentity() {
        return voters.entrySet().stream().filter(entry -> entry.getValue().leader()).map(Map.Entry::getKey).findFirst();
    }
    public synchronized long currentTerm(NodeIdentity id) {
        SingleNodeRatisVoter voter = voters.get(id); return voter == null ? -1 : voter.currentTerm();
    }
    public synchronized long lastAppliedIndex(NodeIdentity id) {
        SingleNodeRatisVoter voter = voters.get(id); return voter == null ? -1 : voter.lastAppliedIndex();
    }

    /** Transfers leadership between two live members without changing fixed membership. */
    public synchronized void transferLeadership(
            NodeIdentity expectedLeader, NodeIdentity newLeader) {
        Objects.requireNonNull(expectedLeader, "expectedLeader");
        Objects.requireNonNull(newLeader, "newLeader");
        if (expectedLeader.equals(newLeader)) return;
        if (!voters.keySet().containsAll(Set.of(expectedLeader, newLeader))) {
            throw new ControlPlaneException(ControlPlaneException.Code.QUORUM_UNAVAILABLE,
                    "leadership transfer requires both fixed voters to be running");
        }
        Optional<NodeIdentity> current = leaderIdentity();
        if (current.isEmpty() || !current.get().equals(expectedLeader)) {
            throw new ControlPlaneException(ControlPlaneException.Code.QUORUM_UNAVAILABLE,
                    "expected leader is not currently leader-ready");
        }
        try (RaftClient client = RaftClient.newBuilder().setRaftGroup(group)
                .setLeaderId(RaftPeerId.valueOf(expectedLeader.toString()))
                .setProperties(clientProperties).setParameters(clientParameters).build()) {
            RaftClientReply reply = client.admin().transferLeadership(
                    RaftPeerId.valueOf(newLeader.toString()), 10_000L);
            if (!reply.isSuccess()) {
                throw new IOException(String.valueOf(reply.getException()));
            }
        } catch (IOException failure) {
            throw new ControlPlaneException(ControlPlaneException.Code.QUORUM_UNAVAILABLE,
                    "live Ratis leadership transfer failed", failure);
        }
    }

    public Mono<Void> stop(NodeIdentity id) {
        return Mono.fromRunnable(() -> stopBlocking(id)).subscribeOn(Schedulers.boundedElastic()).then();
    }
    public synchronized void stopBlocking(NodeIdentity id) {
        SingleNodeRatisVoter voter = voters.remove(id);
        if (voter != null) voter.close();
    }
    public synchronized long snapshot(NodeIdentity id) {
        SingleNodeRatisVoter voter = voters.get(id);
        if (voter == null) {
            throw new ControlPlaneException(ControlPlaneException.Code.INTERNAL_FAILURE,
                    "cannot snapshot a stopped voter");
        }
        return voter.snapshot();
    }
    @Override public synchronized void close() {
        List<SingleNodeRatisVoter> closing = new ArrayList<>(voters.values());
        voters.clear();
        Collections.reverse(closing);
        closing.forEach(SingleNodeRatisVoter::close);
    }

    private static Map<NodeIdentity, ControlSnapshotCheckpoint> openSnapshotCheckpoints(
            MembershipSnapshot membership) {
        Map<NodeIdentity, ControlSnapshotCheckpoint> checkpoints = new LinkedHashMap<>();
        Objects.requireNonNull(membership, "membership").voterIdentities()
                .forEach(identity -> checkpoints.put(
                        identity, ControlSnapshotCheckpoint.open()));
        return checkpoints;
    }
}
