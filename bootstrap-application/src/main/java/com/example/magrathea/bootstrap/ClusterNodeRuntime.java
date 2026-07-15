package com.example.magrathea.bootstrap;

import com.example.magrathea.cluster.control.ratis.RatisTlsConfig;
import com.example.magrathea.cluster.control.ratis.SingleNodeRatisVoter;
import com.example.magrathea.cluster.data.grpc.FileLocalArtifactStore;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaClient;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaServer;
import com.example.magrathea.cluster.data.grpc.ReplicaTlsConfig;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferFaultPlan;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferMetrics;
import com.example.magrathea.storageengine.cluster.application.ClusterAntiEntropyScheduler;
import com.example.magrathea.storageengine.cluster.application.ClusterControlPlanePort;
import com.example.magrathea.storageengine.cluster.application.ClusterEcWriteCoordinator;
import com.example.magrathea.storageengine.cluster.application.ClusterMember;
import com.example.magrathea.storageengine.cluster.application.ClusterRepairCoordinator;
import com.example.magrathea.storageengine.cluster.application.ClusterRepairMetrics;
import com.example.magrathea.storageengine.cluster.application.ClusterRepairScheduler;
import com.example.magrathea.storageengine.cluster.application.EcReferencePublicationService;
import com.example.magrathea.storageengine.cluster.application.FixedEc42Placement;
import com.example.magrathea.storageengine.cluster.application.ClusterRepairWorker;
import com.example.magrathea.storageengine.cluster.application.ClusterWriteCoordinator;
import com.example.magrathea.storageengine.cluster.application.LocalArtifactPort;
import com.example.magrathea.storageengine.cluster.application.MembershipSnapshot;
import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import com.example.magrathea.storageengine.cluster.application.ReferencePageQuery;
import com.example.magrathea.storageengine.cluster.application.ReferencePublicationBarrier;
import com.example.magrathea.storageengine.cluster.application.RepairExecutionGate;
import com.example.magrathea.storageengine.cluster.application.ReplicaReadPort;
import com.example.magrathea.storageengine.cluster.application.ReplicaTransferPort;
import com.example.magrathea.storageengine.cluster.application.TransferRequest;
import com.example.magrathea.storageengine.cluster.application.TransferResult;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Spring lifecycle composition for one production JVM, one voter, and one data server. */
public final class ClusterNodeRuntime implements SmartLifecycle, AutoCloseable {
    private final ClusterProfileProperties properties;
    private final MembershipSnapshot membership;
    private final NodeIdentity localIdentity;
    private final ClusterMember localMember;
    private final FileLocalArtifactStore artifacts;
    private final SingleNodeRatisVoter voter;
    private final ClusterControlPlanePort controlPlane;
    private final ReplicaTlsConfig dataTls;
    private final ReplicaTransferFaultPlan transferFaultPlan;
    private final Map<NodeIdentity, GrpcReplicaClient> clients = new HashMap<>();
    private final ReplicaTransferPort transfers;
    private final ReplicaReadPort reads;
    private final ClusterWriteCoordinator coordinator;
    private final ClusterEcWriteCoordinator ecCoordinator;
    private final String processSession;
    private final ClusterRepairMetrics repairMetrics;
    private final ClusterRepairScheduler repairScheduler;
    private final ClusterRepairCoordinator repairCoordinator;
    private final ClusterAntiEntropyScheduler antiEntropyScheduler;
    private volatile GrpcReplicaServer dataServer;
    private volatile boolean running;

    public ClusterNodeRuntime(ClusterProfileProperties properties) throws IOException {
        this(properties, ReplicaTransferFaultPlan.none(), ReferencePublicationBarrier.none(),
                RepairExecutionGate.open());
    }

    public ClusterNodeRuntime(
            ClusterProfileProperties properties,
            ReplicaTransferFaultPlan transferFaultPlan,
            ReferencePublicationBarrier publicationBarrier) throws IOException {
        this(properties, transferFaultPlan, publicationBarrier, RepairExecutionGate.open());
    }

