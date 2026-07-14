package com.example.magrathea.cluster.control.ratis.ep10;

import com.example.magrathea.cluster.control.ratis.ControlSnapshotCheckpoint;
import com.example.magrathea.cluster.control.ratis.RatisTlsConfig;
import com.example.magrathea.cluster.control.ratis.SingleNodeRatisVoter;
import com.example.magrathea.cluster.data.grpc.FileLocalArtifactStore;
import com.example.magrathea.cluster.data.grpc.GrpcReplicaClient;
import com.example.magrathea.cluster.data.grpc.ReplicaTlsConfig;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferLimits;
import com.example.magrathea.storageengine.cluster.application.BucketNamespace;
import com.example.magrathea.storageengine.cluster.application.ClusterControlPlanePort;
import com.example.magrathea.storageengine.cluster.application.ClusterMember;
import com.example.magrathea.storageengine.cluster.application.ClusterRepairMetrics;
import com.example.magrathea.storageengine.cluster.application.ClusterRepairScheduler;
import com.example.magrathea.storageengine.cluster.application.ClusterRepairWorker;
import com.example.magrathea.storageengine.cluster.application.LocalArtifactPort;
import com.example.magrathea.storageengine.cluster.application.MembershipSnapshot;
import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import com.example.magrathea.storageengine.cluster.application.ObjectReferenceGeneration;
import com.example.magrathea.storageengine.cluster.application.PublicationProposal;
import com.example.magrathea.storageengine.cluster.application.RepairCommandResult;
import com.example.magrathea.storageengine.cluster.application.RepairCommands;
import com.example.magrathea.storageengine.cluster.application.RepairExecutionGate;
import com.example.magrathea.storageengine.cluster.application.RepairJob;
import com.example.magrathea.storageengine.cluster.application.RepairJobId;
import com.example.magrathea.storageengine.cluster.application.RepairJobQuery;
import com.example.magrathea.storageengine.cluster.application.ReplicaReadPort;
import com.example.magrathea.storageengine.cluster.application.TransferRequest;
import com.example.magrathea.storageengine.cluster.application.TransferResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Independent B JVM used only by the REQ-CLUSTER-024 Cucumber harness. */
public final class ReqCluster024RepairProcess {
    private static final NodeIdentity A = NodeIdentity.parse(
            "11111111-1111-4111-8111-111111111111");
    private static final NodeIdentity B = NodeIdentity.parse(
            "22222222-2222-4222-8222-222222222222");
    private static final NodeIdentity C = NodeIdentity.parse(
            "33333333-3333-4333-8333-333333333333");
    private static final ReplicaTransferLimits FRAME_LIMITS = new ReplicaTransferLimits(
            1, 1, 64, 1_048_576, 1, Duration.ofSeconds(30), Duration.ofSeconds(10));
    private static final Duration READ_DEADLINE = Duration.ofSeconds(20);

    private ReqCluster024RepairProcess() { }

    public static void main(String[] arguments) {
        RuntimeState runtime = null;
        try {
            Arguments args = new Arguments(arguments);
            runtime = new RuntimeState(args);
            RuntimeState closing = runtime;
            Runtime.getRuntime().addShutdownHook(new Thread(closing::close,
                    "req-cluster-024-b-shutdown"));
            runtime.run();
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            if (runtime != null) runtime.close();
            System.exit(1);
        }
    }

    private static final class RuntimeState implements AutoCloseable {
        private final Arguments args;
        private final Path runRoot;
        private final Path evidenceRoot;
        private final Path checkpointReached;
        private final Path checkpointRelease;
        private final Path snapshotRequest;
        private final Path snapshotReached;
        private final Path snapshotFailed;
        private final Path eventLog;
        private final Path rpcLog;
        private final Path controlLog;
        private final Path statusFile;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicLong eventSequence = new AtomicLong();
        private SingleNodeRatisVoter voter;
        private GrpcReplicaClient client;
        private ClusterRepairScheduler scheduler;
        private ClusterRepairMetrics metrics;

