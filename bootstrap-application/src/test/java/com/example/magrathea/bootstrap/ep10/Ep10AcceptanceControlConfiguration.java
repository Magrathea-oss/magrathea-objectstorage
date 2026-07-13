package com.example.magrathea.bootstrap.ep10;

import com.example.magrathea.bootstrap.ClusterNodeRuntime;
import com.example.magrathea.cluster.data.grpc.ReplicaTransferFaultPlan;
import com.example.magrathea.storageengine.cluster.application.PublicationProposal;
import com.example.magrathea.storageengine.cluster.application.ReferencePublicationBarrier;
import com.example.magrathea.storageengine.cluster.application.TransferRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/** Deterministic acceptance controls, absent unless the test-only application explicitly enables them. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "magrathea.acceptance.enabled", havingValue = "true")
class Ep10AcceptanceControlConfiguration {
    @Bean
    MutableReplicaFaultPlan acceptanceReplicaFaultPlan() {
        return new MutableReplicaFaultPlan();
    }

    @Bean
    AcceptancePublicationBarrier acceptancePublicationBarrier() {
        return new AcceptancePublicationBarrier();
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
            AcceptancePublicationBarrier barrier) {
        return route()
                .POST("/__acceptance/fault/{mode}", request -> {
                    faultPlan.set(request.pathVariable("mode"));
                    return status(runtime, faultPlan, barrier);
                })
                .POST("/__acceptance/voter/stop", request -> {
                    runtime.stopLocalVoter();
                    return status(runtime, faultPlan, barrier);
                })
                .POST("/__acceptance/voter/start", request -> {
                    runtime.startLocalVoter();
                    return status(runtime, faultPlan, barrier);
                })
                .POST("/__acceptance/publication/arm", request -> {
                    barrier.arm();
                    return status(runtime, faultPlan, barrier);
                })
                .POST("/__acceptance/publication/release", request -> {
                    barrier.release();
                    return status(runtime, faultPlan, barrier);
                })
                .GET("/__acceptance/status", request -> status(runtime, faultPlan, barrier))
                .build();
    }

    private Mono<ServerResponse> status(
            ClusterNodeRuntime runtime,
            MutableReplicaFaultPlan faultPlan,
            AcceptancePublicationBarrier barrier) {
        return ServerResponse.ok().bodyValue(Map.of(
                "voterRunning", runtime.localVoterRunning(),
                "dataServerRunning", runtime.dataServerRunning(),
                "fault", faultPlan.mode().name(),
                "publicationReached", barrier.reached(),
                "acknowledgements", barrier.acknowledgementCount()));
    }
}