    public ClusterNodeRuntime(
            ClusterProfileProperties properties,
            ReplicaTransferFaultPlan transferFaultPlan,
            ReferencePublicationBarrier publicationBarrier,
            RepairExecutionGate repairExecutionGate) throws IOException {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.transferFaultPlan = Objects.requireNonNull(transferFaultPlan, "transferFaultPlan");
        this.membership = membership(properties);
        this.localIdentity = localIdentity(properties, membership);
        this.localMember = membership.member(localIdentity);
        validateRoots(properties.getRoots());
        Files.createDirectories(properties.getRoots().getRuntime());

        Set<NodeIdentity> voters = membership.voterIdentities();
        ClusterProfileProperties.Material control = properties.getTls().getControl();
        RatisTlsConfig controlTls = new RatisTlsConfig(
                requiredFile(control.getCertificate(), "control certificate"),
                requiredFile(control.getPrivateKey(), "control private key"),
                requiredFile(control.getTrustCertificate(), "control trust certificate"),
                localIdentity,
                voters);
        this.dataTls = new ReplicaTlsConfig(
                requiredFile(properties.getTls().getData().getCertificate(), "data certificate"),
                requiredFile(properties.getTls().getData().getPrivateKey(), "data private key"),
                requiredFile(properties.getTls().getData().getTrustCertificate(), "data trust certificate"),
                localIdentity,
                voters);
        this.artifacts = new FileLocalArtifactStore(
                properties.getRoots().getObjects(), properties.getRoots().getTemporary(), localIdentity);
        this.voter = new SingleNodeRatisVoter(
                membership, localIdentity, properties.getRoots().getIdentity(),
                properties.getRoots().getRatis(), controlTls);
        this.controlPlane = voter.controlPlane();
        for (ClusterMember member : membership.voters()) {
            if (!member.identity().equals(localIdentity)) {
                clients.put(member.identity(), new GrpcReplicaClient(
                        member.dataAddress(), dataTls, member.identity()));
            }
        }
        this.transfers = new ReplicaTransferPort() {
            @Override
            public CompletionStage<TransferResult> stage(
                    TransferRequest request, LocalArtifactPort.Source source) {
                return client(request.targetNode()).stage(request, source);
            }

            @Override
            public CompletionStage<TransferResult> stage(
                    ClusterMember target,
                    TransferRequest request,
                    LocalArtifactPort.Source source) {
                if (!target.identity().equals(request.targetNode())) {
                    throw new IllegalArgumentException(
                            "transfer target and committed member identity differ");
                }
                return client(target.identity()).stage(request, source);
            }
        };
        this.reads = (source, request, sink) ->
                client(source.identity()).read(request, sink);
        Duration transferDeadline = positive(
                properties.getDeadlines().getTransfer(), "transfer deadline");
        this.coordinator = new ClusterWriteCoordinator(
                localIdentity, controlPlane, artifacts, transfers,
                transferDeadline,
                Objects.requireNonNull(publicationBarrier, "publicationBarrier"));
        this.ecCoordinator = new ClusterEcWriteCoordinator(
                localIdentity, controlPlane, artifacts, transfers,
                new FixedEc42Placement(),
                new EcReferencePublicationService(controlPlane),
                publicationBarrier,
                transferDeadline);
        Duration readDeadline = positive(properties.getDeadlines().getRead(), "read deadline");
        this.processSession = UUID.randomUUID().toString();
        this.repairMetrics = new ClusterRepairMetrics();
        ClusterRepairWorker repairWorker = new ClusterRepairWorker(localIdentity, processSession,
                controlPlane, artifacts, reads, readDeadline,
                Objects.requireNonNull(repairExecutionGate, "repairExecutionGate"),
                repairMetrics, Clock.systemUTC());
        this.repairScheduler = new ClusterRepairScheduler(localIdentity, controlPlane, repairWorker,
                Clock.systemUTC(), Duration.ofMillis(250));
        this.repairCoordinator = new ClusterRepairCoordinator(localIdentity, controlPlane, artifacts,
                repairScheduler, repairMetrics, Clock.systemUTC());
        ClusterProfileProperties.AntiEntropy antiEntropy = Objects.requireNonNull(
                properties.getAntiEntropy(), "antiEntropy");
        Duration antiEntropyInterval = positive(
                antiEntropy.getInterval(), "anti-entropy interval");
        int antiEntropyPageSize = antiEntropy.getPageSize();
        new ReferencePageQuery(null, antiEntropyPageSize);
        this.antiEntropyScheduler = new ClusterAntiEntropyScheduler(localIdentity, controlPlane,
                artifacts, repairCoordinator, repairScheduler, antiEntropyInterval,
                antiEntropyPageSize, () -> repairScheduler.status().scanFailures());
    }

    public NodeIdentity localIdentity() { return localIdentity; }
    public LocalArtifactPort artifacts() { return artifacts; }
    public ClusterControlPlanePort controlPlane() { return controlPlane; }
    public ReplicaReadPort reads() { return reads; }
    public ClusterWriteCoordinator coordinator() { return coordinator; }
    public ClusterEcWriteCoordinator ecCoordinator() { return ecCoordinator; }
    public ClusterRepairCoordinator repairCoordinator() { return repairCoordinator; }
    public ClusterRepairMetrics repairMetrics() { return repairMetrics; }
    public ClusterRepairScheduler.Status repairSchedulerStatus() { return repairScheduler.status(); }
    public ClusterAntiEntropyScheduler.Status antiEntropyStatus() {
        return antiEntropyScheduler.status();
    }
    public String processSession() { return processSession; }
    public long publishedOpenCount(String artifactId) { return artifacts.publishedOpenCount(artifactId); }