        private RuntimeState(Arguments args) throws Exception {
            this.args = args;
            runRoot = args.path("run-root");
            evidenceRoot = args.path("evidence-root");
            Files.createDirectories(runRoot);
            Files.createDirectories(evidenceRoot);
            checkpointReached = runRoot.resolve("checkpoint.reached");
            checkpointRelease = runRoot.resolve("checkpoint.release");
            snapshotRequest = runRoot.resolve("snapshot.request");
            snapshotReached = runRoot.resolve("snapshot.reached");
            snapshotFailed = runRoot.resolve("snapshot.failed");
            eventLog = evidenceRoot.resolve("checkpoints.log");
            rpcLog = evidenceRoot.resolve("rpc-attempts.log");
            controlLog = evidenceRoot.resolve("control-operations.log");
            statusFile = runRoot.resolve("status.properties");
        }

        private void run() throws Exception {
            MembershipSnapshot membership = membership();
            Path certificate = args.path("certificate");
            Path key = args.path("private-key");
            Path ca = args.path("ca-certificate");
            RatisTlsConfig controlTls = new RatisTlsConfig(
                    certificate, key, ca, B, Set.of(A, B, C));
            ControlSnapshotCheckpoint snapshotCheckpoint = temporary -> {
                if (!Files.exists(snapshotRequest)) return;
                byte[] header = Files.readAllBytes(temporary);
                int version = header.length < Integer.BYTES
                        ? -1 : ByteBuffer.wrap(header, 0, Integer.BYTES).getInt();
                writeAtomically(snapshotReached,
                        "version=" + version + "\npath=" + temporary + "\npid="
                                + ProcessHandle.current().pid() + "\n");
                throw new IOException(
                        "REQ-CLUSTER-024 interrupted versioned snapshot installation");
            };
            voter = new SingleNodeRatisVoter(
                    membership, B, args.path("identity-root"), args.path("ratis-root"),
                    controlTls, snapshotCheckpoint);
            voter.startBlocking();

            ReplicaTlsConfig dataTls = new ReplicaTlsConfig(
                    certificate, key, ca, B, Set.of(A, B, C));
            client = new GrpcReplicaClient(
                    new InetSocketAddress("127.0.0.1", 19903), dataTls, C, FRAME_LIMITS);
            FileLocalArtifactStore artifacts = new FileLocalArtifactStore(
                    args.path("objects-root"), args.path("temporary-root"), B);
            String selectedCheckpoint = args.value("checkpoint");
            FileRepairGate gate = new FileRepairGate(
                    selectedCheckpoint.equals("OPEN") ? null
                            : RepairExecutionGate.Checkpoint.valueOf(selectedCheckpoint),
                    checkpointReached, checkpointRelease, eventLog, eventSequence);
            RecordingControlPlane control = new RecordingControlPlane(
                    voter.controlPlane(), controlLog, eventSequence, gate);
            ReplicaReadPort reads = new RecordingGrpcReads(client, rpcLog, eventSequence,
                    certificate);
            metrics = new ClusterRepairMetrics();
            Instant processTime = Instant.parse(args.value("clock"));
            ClusterRepairWorker worker = new ClusterRepairWorker(
                    B, args.value("session"), control, artifacts, reads, READ_DEADLINE,
                    gate, metrics, Clock.fixed(processTime, ZoneOffset.UTC));
            scheduler = new ClusterRepairScheduler(
                    B, control, worker, Clock.fixed(processTime, ZoneOffset.UTC),
                    Duration.ofMillis(100));
            writeStatus("READY");
            scheduler.start();

            while (!closed.get()) {
                if (Files.exists(snapshotRequest) && !Files.exists(snapshotFailed)) {
                    try {
                        voter.snapshot();
                        writeAtomically(snapshotFailed,
                                "unexpected-success=true\npid="
                                        + ProcessHandle.current().pid() + "\n");
                    } catch (RuntimeException expected) {
                        writeAtomically(snapshotFailed,
                                "interrupted=true\nerror="
                                        + expected.getClass().getSimpleName() + "\npid="
                                        + ProcessHandle.current().pid() + "\n");
                    }
                }
                writeStatus("RUNNING");
                Thread.sleep(20);
            }
        }

        private void writeStatus(String lifecycle) throws IOException {
            ClusterRepairMetrics.Snapshot snapshot = metrics == null
                    ? new ClusterRepairMetrics().snapshot() : metrics.snapshot();
            long applied = voter == null ? -1 : voter.lastAppliedIndex();
            writeAtomically(statusFile,
                    "lifecycle=" + lifecycle + "\n"
                            + "pid=" + ProcessHandle.current().pid() + "\n"
                            + "lastApplied=" + applied + "\n"
                            + "alreadyValid=" + snapshot.alreadyValid() + "\n"
                            + "claims=" + snapshot.claims() + "\n"
                            + "fetches=" + snapshot.fetches() + "\n"
                            + "publications=" + snapshot.publications() + "\n");
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            if (scheduler != null) scheduler.close();
            if (client != null) client.close();
            if (voter != null) voter.close();
        }
    }

