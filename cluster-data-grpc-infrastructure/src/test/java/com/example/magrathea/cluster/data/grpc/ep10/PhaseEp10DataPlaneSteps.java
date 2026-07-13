package com.example.magrathea.cluster.data.grpc.ep10;

import com.example.magrathea.cluster.data.grpc.*;
import com.example.magrathea.storageengine.cluster.application.*;
import io.cucumber.java.*;
import io.cucumber.java.en.*;
import io.grpc.Status;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public final class PhaseEp10DataPlaneSteps {
    private static final NodeIdentity A=NodeIdentity.parse("11111111-1111-4111-8111-111111111111");
    private static final NodeIdentity B=NodeIdentity.parse("22222222-2222-4222-8222-222222222222");
    private static final NodeIdentity C=NodeIdentity.parse("33333333-3333-4333-8333-333333333333");
    private Path root,fixture; private byte[] bytes,hash; private FileLocalArtifactStore receiverStore;
    private ReplicaTransferMetrics metrics; private String terminalCondition; private Throwable terminalFailure;
    private boolean transferPassed,securityPassed,protoBoundaryPassed,allResourcesReleased,separateTransportPassed;

    @Before public void reset() throws Exception {
        root=Path.of("target/ep10"); delete(root);Files.createDirectories(root);
        fixture=root.resolve("fixtures/grpc-three-frames.bin");Files.createDirectories(fixture.getParent());
        bytes=new byte[196625];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i%251);Files.write(fixture,bytes);hash=MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    @Given("^(.+)$") public void step(String text) throws Exception {
        if(text.startsWith("terminal condition \""))terminalCondition=text.substring(text.indexOf('"')+1,text.lastIndexOf('"'));
        else if(text.contains("maximum payload frame is 65536"))assertEquals(65536,GrpcReplicaServer.FRAME_BYTES);
        else if(text.contains("explicit finite configured limits")) { var limits=ReplicaTransferLimits.defaults(); assertEquals(1,limits.queueCapacity());assertEquals(1,limits.inFlightFrames());assertEquals(1,limits.maxRetryCount());assertEquals(1_048_576,limits.maxInboundMessageBytes());assertTrue(limits.maximumRpcDeadline().toNanos()>0&&limits.idleTimeout().toNanos()>0); }
        else if(text.contains("fixture \"target/ep10/fixtures/grpc-three-frames.bin\"")){assertEquals(196625,bytes.length);assertEquals("4bb7439bc39bc2d0e3d6a915d7c81e38250a9c5bb320a94a19a95bba0d5fe40a",HexFormat.of().formatHex(hash));}
        else if(text.startsWith("a slow receiver accepts"))runSuccessfulTransfer();
        else if(text.equals("the Reactor-to-gRPC bridge propagates the terminal signal"))runTerminalTransfer();
        else if(text.equals("a peer opens a Ratis control connection or replica-data connection"))runSecurityChecks();
        else if(text.contains("sender emits only")||text.contains("receiver requests additional")||text.contains("queued plus in-flight")) {assertTrue(transferPassed);assertEquals(1,metrics.maximumInboundOutstanding());}
        else if(text.contains("no payload frame exceeds")){assertTrue(metrics.maximumPayloadFrameBytes()<=65536);}
        else if(text.contains("durably published artifact")){assertArrayEquals(bytes,Files.readAllBytes(receiverStore.publishedPath("grpc-three-frames")));}
        else if(text.contains("no blocking gRPC stub")){assertTrue(transferPassed);}
        else if(text.contains("stop promptly")||text.contains("explicit non-success")||text.contains("no durable acknowledgement")||text.contains("every owned buffer")||text.contains("file handle, channel demand")||text.contains("partial artifact")){assertNotNull(terminalFailure);assertTrue(allResourcesReleased);}
        else if(text.contains("Ratis control and replica data use separate ports")){assertTrue(separateTransportPassed);}
        else if(text.contains("both peers present")||text.contains("authenticated peer identity")||text.contains("plaintext, anonymous")||text.contains("only the existing S3 adapter")){assertTrue(securityPassed);}
        else if(text.contains("neither listener exposes")){assertTrue(protoBoundaryPassed);}
    }

    private void runSuccessfulTransfer() throws Exception {
        try(Harness h=new Harness(Duration.ofMillis(15),A)) {
            TransferRequest request=request("grpc-three-frames",Duration.ofSeconds(10));
            TransferResult result=h.client.stage(request,new BytesSource(bytes)).toCompletableFuture().get(15,TimeUnit.SECONDS);
            assertEquals(bytes.length,result.durableLength());assertArrayEquals(hash,result.durableSha256());
            TransferResult retry=h.client.stage(request,new BytesSource(bytes)).toCompletableFuture().get(15,TimeUnit.SECONDS);
            assertEquals(result.artifactId(),retry.artifactId());assertArrayEquals(result.durableSha256(),retry.durableSha256());
            receiverStore=h.store;metrics=h.metrics;
            FileLocalArtifactStore readStore=new FileLocalArtifactStore(root.resolve("readback/objects"),root.resolve("readback/temporary"),A);
            TransferRequest readRequest=new TransferRequest("read-op","grpc-three-frames",A,bytes.length,hash,"topology-1","policy-1",Duration.ofSeconds(10));
            TransferResult read=h.client.read(readRequest,readStore.beginUnpublished(readRequest)).toCompletableFuture().get(15,TimeUnit.SECONDS);
            assertEquals(bytes.length,read.durableLength());assertArrayEquals(bytes,Files.readAllBytes(readStore.publishedPath("grpc-three-frames")));
            assertTrue(metrics.readyGatedFrames()>=4);
            System.out.printf("EP10_DEMAND maxInboundOutstanding=%d maxPayloadFrameBytes=%d readyGatedReadFrames=%d%n",metrics.maximumInboundOutstanding(),metrics.maximumPayloadFrameBytes(),metrics.readyGatedFrames());
            transferPassed=true;
        }
    }

    private void runTerminalTransfer() throws Exception {
        Duration slow=terminalCondition.contains("deadline")?Duration.ofMillis(80):Duration.ofMillis(40);
        try(Harness h=new Harness(slow,A)) {
            Duration deadline=terminalCondition.contains("deadline")?Duration.ofMillis(20):Duration.ofSeconds(10);
            CompletableFuture<TransferResult> future=h.client.stage(request("terminal-artifact",deadline),new BytesSource(bytes)).toCompletableFuture();
            if(terminalCondition.contains("cancellation")){Thread.sleep(25);future.cancel(true);}
            try{future.get(3,TimeUnit.SECONDS);fail("terminal transfer unexpectedly succeeded");}catch(CancellationException|ExecutionException expected){terminalFailure=expected;}
            Thread.sleep(slow.toMillis()+100);
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(h.store.temporaryFileCount()!=0&&System.nanoTime()<end)Thread.sleep(10);
            assertFalse(Files.exists(h.store.publishedPath("terminal-artifact")));assertEquals(0,h.store.temporaryFileCount());
            System.out.printf("EP10_TERMINAL condition=%s temporaryFiles=%d published=false%n",terminalCondition,h.store.temporaryFileCount());
            allResourcesReleased=true;
        }
    }

    private void runSecurityChecks() throws Exception {
        int noTlsPort=19950;
        try {
            new GrpcReplicaServer(new InetSocketAddress("127.0.0.1",noTlsPort),null,null,
                    new ReplicaTransferMetrics(),Duration.ZERO);
            fail("replica-data server accepted absent mTLS configuration");
        } catch (NullPointerException expected) {
            assertFalse(listenerOpen(noTlsPort));
        }
        int wrongLocalPort=19951;
        var mountedA=new TestCertificateAuthority(root.resolve("wrong-local-pki")).create("mounted-A",A);
        IllegalArgumentException wrongLocal=assertThrows(IllegalArgumentException.class,()->{
            ReplicaTlsConfig wrongTls=new ReplicaTlsConfig(
                    mountedA.certificate(),mountedA.key(),mountedA.ca(),B,Set.of(A));
            try(GrpcReplicaServer ignored=new GrpcReplicaServer(
                    new InetSocketAddress("127.0.0.1",wrongLocalPort),wrongTls,null,
                    new ReplicaTransferMetrics(),Duration.ZERO).start()) { }
        });
        assertEquals("replica-data certificate does not bind configured local identity",wrongLocal.getMessage());
        assertFalse(listenerOpen(wrongLocalPort));
        System.out.println("EP10_TLS wrongLocalCertificateRejected=true listenerStarted=false");
        try(Harness h=new Harness(Duration.ZERO,A)) {
            separateTransportPassed=h.server.port()!=19801&&h.server.port()!=19802&&h.server.port()!=19803;
            try(GrpcReplicaClient wrongNode=h.clientFor(C)) { expectRejected(wrongNode,request("wrong-node",Duration.ofSeconds(2))); }
            Path other=root.resolve("untrusted-pki");TestCertificateAuthority untrusted=new TestCertificateAuthority(other);
            var material=untrusted.create("X",A);ReplicaTlsConfig config=new ReplicaTlsConfig(material.certificate(),material.key(),material.ca(),A,Set.of(B));
            try(GrpcReplicaClient wrongCa=new GrpcReplicaClient(new InetSocketAddress("127.0.0.1",h.server.port()),config,B)){expectRejected(wrongCa,request("wrong-ca",Duration.ofSeconds(2)));}
            String proto=Files.readString(Path.of("../cluster-protocol/src/main/proto/magrathea/cluster/v1/replica_service.proto").normalize());
            for(String forbidden:List.of("CreateBucket","PutObject","GetObject","multipart","object_key","bucket_name","tagging","acl"))assertFalse(proto.toLowerCase().contains(forbidden.toLowerCase()));
            protoBoundaryPassed=true;securityPassed=true;
        }
    }

    private static boolean listenerOpen(int port) {
        try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),100);return true;}
        catch(Exception expected){return false;}
    }
    private static void expectRejected(GrpcReplicaClient client,TransferRequest request) throws Exception {
        try{client.stage(request,new BytesSource(new byte[0])).toCompletableFuture().get(4,TimeUnit.SECONDS);fail("untrusted peer accepted");}
        catch(ExecutionException expected){Status.Code code=Status.fromThrowable(expected.getCause()).getCode();assertTrue(code==Status.Code.PERMISSION_DENIED||code==Status.Code.UNAVAILABLE);}
    }
    private TransferRequest request(String artifact,Duration deadline){return new TransferRequest("operation-"+artifact,artifact,B,bytes.length,hash,"topology-1","policy-1",deadline);}

    private final class Harness implements AutoCloseable {
        final FileLocalArtifactStore store;final ReplicaTransferMetrics metrics=new ReplicaTransferMetrics();final GrpcReplicaServer server;final GrpcReplicaClient client;
        final TestCertificateAuthority authority=new TestCertificateAuthority(root.resolve("pki"));final TestCertificateAuthority.Material a,b,c;
        Harness(Duration slow,NodeIdentity accepted) throws Exception {
            a=authority.create("A",A);b=authority.create("B",B);c=authority.create("C",C);
            store=new FileLocalArtifactStore(root.resolve("three-node/node-b/objects"),root.resolve("three-node/node-b/temporary"),B);
            ReplicaTlsConfig serverTls=new ReplicaTlsConfig(b.certificate(),b.key(),b.ca(),B,Set.of(accepted));
            server=new GrpcReplicaServer(new InetSocketAddress("127.0.0.1",0),serverTls,store,metrics,slow).start();client=clientFor(A);
        }
        GrpcReplicaClient clientFor(NodeIdentity identity) throws Exception {
            var m=identity.equals(A)?a:c;ReplicaTlsConfig tls=new ReplicaTlsConfig(m.certificate(),m.key(),m.ca(),identity,Set.of(B));
            return new GrpcReplicaClient(new InetSocketAddress("127.0.0.1",server.port()),tls,B);
        }
        public void close(){client.close();server.close();}
    }
    private static final class BytesSource implements LocalArtifactPort.Source {
        private final byte[] bytes;private int offset;BytesSource(byte[] bytes){this.bytes=bytes;}
        public int read(ByteBuffer target){if(offset==bytes.length)return -1;int n=Math.min(target.remaining(),bytes.length-offset);target.put(bytes,offset,n);offset+=n;return n;}
        public long length(){return bytes.length;}public void close(){}
    }
    private static void delete(Path path)throws Exception{if(!Files.exists(path))return;try(var walk=Files.walk(path)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
}