    @Override
    public synchronized void start() {
        if (running) return;
        try {
            voter.startBlocking();
            dataServer = new GrpcReplicaServer(
                    localMember.dataAddress(), dataTls, artifacts,
                    new ReplicaTransferMetrics(),
                    properties.getDeadlines().getSlowReplicaAcceptance(), transferFaultPlan).start();
            repairScheduler.start();
            antiEntropyScheduler.start();
            running = true;
        } catch (IOException | RuntimeException failure) {
            antiEntropyScheduler.close();
            repairScheduler.close();
            if (dataServer != null) dataServer.close();
            dataServer = null;
            voter.close();
            throw new IllegalStateException("cannot start local cluster node " + localMember.name(), failure);
        }
    }

    /** Stops only this JVM's control voter; the replica data server remains available. */
    public synchronized void stopLocalVoter() {
        voter.close();
    }

    /** Restarts this JVM's control voter from its persisted identity and Ratis roots. */
    public synchronized void startLocalVoter() {
        if (!running) throw new IllegalStateException("cluster node runtime is not running");
        voter.startBlocking();
    }

    public synchronized boolean localVoterRunning() {
        return voter.running();
    }

    public synchronized boolean dataServerRunning() {
        return dataServer != null;
    }

    @Override
    public synchronized void stop() {
        if (!running && dataServer == null) return;
        running = false;
        antiEntropyScheduler.close();
        repairScheduler.close();
        if (dataServer != null) dataServer.close();
        dataServer = null;
        clients.values().forEach(GrpcReplicaClient::close);
        voter.close();
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MIN_VALUE + 100;
    }

    @Override
    public void close() {
        stop();
    }

    private GrpcReplicaClient client(NodeIdentity identity) {
        GrpcReplicaClient client = clients.get(identity);
        if (client == null) {
            throw new IllegalArgumentException(
                    "no remote replica client for fixed member " + identity);
        }
        return client;
    }

    private static MembershipSnapshot membership(ClusterProfileProperties properties) {
        List<ClusterProfileProperties.Peer> configured = properties.getPeers();
        if (configured == null || configured.size() != 3
                || !configured.stream().map(ClusterProfileProperties.Peer::getName)
                .collect(java.util.stream.Collectors.toSet()).equals(Set.of("A", "B", "C"))) {
            throw new IllegalArgumentException("cluster peers must declare fixed A/B/C exactly once");
        }
        List<ClusterMember> members = configured.stream().map(peer -> {
            InetSocketAddress control = address(peer.getControlAddress(), "control address");
            InetSocketAddress data = address(peer.getDataAddress(), "data address");
            return new ClusterMember(
                    peer.getName(), NodeIdentity.parse(peer.getId()),
                    control.getHostString(), control.getPort(),
                    data.getHostString(), data.getPort(),
                    requiredText(peer.getFailureDomain(), "failure domain"));
        }).toList();
        return new MembershipSnapshot(
                members,
                requiredText(properties.getTopologyEpoch(), "topology epoch"),
                requiredText(properties.getPolicyEpoch(), "policy epoch"));
    }

    private static NodeIdentity localIdentity(
            ClusterProfileProperties properties, MembershipSnapshot membership) {
        String configured = requiredText(properties.getNodeId(), "node ID");
        return membership.voters().stream()
                .filter(member -> member.name().equals(configured)
                        || member.identity().toString().equals(configured))
                .map(ClusterMember::identity)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "node ID must match one fixed A/B/C name or UUID"));
    }

    private static InetSocketAddress address(String value, String field) {
        String configured = requiredText(value, field);
        int separator = configured.lastIndexOf(':');
        if (separator < 1 || separator == configured.length() - 1) {
            throw new IllegalArgumentException(field + " must be host:port");
        }
        int port = Integer.parseInt(configured.substring(separator + 1));
        if (port < 1 || port > 65_535) throw new IllegalArgumentException(field + " port is invalid");
        return new InetSocketAddress(configured.substring(0, separator), port);
    }

    private static void validateRoots(ClusterProfileProperties.Roots roots) {
        required(roots.getIdentity(), "identity root");
        required(roots.getRatis(), "Ratis root");
        required(roots.getObjects(), "object root");
        required(roots.getTemporary(), "temporary root");
        required(roots.getRuntime(), "runtime root");
    }

    private static Path required(Path value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static Path requiredFile(Path value, String field) {
        required(value, field);
        if (!Files.isRegularFile(value)) {
            throw new IllegalArgumentException(field + " must name a readable regular file");
        }
        return value;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static java.time.Duration positive(java.time.Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