    private static final class FileRepairGate implements RepairExecutionGate {
        private final RepairExecutionGate.Checkpoint selected;
        private final Path reached;
        private final Path release;
        private final Path eventLog;
        private final AtomicLong sequence;
        private final AtomicBoolean selectedOnce = new AtomicBoolean();

        private FileRepairGate(RepairExecutionGate.Checkpoint selected, Path reached,
                Path release, Path eventLog, AtomicLong sequence) {
            this.selected = selected;
            this.reached = reached;
            this.release = release;
            this.eventLog = eventLog;
            this.sequence = sequence;
        }

        @Override
        public Mono<Void> awaitClaimPermission() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> await(RepairExecutionGate.Checkpoint checkpoint,
                RepairExecutionGate.Observation observation) {
            append(eventLog, sequence.incrementAndGet() + "\tCHECKPOINT\t" + checkpoint
                    + "\t" + observation.claimGeneration() + "\t"
                    + observation.stagedBytes() + "\t"
                    + ProcessHandle.current().pid());
            if (checkpoint != selected || !selectedOnce.compareAndSet(false, true)) {
                return Mono.empty();
            }
            try {
                writeAtomically(reached,
                        "checkpoint=" + checkpoint + "\nclaimGeneration="
                                + observation.claimGeneration() + "\nstagedBytes="
                                + observation.stagedBytes() + "\njobId="
                                + observation.jobId() + "\npid="
                                + ProcessHandle.current().pid() + "\n");
            } catch (IOException failure) {
                return Mono.error(failure);
            }
            return Mono.fromRunnable(() -> {
                        while (!Files.exists(release)) {
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(
                                        "repair checkpoint interrupted", interrupted);
                            }
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic()).then();
        }

        @Override
        public void awaitSynchronously(RepairExecutionGate.Checkpoint checkpoint,
                RepairExecutionGate.Observation observation) {
            append(eventLog, sequence.incrementAndGet() + "\tCHECKPOINT\t" + checkpoint
                    + "\t" + observation.claimGeneration() + "\t"
                    + observation.stagedBytes() + "\t"
                    + ProcessHandle.current().pid());
            if (checkpoint != selected || !selectedOnce.compareAndSet(false, true)) {
                return;
            }
            try {
                writeAtomically(reached,
                        "checkpoint=" + checkpoint + "\nclaimGeneration="
                                + observation.claimGeneration() + "\nstagedBytes="
                                + observation.stagedBytes() + "\njobId="
                                + observation.jobId() + "\npid="
                                + ProcessHandle.current().pid() + "\n");
            } catch (IOException failure) {
                throw new IllegalStateException("cannot write checkpoint evidence", failure);
            }
            while (!Files.exists(release)) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("repair checkpoint interrupted",
                            interrupted);
                }
            }
        }
    }

    private static final class RecordingGrpcReads implements ReplicaReadPort {
        private final GrpcReplicaClient client;
        private final Path rpcLog;
        private final AtomicLong sequence;
        private final Path certificate;

        private RecordingGrpcReads(GrpcReplicaClient client, Path rpcLog,
                AtomicLong sequence, Path certificate) {
            this.client = client;
            this.rpcLog = rpcLog;
            this.sequence = sequence;
            this.certificate = certificate;
        }

        @Override
        public CompletionStage<TransferResult> fetch(ClusterMember source,
                TransferRequest request, LocalArtifactPort.RepairSink sink) {
            if (!source.identity().equals(C)
                    || !source.dataAddress().equals(
                            new InetSocketAddress("127.0.0.1", 19903))) {
                throw new AssertionError("repair selected a source other than fixed source C");
            }
            long generation = Long.parseLong(request.operationId()
                    .substring(request.operationId().lastIndexOf('-') + 1));
            append(rpcLog, sequence.incrementAndGet() + "\tRPC\t" + generation + "\t"
                    + request.operationId() + "\t" + ProcessHandle.current().pid()
                    + "\t" + certificate);
            return client.read(request, sink);
        }
    }

    private static final class RecordingControlPlane implements ClusterControlPlanePort {
        private final ClusterControlPlanePort delegate;
        private final Path log;
        private final AtomicLong sequence;
        private final FileRepairGate repairGate;

        private RecordingControlPlane(ClusterControlPlanePort delegate, Path log,
                AtomicLong sequence, FileRepairGate repairGate) {
            this.delegate = delegate;
            this.log = log;
            this.sequence = sequence;
            this.repairGate = repairGate;
        }

        private void record(String operation) {
            append(log, sequence.incrementAndGet() + "\tCONTROL\t" + operation + "\t"
                    + ProcessHandle.current().pid());
        }

        @Override public Mono<MembershipSnapshot> membership() { return delegate.membership(); }
        @Override public Mono<BucketNamespace> createBucket(String bucket) {
            return delegate.createBucket(bucket);
        }
        @Override public Mono<BucketNamespace> bucket(String bucket) {
            return delegate.bucket(bucket);
        }
        @Override public Flux<BucketNamespace> buckets() { return delegate.buckets(); }
        @Override public Mono<ObjectReferenceGeneration> compareAndPublish(
                PublicationProposal proposal) { return delegate.compareAndPublish(proposal); }
        @Override public Mono<ObjectReferenceGeneration> objectReference(
                String bucket, String objectKey) {
            return delegate.objectReference(bucket, objectKey);
        }
        @Override public Mono<RepairCommandResult> ensureRepair(RepairCommands.Ensure command) {
            return delegate.ensureRepair(command);
        }
        @Override public Mono<RepairJob> repairJob(RepairJobId jobId) {
            return delegate.repairJob(jobId);
        }
        @Override public Flux<RepairJob> repairJobs(RepairJobQuery query) {
            record("REPAIR_QUERY");
            return delegate.repairJobs(query);
        }
        @Override public Mono<RepairCommandResult> claimRepair(RepairCommands.Claim command) {
            record("CLAIM");
            return delegate.claimRepair(command);
        }
        @Override public Mono<RepairCommandResult> renewRepair(RepairCommands.Renew command) {
            return delegate.renewRepair(command);
        }
        @Override public Mono<RepairCommandResult> retryRepair(RepairCommands.Retry command) {
            return delegate.retryRepair(command);
        }
        @Override public Mono<RepairCommandResult> blockRepair(RepairCommands.Block command) {
            return delegate.blockRepair(command);
        }
        @Override public Mono<RepairCommandResult> succeedRepair(
                RepairCommands.Succeed command) {
            return delegate.succeedRepair(command).flatMap(result -> {
                if (!result.accepted()) return Mono.just(result);
                RepairExecutionGate.Observation committed =
                        new RepairExecutionGate.Observation(
                                command.jobId(), command.claimGeneration(),
                                command.durableLength());
                return repairGate.await(
                                RepairExecutionGate.Checkpoint
                                        .COMPLETION_COMMITTED_BEFORE_ACKNOWLEDGEMENT,
                                committed)
                        .thenReturn(result);
            });
        }
        @Override public Mono<RepairCommandResult> obsoleteRepair(
                RepairCommands.Obsolete command) {
            return delegate.obsoleteRepair(command);
        }
        @Override public Mono<RepairCommandResult> reevaluateRepair(
                RepairCommands.Reevaluate command) {
            return delegate.reevaluateRepair(command);
        }
    }

    private static MembershipSnapshot membership() {
        return new MembershipSnapshot(List.of(
                new ClusterMember("A", A, "127.0.0.1", 19801),
                new ClusterMember("B", B, "127.0.0.1", 19802),
                new ClusterMember("C", C, "127.0.0.1", 19803)),
                "topology-1", "policy-1");
    }

    private static synchronized void append(Path path, String line) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot append REQ-CLUSTER-024 evidence", failure);
        }
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-"
                + ProcessHandle.current().pid() + "-" + Thread.currentThread().threadId());
        Files.writeString(temporary, content, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static final class Arguments {
        private final Map<String, String> values;

        private Arguments(String[] arguments) {
            Map<String, String> parsed = new java.util.LinkedHashMap<>();
            for (String argument : arguments) {
                if (!argument.startsWith("--") || !argument.contains("=")) {
                    throw new IllegalArgumentException("invalid argument " + argument);
                }
                int separator = argument.indexOf('=');
                parsed.put(argument.substring(2, separator),
                        argument.substring(separator + 1));
            }
            values = Map.copyOf(parsed);
        }

        private String value(String name) {
            String value = values.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value;
        }

        private Path path(String name) {
            return Path.of(value(name));
        }
    }
}
