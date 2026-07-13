package com.example.magrathea.cluster.control.ratis;

import com.example.magrathea.storageengine.cluster.application.ClusterMember;
import com.example.magrathea.storageengine.cluster.application.ControlPlaneException;
import com.example.magrathea.storageengine.cluster.application.MembershipSnapshot;
import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import org.apache.ratis.RaftConfigKeys;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.grpc.GrpcTlsConfig;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.rpc.SupportedRpcType;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.util.TimeDuration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Owns exactly one local Ratis voter while clients address the complete fixed group. */
public final class SingleNodeRatisVoter implements AutoCloseable {
    static final RaftGroupId GROUP_ID = RaftGroupId.valueOf(
            UUID.fromString("eeeeeeee-0010-4000-8000-000000000010"));

    private final MembershipSnapshot membership;
    private final NodeIdentity localIdentity;
    private final Path identityRoot;
    private final Path ratisRoot;
    private final RatisTlsConfig tls;
    private final RaftGroup group;
    private final RaftProperties clientProperties;
    private final Parameters clientParameters;
    private final PersistedNodeIdentityStore identityStore = new PersistedNodeIdentityStore();
    private RaftServer server;
    private ClusterControlStateMachine stateMachine;

    public SingleNodeRatisVoter(
            MembershipSnapshot membership,
            NodeIdentity localIdentity,
            Path identityRoot,
            Path ratisRoot,
            RatisTlsConfig tls) {
        this.membership = Objects.requireNonNull(membership, "membership");
        this.localIdentity = Objects.requireNonNull(localIdentity, "localIdentity");
        this.identityRoot = Objects.requireNonNull(identityRoot, "identityRoot");
        this.ratisRoot = Objects.requireNonNull(ratisRoot, "ratisRoot");
        this.tls = Objects.requireNonNull(tls, "cluster mode requires Ratis mTLS");
        Set<NodeIdentity> voters = membership.voterIdentities();
        if (voters.size() != 3 || !voters.contains(localIdentity)) {
            throw new ControlPlaneException(ControlPlaneException.Code.INVALID_MEMBERSHIP,
                    "local voter must belong to fixed A/B/C membership");
        }
        if (!localIdentity.equals(tls.localIdentity()) || !voters.equals(tls.acceptedPeers())) {
            throw new IllegalArgumentException(
                    "Ratis mTLS identity must bind the local certificate and admit exactly fixed A/B/C");
        }
        this.group = group(membership);
        this.clientProperties = baseProperties();
        this.clientParameters = tlsParameters(tls, false);
    }

