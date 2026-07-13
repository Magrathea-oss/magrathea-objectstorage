package com.example.magrathea.cluster.data.grpc;

import com.example.magrathea.cluster.protocol.v1.*;
import com.example.magrathea.storageengine.cluster.application.*;
import com.google.protobuf.ByteString;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.*;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.stub.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Internal Netty-shaded gRPC data server with mTLS and manual stream demand. */
public final class GrpcReplicaServer implements AutoCloseable {
    public static final int FRAME_BYTES = 65_536;
    public static final int MAX_INBOUND_MESSAGE_BYTES = 1_048_576;
    private final Server server;
    private final ExecutorService ioExecutor;
    private final ScheduledExecutorService timer;

    public GrpcReplicaServer(InetSocketAddress address, ReplicaTlsConfig tls, LocalArtifactPort artifacts,
                             ReplicaTransferMetrics metrics, Duration slowAcceptance) throws IOException {
        this(address, tls, artifacts, metrics, slowAcceptance,
                ReplicaTransferLimits.defaults(), ReplicaTransferFaultPlan.none());
    }
    public GrpcReplicaServer(InetSocketAddress address, ReplicaTlsConfig tls, LocalArtifactPort artifacts,
                             ReplicaTransferMetrics metrics, Duration slowAcceptance,
                             ReplicaTransferFaultPlan faultPlan) throws IOException {
        this(address, tls, artifacts, metrics, slowAcceptance,
                ReplicaTransferLimits.defaults(), faultPlan);
    }
    public GrpcReplicaServer(InetSocketAddress address, ReplicaTlsConfig tls, LocalArtifactPort artifacts,
                             ReplicaTransferMetrics metrics, Duration slowAcceptance,
                             ReplicaTransferLimits limits) throws IOException {
        this(address, tls, artifacts, metrics, slowAcceptance, limits, ReplicaTransferFaultPlan.none());
    }
    public GrpcReplicaServer(InetSocketAddress address, ReplicaTlsConfig tls, LocalArtifactPort artifacts,
                             ReplicaTransferMetrics metrics, Duration slowAcceptance,
                             ReplicaTransferLimits limits, ReplicaTransferFaultPlan faultPlan) throws IOException {
        Objects.requireNonNull(tls, "cluster mode requires replica-data mTLS certificate, key, and trust paths");
        ioExecutor = Executors.newFixedThreadPool(2, Thread.ofPlatform().name("replica-data-io-", 0).factory());
        timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("replica-data-timer-", 0).factory());
        var ssl = GrpcSslContexts.forServer(tls.certificateChain().toFile(), tls.privateKey().toFile())
                .trustManager(tls.trustCertificate().toFile()).clientAuth(ClientAuth.REQUIRE).build();
        var service = ServerInterceptors.intercept(
                new Service(tls.localIdentity(), artifacts, metrics, ioExecutor, timer,
                        slowAcceptance, limits, Objects.requireNonNull(faultPlan, "faultPlan")),
                new PeerIdentityInterceptor(tls.acceptedPeers()));
        server = NettyServerBuilder.forAddress(address).sslContext(ssl).executor(ioExecutor)
                .maxInboundMessageSize(limits.maxInboundMessageBytes()).addService(service).build();
    }
    public GrpcReplicaServer start() throws IOException { server.start(); return this; }
    public int port() { return server.getPort(); }
    @Override public void close() { server.shutdownNow(); try { server.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } ioExecutor.shutdownNow(); timer.shutdownNow(); }

    private static final class Service extends ReplicaTransferServiceGrpc.ReplicaTransferServiceImplBase {
        private final NodeIdentity local; private final LocalArtifactPort artifacts; private final ReplicaTransferMetrics metrics;
        private final Executor executor; private final ScheduledExecutorService timer; private final Duration slowAcceptance; private final ReplicaTransferLimits limits;
        private final ReplicaTransferFaultPlan faultPlan;
        Service(NodeIdentity local, LocalArtifactPort artifacts, ReplicaTransferMetrics metrics, Executor executor,
                ScheduledExecutorService timer, Duration slowAcceptance, ReplicaTransferLimits limits,
                ReplicaTransferFaultPlan faultPlan) {
            this.local=local; this.artifacts=artifacts; this.metrics=metrics; this.executor=executor; this.timer=timer; this.slowAcceptance=slowAcceptance; this.limits=limits; this.faultPlan=faultPlan;
        }
        @Override public StreamObserver<StageReplicaRequest> stage(StreamObserver<StageReplicaResponse> responseObserver) {
            ServerCallStreamObserver<StageReplicaResponse> response=(ServerCallStreamObserver<StageReplicaResponse>)responseObserver;
            response.disableAutoRequest();
            AtomicBoolean terminal=new AtomicBoolean();
            class Receiver implements StreamObserver<StageReplicaRequest> {
                LocalArtifactPort.Sink sink; TransferRequest request; boolean metadata; long frames; ScheduledFuture<?> idle;
                void demand() { if (!terminal.get()) { metrics.requested(); idle=timer.schedule(()->fail(Status.DEADLINE_EXCEEDED.withDescription("replica stream idle timeout")),limits.idleTimeout().toNanos(),TimeUnit.NANOSECONDS); response.request(1); } }
                public void onNext(StageReplicaRequest part) {
                    if(idle!=null)idle.cancel(false); metrics.accepted();
                    try {
                        if (!metadata) {
                            if (!part.hasMetadata()) throw new TransferException(TransferError.PROTOCOL_ERROR, "metadata must be first");
                            var m=part.getMetadata();
                            if (!m.getTargetNodeId().equals(local.toString())) throw new TransferException(TransferError.IDENTITY_MISMATCH, "target identity mismatch");
                            request=faultPlan.rewrite(new TransferRequest(m.getOperationId(),m.getArtifactId(),local,m.getExpectedLength(),m.getExpectedSha256().toByteArray(),m.getTopologyEpoch(),m.getPolicyEpoch(),Duration.ofDays(1)));
                            sink=artifacts.beginUnpublished(request); metadata=true;
                        } else {
                            if (!part.hasPayload()) throw new TransferException(TransferError.PROTOCOL_ERROR, "payload expected");
                            var p=part.getPayload(); int size=p.getPayload().size();
                            if (size > limits.maxFrameBytes()) throw new TransferException(TransferError.FRAME_TOO_LARGE, "payload frame exceeds 64KiB");
                            if (!slowAcceptance.isZero()) Thread.sleep(slowAcceptance.toMillis());
                            faultPlan.beforePayload(request, p.getOffset(), size);
                            sink.accept(p.getOffset(), ByteBuffer.wrap(p.getPayload().toByteArray())); frames++; metrics.frame(size);
                        }
                        demand();
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); fail(Status.CANCELLED.withCause(e)); }
                    catch (Throwable failure) { fail(status(failure)); }
                }
                public void onError(Throwable failure) { if(idle!=null)idle.cancel(false); metrics.closed(); abort(); }
                public void onCompleted() {
                    if(idle!=null)idle.cancel(false); metrics.closed();
                    try {
                        if (sink == null) throw new TransferException(TransferError.PROTOCOL_ERROR, "metadata missing");
                        TransferResult r=sink.publish(); terminal.set(true);
                        response.onNext(StageReplicaResponse.newBuilder().setOperationId(r.operationId()).setArtifactId(r.artifactId())
                                .setNodeId(r.node().toString()).setDurableLength(r.durableLength()).setDurableSha256(ByteString.copyFrom(r.durableSha256()))
                                .setTopologyEpoch(r.topologyEpoch()).setPolicyEpoch(r.policyEpoch()).build()); response.onCompleted();
                    } catch (Throwable failure) { fail(status(failure)); }
                }
                void fail(Status status) { if (terminal.compareAndSet(false,true)) { if(idle!=null)idle.cancel(false); metrics.closed(); if(sink!=null)sink.abort(); response.onError(status.asRuntimeException()); } }
                void abort() { if(idle!=null)idle.cancel(false); if (terminal.compareAndSet(false,true) && sink!=null) sink.abort(); }
            }
            Receiver receiver=new Receiver();
            response.setOnCancelHandler(receiver::abort);
            receiver.demand();
            return receiver;
        }

        @Override public void read(ReadReplicaRequest request, StreamObserver<ReplicaPayloadFrame> observer) {
            ServerCallStreamObserver<ReplicaPayloadFrame> out=(ServerCallStreamObserver<ReplicaPayloadFrame>)observer;
            AtomicBoolean draining=new AtomicBoolean(); AtomicBoolean done=new AtomicBoolean();
            try {
                LocalArtifactPort.Source source=artifacts.openPublished(request.getArtifactId());
                class Drain implements Runnable { long offset;
                    public void run() {
                        if (!draining.compareAndSet(false,true)) return;
                        try {
                            while (!done.get() && out.isReady()) {
                                ByteBuffer bytes=ByteBuffer.allocate(FRAME_BYTES); int n=source.read(bytes);
                                if(n<0){done.set(true);source.close();out.onCompleted();break;}
                                bytes.flip(); byte[] payload=new byte[n];bytes.get(payload); metrics.readyFrame();
                                out.onNext(ReplicaPayloadFrame.newBuilder().setOffset(offset).setPayload(ByteString.copyFrom(payload)).build());offset+=n;
                            }
                        } catch(Throwable failure){done.set(true);try{source.close();}catch(IOException ignored){}out.onError(status(failure).asRuntimeException());}
                        finally { draining.set(false); if(!done.get()&&out.isReady()) executor.execute(this); }
                    }
                }
                Drain drain=new Drain(); out.setOnReadyHandler(()->executor.execute(drain)); out.setOnCancelHandler(()->{done.set(true);try{source.close();}catch(IOException ignored){}}); executor.execute(drain);
            } catch(IOException failure){observer.onError(Status.NOT_FOUND.withCause(failure).asRuntimeException());}
        }
        private static Status status(Throwable failure) {
            if(failure instanceof TransferException t) return (switch(t.error()) {
                case CANCELLED -> Status.CANCELLED; case DEADLINE_EXCEEDED -> Status.DEADLINE_EXCEEDED;
                case IDENTITY_MISMATCH, UNTRUSTED_PEER -> Status.PERMISSION_DENIED;
                case ARTIFACT_CONFLICT -> Status.ALREADY_EXISTS; default -> Status.INVALID_ARGUMENT;
            }).withDescription(failure.getMessage());
            return Status.INTERNAL.withDescription(failure.getMessage()).withCause(failure);
        }
    }
}
