package com.example.magrathea.cluster.data.grpc;

import com.example.magrathea.cluster.protocol.v1.*;
import com.example.magrathea.storageengine.cluster.application.*;
import com.google.protobuf.ByteString;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.*;
import io.grpc.stub.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Async grpc-java adapter using readiness-gated output and cancellable deadlines; no blocking stub. */
public final class GrpcReplicaClient implements ReplicaTransferPort, AutoCloseable {
    private final ManagedChannel channel; private final ExecutorService producer; private final ReplicaTransferLimits limits;
    public GrpcReplicaClient(InetSocketAddress address, ReplicaTlsConfig tls, NodeIdentity expectedServer) throws IOException {
        this(address,tls,expectedServer,ReplicaTransferLimits.defaults());
    }
    public GrpcReplicaClient(InetSocketAddress address, ReplicaTlsConfig tls, NodeIdentity expectedServer, ReplicaTransferLimits limits) throws IOException {
        this.limits=limits;
        var ssl=GrpcSslContexts.forClient().keyManager(tls.certificateChain().toFile(),tls.privateKey().toFile())
                .trustManager(tls.trustCertificate().toFile()).build();
        channel=NettyChannelBuilder.forAddress(address).sslContext(ssl).overrideAuthority(expectedServer.toString())
                .maxInboundMessageSize(limits.maxInboundMessageBytes()).build();
        producer=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("replica-data-producer-",0).factory());
    }

    @Override public CompletionStage<TransferResult> stage(TransferRequest request, LocalArtifactPort.Source source) {
        if(request.deadline().compareTo(limits.maximumRpcDeadline())>0) throw new IllegalArgumentException("RPC deadline exceeds configured finite maximum");
        CompletableFuture<TransferResult> result=new CompletableFuture<>();
        var stub=ReplicaTransferServiceGrpc.newStub(channel).withDeadlineAfter(request.deadline().toNanos(),TimeUnit.NANOSECONDS);
        AtomicBoolean started=new AtomicBoolean();
        ClientResponseObserver<StageReplicaRequest,StageReplicaResponse> response=new ClientResponseObserver<>() {
            ClientCallStreamObserver<StageReplicaRequest> call;
            public void beforeStart(ClientCallStreamObserver<StageReplicaRequest> stream) {
                call=stream; stream.setOnReadyHandler(()->{if(started.compareAndSet(false,true))producer.execute(()->send(stream));});
                result.whenComplete((v,e)->{if(result.isCancelled())stream.cancel("application cancellation",null);});
            }
            private void send(ClientCallStreamObserver<StageReplicaRequest> stream) {
                try {
                    awaitReady(stream,result);
                    var metadata=ReplicaStageMetadata.newBuilder().setOperationId(request.operationId()).setArtifactId(request.artifactId())
                            .setTargetNodeId(request.targetNode().toString()).setExpectedLength(request.expectedLength())
                            .setExpectedSha256(ByteString.copyFrom(request.expectedSha256())).setTopologyEpoch(request.topologyEpoch()).setPolicyEpoch(request.policyEpoch()).build();
                    stream.onNext(StageReplicaRequest.newBuilder().setMetadata(metadata).build());
                    long offset=0;
                    while(offset<request.expectedLength()) {
                        if(Context.current().isCancelled()||result.isCancelled())throw Status.CANCELLED.asRuntimeException();
                        ByteBuffer frame=ByteBuffer.allocate(limits.maxFrameBytes()); int n=source.read(frame);
                        if(n<0)break; frame.flip();byte[] bytes=new byte[n];frame.get(bytes);awaitReady(stream,result);
                        stream.onNext(StageReplicaRequest.newBuilder().setPayload(ReplicaPayloadFrame.newBuilder().setOffset(offset).setPayload(ByteString.copyFrom(bytes))).build());offset+=n;
                    }
                    source.close(); stream.onCompleted();
                } catch(Throwable failure){try{source.close();}catch(IOException ignored){}stream.cancel("source or transfer failed",failure);result.completeExceptionally(failure);}
            }
            public void onNext(StageReplicaResponse r) { result.complete(new TransferResult(r.getOperationId(),r.getArtifactId(),NodeIdentity.parse(r.getNodeId()),r.getDurableLength(),r.getDurableSha256().toByteArray(),r.getTopologyEpoch(),r.getPolicyEpoch(),false)); }
            public void onError(Throwable failure) { result.completeExceptionally(failure); }
            public void onCompleted() { if(!result.isDone())result.completeExceptionally(new TransferException(TransferError.PROTOCOL_ERROR,"missing durable response")); }
        };
        stub.stage(response);
        return result;
    }

    /** Receives a server stream with one-message demand issued only after local sink acceptance. */
    public CompletionStage<TransferResult> read(TransferRequest request, LocalArtifactPort.RepairSink sink) {
        if(request.deadline().compareTo(limits.maximumRpcDeadline())>0) throw new IllegalArgumentException("RPC deadline exceeds configured finite maximum");
        CompletableFuture<TransferResult> result=new CompletableFuture<>();
        var stub=ReplicaTransferServiceGrpc.newStub(channel).withDeadlineAfter(request.deadline().toNanos(),TimeUnit.NANOSECONDS);
        ClientResponseObserver<ReadReplicaRequest,ReplicaPayloadFrame> observer=new ClientResponseObserver<>() {
            ClientCallStreamObserver<ReadReplicaRequest> call;
            public void beforeStart(ClientCallStreamObserver<ReadReplicaRequest> stream){call=stream;stream.disableAutoRequestWithInitial(1);result.whenComplete((v,e)->{if(result.isCancelled())stream.cancel("application cancellation",null);});}
            public void onNext(ReplicaPayloadFrame frame){try{if(frame.getPayload().size()>limits.maxFrameBytes())throw new IOException("oversize frame");sink.accept(frame.getOffset(),ByteBuffer.wrap(frame.getPayload().toByteArray()));call.request(1);}catch(Throwable e){sink.abort();call.cancel("disk acceptance failed",e);result.completeExceptionally(e);}}
            public void onError(Throwable failure){sink.abort();result.completeExceptionally(failure);}
            public void onCompleted(){try{result.complete(sink.verify());}catch(Throwable failure){sink.abort();result.completeExceptionally(failure);}}
        };
        stub.read(ReadReplicaRequest.newBuilder().setArtifactId(request.artifactId()).setExpectedLength(request.expectedLength()).setExpectedSha256(ByteString.copyFrom(request.expectedSha256())).build(),observer);
        return result;
    }

    private static void awaitReady(ClientCallStreamObserver<?> stream, CompletableFuture<?> result) throws InterruptedException {
        while(!stream.isReady()){if(result.isDone())throw Status.CANCELLED.asRuntimeException();Thread.sleep(1);}
    }
    @Override public void close(){channel.shutdownNow();try{channel.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}producer.shutdownNow();}
}
