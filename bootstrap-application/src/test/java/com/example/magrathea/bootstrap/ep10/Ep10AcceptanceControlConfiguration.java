package com.example.magrathea.bootstrap.ep10;

import com.example.magrathea.bootstrap.ClusterNodeRuntime;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferFaultPlan;
import com.example.magrathea.storageengine.cluster.application.ClusterObjectMetadata;
import com.example.magrathea.storageengine.cluster.application.ControlPlaneException;
import com.example.magrathea.storageengine.cluster.application.LocalArtifactPort;
import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import com.example.magrathea.storageengine.cluster.application.PublicationProposal;
import com.example.magrathea.storageengine.cluster.application.ReferencePublicationBarrier;
import com.example.magrathea.storageengine.cluster.application.RepairExecutionGate;
import com.example.magrathea.storageengine.cluster.application.RepairJobQuery;
import com.example.magrathea.storageengine.cluster.application.ReplicaAcknowledgement;
import com.example.magrathea.storageengine.cluster.application.TransferRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/** Deterministic acceptance controls, absent unless the test-only application explicitly enables them. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "magrathea.acceptance.enabled", havingValue = "true")
class Ep10AcceptanceControlConfiguration {
    private static final String BUCKET = "ep10-repair-archive";
    private static final String SHA256 = "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800";
    private static final String ETAG = "1ae14a405348c337d00821e272868e71";
    private static final NodeIdentity A = NodeIdentity.parse("11111111-1111-4111-8111-111111111111");
    private static final NodeIdentity B = NodeIdentity.parse("22222222-2222-4222-8222-222222222222");
    private static final NodeIdentity C = NodeIdentity.parse("33333333-3333-4333-8333-333333333333");
    @Bean
    MutableReplicaFaultPlan acceptanceReplicaFaultPlan() {
        return new MutableReplicaFaultPlan();
    }

    @Bean
    AcceptancePublicationBarrier acceptancePublicationBarrier() {
        return new AcceptancePublicationBarrier();
    }

    @Bean
    AcceptanceRepairGate acceptanceRepairGate() {
        return new AcceptanceRepairGate();
    }

    enum FaultMode { NONE, DEADLINE, CHECKSUM_MISMATCH }

    static final class MutableReplicaFaultPlan implements ReplicaTransferFaultPlan {
        private static final Duration DELAY = Duration.ofSeconds(2);
        private final AtomicReference<FaultMode> mode = new AtomicReference<>(FaultMode.NONE);

        void set(String requested) {
            mode.set(switch (requested) {
                case "none" -> FaultMode.NONE;
                case "deadline" -> FaultMode.DEADLINE;
                case "checksum-mismatch" -> FaultMode.CHECKSUM_MISMATCH;
                default -> throw new IllegalArgumentException("unknown acceptance fault mode " + requested);
            });
        }

        FaultMode mode() {
            return mode.get();
        }

        @Override
        public TransferRequest rewrite(TransferRequest request) {
            if (mode.get() != FaultMode.CHECKSUM_MISMATCH) return request;
            byte[] rejectedChecksum = request.expectedSha256();
            rejectedChecksum[0] ^= 0x01;
            return new TransferRequest(
                    request.operationId(), request.artifactId(), request.targetNode(),
                    request.expectedLength(), rejectedChecksum, request.topologyEpoch(),
                    request.policyEpoch(), request.deadline());
        }

        @Override
        public void beforePayload(TransferRequest request, long offset, int length)
                throws InterruptedException {
            if (mode.get() == FaultMode.DEADLINE) Thread.sleep(DELAY.toMillis());
        }
    }

    static final class AcceptanceRepairGate implements RepairExecutionGate {
        private final AtomicReference<CompletableFuture<Void>> permission =
                new AtomicReference<>(CompletableFuture.completedFuture(null));
        private volatile boolean paused;

        void pause() {
            paused = true;
            permission.set(new CompletableFuture<>());
        }

        void release() {
            paused = false;
            permission.get().complete(null);
        }

        boolean paused() {
            return paused;
        }

        @Override
        public Mono<Void> awaitClaimPermission() {
            return Mono.fromFuture(permission.get());
        }
    }

    static final class AcceptancePublicationBarrier implements ReferencePublicationBarrier {
        private CompletableFuture<PublicationProposal> reached = new CompletableFuture<>();
        private CompletableFuture<Void> released = new CompletableFuture<>();
        private boolean armed;

        synchronized void arm() {
            reached = new CompletableFuture<>();
            released = new CompletableFuture<>();
            armed = true;
        }

        synchronized void release() {
            armed = false;
            released.complete(null);
        }

        synchronized boolean reached() {
            return reached.isDone();
        }

        synchronized int acknowledgementCount() {
            return reached.isDone() ? reached.join().acknowledgements().size() : 0;
        }

        @Override
        public synchronized Mono<Void> await(PublicationProposal proposal) {
            if (!armed) return Mono.empty();
            reached.complete(proposal);
            return Mono.fromFuture(released);
        }
    }

    @Bean
    RouterFunction<ServerResponse> acceptanceControlRoutes(
            ClusterNodeRuntime runtime,
            MutableReplicaFaultPlan faultPlan,
            AcceptancePublicationBarrier barrier,
            AcceptanceRepairGate repairGate) {
        return route()
                .POST("/__acceptance/fault/{mode}", request -> {
                    faultPlan.set(request.pathVariable("mode"));
                    return status(runtime, faultPlan, barrier, repairGate);
                })
                .POST("/__acceptance/voter/stop", request -> {
                    runtime.stopLocalVoter();
                    return status(runtime, faultPlan, barrier, repairGate);
                })
                .POST("/__acceptance/voter/start", request -> {
                    runtime.startLocalVoter();
                    return status(runtime, faultPlan, barrier, repairGate);
                })
                .POST("/__acceptance/publication/arm", request -> {
                    barrier.arm();
                    return status(runtime, faultPlan, barrier, repairGate);
                })
                .POST("/__acceptance/publication/release", request -> {
                    barrier.release();
                    return status(runtime, faultPlan, barrier, repairGate);
                })
                .POST("/__acceptance/repair/pause", request -> {
                    repairGate.pause();
                    return status(runtime, faultPlan, barrier, repairGate);
                })
                .POST("/__acceptance/repair/release", request -> {
                    repairGate.release();
                    return status(runtime, faultPlan, barrier, repairGate);
                })
                .POST("/__acceptance/repair/setup/{generation}/{localState}", request ->
                        setup(runtime, Integer.parseInt(request.pathVariable("generation")),
                                request.pathVariable("localState"))
                                .then(status(runtime, faultPlan, barrier, repairGate)))
                .GET("/__acceptance/repair/jobs", request -> runtime.controlPlane()
                        .repairJobs(RepairJobQuery.all()).map(job -> "generation="
                                + job.specification().referenceGeneration() + " artifact="
                                + job.specification().artifactId() + " target="
                                + job.specification().target() + " state=" + job.state()
                                + " sourceHints=" + job.history().stream()
                                        .map(entry -> String.valueOf(entry.sourceHint())).toList())
                        .collectList().flatMap(jobs -> ServerResponse.ok()
                                .contentType(MediaType.TEXT_PLAIN).bodyValue(String.join("\n", jobs))))
                .GET("/__acceptance/repair/open-count/{artifactId}", request ->
                        ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("opens", runtime.publishedOpenCount(
                                        request.pathVariable("artifactId")))))
                .GET("/__acceptance/status", request -> status(runtime, faultPlan, barrier, repairGate))
                .build();
    }

    private Mono<Void> setup(ClusterNodeRuntime runtime, int generation, String localState) {
        if (generation != 7 && generation != 8) {
            return Mono.error(new IllegalArgumentException("only generations 7 and 8 are accepted"));
        }
        if (!Set.of("valid", "missing", "corrupt").contains(localState)) {
            return Mono.error(new IllegalArgumentException("unknown local repair fixture state"));
        }
        String key = key(generation);
        String artifactId = artifact(generation);
        return runtime.controlPlane().createBucket(BUCKET)
                .onErrorResume(ignored -> runtime.controlPlane().bucket(BUCKET))
                .then(currentGeneration(runtime, key))
                .flatMap(current -> advance(runtime, key, generation, current))
                .then(Mono.fromRunnable(() -> stageLocal(runtime, artifactId, localState)));
    }

    private Mono<Long> currentGeneration(ClusterNodeRuntime runtime, String key) {
        return runtime.controlPlane().objectReference(BUCKET, key)
                .map(reference -> reference.generation())
                .onErrorResume(failure -> failure instanceof ControlPlaneException exception
                                && exception.code() == ControlPlaneException.Code.NOT_FOUND,
                        ignored -> Mono.just(0L));
    }

    private Mono<Void> advance(ClusterNodeRuntime runtime, String key, int target, long current) {
        if (current >= target) return Mono.empty();
        long next = current + 1;
        String artifactId = next == target ? artifact(target) : "acceptance-generation-" + target + "-" + next;
        String operationId = "acceptance-setup-" + target + "-" + next;
        List<ReplicaAcknowledgement> acknowledgements = List.of(
                acknowledgement(operationId, artifactId, B),
                acknowledgement(operationId, artifactId, C));
        PublicationProposal proposal = new PublicationProposal(BUCKET, key, current, operationId,
                artifactId, 134, SHA256, "topology-1", "policy-1", Set.of(A, B, C),
                acknowledgements, new ClusterObjectMetadata("STANDARD", Map.of(), Map.of(),
                        ETAG, Instant.parse("2026-07-13T00:00:00Z")));
        return runtime.controlPlane().compareAndPublish(proposal)
                .then(advance(runtime, key, target, next));
    }

    private ReplicaAcknowledgement acknowledgement(
            String operationId, String artifactId, NodeIdentity node) {
        return new ReplicaAcknowledgement(operationId, artifactId, node, 134, SHA256,
                "topology-1", "policy-1", true);
    }

    private void stageLocal(ClusterNodeRuntime runtime, String artifactId, String localState) {
        if (localState.equals("missing")) return;
        try {
            byte[] bytes = fixture();
            if (localState.equals("corrupt")) bytes[bytes.length - 1] = (byte) 0xf5;
            try (LocalArtifactPort.IncomingSink sink = runtime.artifacts()
                    .beginIncoming("acceptance-" + artifactId, artifactId)) {
                sink.accept(ByteBuffer.wrap(bytes));
                sink.publish();
            }
        } catch (Exception failure) {
            throw new IllegalStateException("cannot stage acceptance repair fixture", failure);
        }
    }

    private byte[] fixture() throws Exception {
        try (var input = Ep10AcceptanceControlConfiguration.class.getClassLoader()
                .getResourceAsStream("fixtures/upload/large-object.bin")) {
            if (input == null) throw new IllegalStateException("repair fixture resource is absent");
            byte[] bytes = input.readAllBytes();
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (bytes.length != 134 || !actual.equals(SHA256)) {
                throw new IllegalStateException("repair fixture facts differ");
            }
            return bytes;
        }
    }

    private static String key(int generation) {
        return generation == 7 ? "evidence/2026/current-generation-repair.bin"
                : "evidence/2026/corrupt-current-generation.bin";
    }

    private static String artifact(int generation) {
        return generation == 7 ? "whole-7f351d76-50d8-4f48-9b86-6f94e777a101"
                : "whole-a26f054a-b8c4-48ba-8107-e2140e964202";
    }

    private Mono<ServerResponse> status(
            ClusterNodeRuntime runtime,
            MutableReplicaFaultPlan faultPlan,
            AcceptancePublicationBarrier barrier,
            AcceptanceRepairGate repairGate) {
        return ServerResponse.ok().bodyValue(Map.of(
                "voterRunning", runtime.localVoterRunning(),
                "dataServerRunning", runtime.dataServerRunning(),
                "fault", faultPlan.mode().name(),
                "publicationReached", barrier.reached(),
                "acknowledgements", barrier.acknowledgementCount(),
                "repairPaused", repairGate.paused(),
                "processSession", runtime.processSession(),
                "repairMetrics", runtime.repairMetrics().snapshot(),
                "repairScheduler", runtime.repairSchedulerStatus()));
    }
}