    public Mono<Void> start() {
        return Mono.fromRunnable(this::startBlocking).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public synchronized void startBlocking() {
        if (server != null) return;
        identityStore.initializeOrRecover(identityRoot, localIdentity);
        ClusterMember member = membership.member(localIdentity);
        RaftProperties properties = baseProperties();
        GrpcConfigKeys.Server.setHost(properties, member.host());
        GrpcConfigKeys.Server.setPort(properties, member.controlPort());
        GrpcConfigKeys.Client.setHost(properties, member.host());
        GrpcConfigKeys.Client.setPort(properties, clientPort(member.controlPort()));
        GrpcConfigKeys.Admin.setHost(properties, member.host());
        GrpcConfigKeys.Admin.setPort(properties, adminPort(member.controlPort()));
        RaftServerConfigKeys.setStorageDir(properties, List.of(ratisRoot.toFile()));
        RaftServerConfigKeys.Snapshot.setAutoTriggerEnabled(properties, true);
        RaftServerConfigKeys.Snapshot.setAutoTriggerThreshold(properties, 8);
        RaftServerConfigKeys.Snapshot.setTriggerWhenStopEnabled(properties, true);
        stateMachine = new ClusterControlStateMachine(membership);
        try {
            server = RaftServer.newBuilder()
                    .setServerId(RaftPeerId.valueOf(localIdentity.toString()))
                    .setGroup(group)
                    .setProperties(properties)
                    .setParameters(tlsParameters(tls, true))
                    .setStateMachine(stateMachine)
                    .setOption(org.apache.ratis.server.storage.RaftStorage.StartupOption.RECOVER)
                    .build();
            server.start();
        } catch (IOException failure) {
            close();
            throw new ControlPlaneException(ControlPlaneException.Code.INTERNAL_FAILURE,
                    "cannot start local Ratis voter " + member.name(), failure);
        }
    }

    public RatisControlPlaneAdapter controlPlane() {
        return new RatisControlPlaneAdapter(group, clientProperties, clientParameters);
    }

    public synchronized boolean running() {
        return server != null;
    }

    public synchronized boolean leader() {
        if (server == null) return false;
        try { return server.getDivision(GROUP_ID).getInfo().isLeaderReady(); }
        catch (IOException unavailable) { return false; }
    }

    public synchronized long currentTerm() {
        if (server == null) return -1;
        try { return server.getDivision(GROUP_ID).getInfo().getCurrentTerm(); }
        catch (IOException unavailable) { return -1; }
    }

    public synchronized long lastAppliedIndex() {
        if (server == null) return -1;
        try { return server.getDivision(GROUP_ID).getInfo().getLastAppliedIndex(); }
        catch (IOException unavailable) { return -1; }
    }

    public synchronized long snapshot() {
        if (stateMachine == null) {
            throw new ControlPlaneException(ControlPlaneException.Code.INTERNAL_FAILURE,
                    "local Ratis voter is not running");
        }
        try {
            return stateMachine.takeSnapshot();
        } catch (IOException failure) {
            throw new ControlPlaneException(ControlPlaneException.Code.INTERNAL_FAILURE,
                    "cannot snapshot local voter", failure);
        }
    }

    @Override
    public synchronized void close() {
        RaftServer closing = server;
        server = null;
        stateMachine = null;
        if (closing != null) {
            try {
                closing.close();
            } catch (IOException ignored) {
                // Shutdown remains best-effort; the JVM no longer owns this voter.
            }
        }
    }

    static RaftGroup group(MembershipSnapshot membership) {
        List<RaftPeer> peers = membership.voters().stream()
                .map(member -> RaftPeer.newBuilder()
                        .setId(member.identity().toString())
                        .setAddress(member.host() + ":" + member.controlPort())
                        .setClientAddress(member.host() + ":" + clientPort(member.controlPort()))
                        .setAdminAddress(member.host() + ":" + adminPort(member.controlPort()))
                        .build())
                .toList();
        return RaftGroup.valueOf(GROUP_ID, peers);
    }

    static Parameters tlsParameters(RatisTlsConfig config, boolean authorizePeers) {
        Parameters parameters = new Parameters();
        GrpcTlsConfig grpcTls = config.grpcTlsConfig();
        GrpcConfigKeys.TLS.setConf(parameters, grpcTls);
        GrpcConfigKeys.Server.setTlsConf(parameters, grpcTls);
        GrpcConfigKeys.Client.setTlsConf(parameters, grpcTls);
        GrpcConfigKeys.Admin.setTlsConf(parameters, grpcTls);
        if (authorizePeers) {
            GrpcConfigKeys.Server.setServicesCustomizer(parameters,
                    (builder, types) -> builder.intercept(
                            RatisPeerIdentity.allowOnly(config.acceptedPeers())));
        }
        return parameters;
    }

    static RaftProperties baseProperties() {
        RaftProperties properties = new RaftProperties();
        RaftConfigKeys.Rpc.setType(properties, SupportedRpcType.GRPC);
        RaftServerConfigKeys.Rpc.setTimeoutMin(properties,
                TimeDuration.valueOf(250, TimeUnit.MILLISECONDS));
        RaftServerConfigKeys.Rpc.setTimeoutMax(properties,
                TimeDuration.valueOf(500, TimeUnit.MILLISECONDS));
        RaftServerConfigKeys.Rpc.setRequestTimeout(properties,
                TimeDuration.valueOf(2, TimeUnit.SECONDS));
        return properties;
    }

    private static int clientPort(int serverPort) {
        return serverPort - 100;
    }

    private static int adminPort(int serverPort) {
        return serverPort - 200;
    }
}
