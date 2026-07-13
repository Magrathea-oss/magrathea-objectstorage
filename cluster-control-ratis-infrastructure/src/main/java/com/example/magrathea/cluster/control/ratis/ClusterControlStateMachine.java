package com.example.magrathea.cluster.control.ratis;

import com.example.magrathea.storageengine.cluster.application.*;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.server.storage.FileInfo;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.SnapshotInfo;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
import org.apache.ratis.statemachine.impl.SingleFileSnapshotInfo;
import org.apache.ratis.util.MD5FileUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Deterministic replicated state machine for fixed membership, buckets and immutable references. */
final class ClusterControlStateMachine extends BaseStateMachine {
    private static final int SNAPSHOT_VERSION = 1;
    private final MembershipSnapshot membership;
    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();
    private final NavigableMap<String, BucketNamespace> buckets = new TreeMap<>();
    private final NavigableMap<String, ObjectReferenceGeneration> references = new TreeMap<>();

    ClusterControlStateMachine(MembershipSnapshot membership) { this.membership = membership; }

    @Override
    public synchronized void initialize(RaftServer server, org.apache.ratis.protocol.RaftGroupId groupId, RaftStorage raftStorage) throws IOException {
        super.initialize(server, groupId, raftStorage);
        storage.init(raftStorage);
        loadSnapshot(storage.loadLatestSnapshot());
    }

    @Override public org.apache.ratis.statemachine.StateMachineStorage getStateMachineStorage() { return storage; }
    @Override public SnapshotInfo getLatestSnapshot() { return storage.getLatestSnapshot(); }

    @Override
    public synchronized CompletableFuture<Message> applyTransaction(TransactionContext transaction) {
        var entry = transaction.getLogEntry();
        byte[] bytes = entry.getStateMachineLogEntry().getLogData().toByteArray();
        String result = apply(ControlPlaneCodec.readCommand(bytes));
        updateLastAppliedTermIndex(entry.getTerm(), entry.getIndex());
        return CompletableFuture.completedFuture(Message.valueOf(result));
    }

    @Override
    public synchronized CompletableFuture<Message> query(Message request) {
        return CompletableFuture.completedFuture(Message.valueOf(query(ControlPlaneCodec.readCommand(request.getContent().toByteArray()))));
    }

    private String apply(ControlPlaneCodec.Command command) {
        if (command instanceof ControlPlaneCodec.CreateBucket create) {
            BucketNamespace bucket = buckets.computeIfAbsent(create.bucket(), name -> new BucketNamespace(name, 1));
            return "OK\tB\t" + ControlPlaneCodec.encode(bucket);
        }
        if (command instanceof ControlPlaneCodec.Publish publish) {
            String key = publish.bucket() + "\u0000" + publish.key();
            ObjectReferenceGeneration current = references.get(key);
            long actual = current == null ? 0 : current.generation();
            if (publish.priorGeneration() != actual) return error(ControlPlaneException.Code.STALE_GENERATION, "expected " + actual);
            if (!membership.topologyEpoch().equals(publish.topologyEpoch())) return error(ControlPlaneException.Code.STALE_TOPOLOGY_EPOCH, "topology epoch changed");
            if (!membership.policyEpoch().equals(publish.policyEpoch())) return error(ControlPlaneException.Code.STALE_POLICY_EPOCH, "policy epoch changed");
            if (publish.replicas().size() < 2 || !membership.voterIdentities().containsAll(publish.replicas())) return error(ControlPlaneException.Code.INVALID_ACKNOWLEDGEMENT, "replicas are not unique fixed voters");
            ObjectReferenceGeneration next = new ObjectReferenceGeneration(publish.bucket(), publish.key(), actual + 1,
                    publish.operationId(), publish.artifactId(), publish.length(), publish.sha256(), publish.topologyEpoch(),
                    publish.policyEpoch(), publish.replicas(), publish.metadata());
            references.put(key, next);
            return "OK\tR\t" + ControlPlaneCodec.encode(next);
        }
        return error(ControlPlaneException.Code.INTERNAL_FAILURE, "query submitted as write");
    }

    private String query(ControlPlaneCodec.Command command) {
        if (command instanceof ControlPlaneCodec.QueryMembership) {
            return "OK\tM\t" + membership.topologyEpoch() + "\t" + membership.policyEpoch() + "\t"
                    + membership.voters().stream().map(v -> String.join(",",
                            v.name(), v.identity().toString(), v.host(), Integer.toString(v.controlPort()),
                            v.dataHost(), Integer.toString(v.dataPort()), v.failureDomain()))
                    .reduce((a,b) -> a + ";" + b).orElse("");
        }
        if (command instanceof ControlPlaneCodec.QueryBucket query) {
            BucketNamespace value = buckets.get(query.bucket());
            return value == null ? error(ControlPlaneException.Code.NOT_FOUND, "bucket not found") : "OK\tB\t" + ControlPlaneCodec.encode(value);
        }
        if (command instanceof ControlPlaneCodec.QueryReference query) {
            ObjectReferenceGeneration value = references.get(query.bucket() + "\u0000" + query.key());
            return value == null ? error(ControlPlaneException.Code.NOT_FOUND, "reference not found") : "OK\tR\t" + ControlPlaneCodec.encode(value);
        }
        if (command instanceof ControlPlaneCodec.QueryBuckets) {
            return "OK\tL\t" + ControlPlaneCodec.encodeBuckets(List.copyOf(buckets.values()));
        }
        return error(ControlPlaneException.Code.INTERNAL_FAILURE, "write submitted as query");
    }

    @Override
    public synchronized long takeSnapshot() throws IOException {
        TermIndex applied = getLastAppliedTermIndex();
        if (applied == null || applied.getIndex() < 0) return -1;
        File target = storage.getSnapshotFile(applied.getTerm(), applied.getIndex());
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
            out.writeInt(SNAPSHOT_VERSION); out.writeUTF(membership.topologyEpoch()); out.writeUTF(membership.policyEpoch());
            out.writeInt(buckets.size()); for (BucketNamespace bucket : buckets.values()) { out.writeUTF(bucket.bucket()); out.writeLong(bucket.generation()); }
            out.writeInt(references.size()); for (ObjectReferenceGeneration reference : references.values()) out.writeUTF(ControlPlaneCodec.encode(reference));
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        var digest = MD5FileUtil.computeAndSaveMd5ForFile(target);
        storage.updateLatestSnapshot(new SingleFileSnapshotInfo(new FileInfo(target.toPath(), digest), applied));
        return applied.getIndex();
    }

    private synchronized void loadSnapshot(SingleFileSnapshotInfo snapshot) throws IOException {
        if (snapshot == null) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(snapshot.getFile().getPath().toFile())))) {
            if (in.readInt() != SNAPSHOT_VERSION) throw new IOException("unsupported control snapshot version");
            if (!membership.topologyEpoch().equals(in.readUTF()) || !membership.policyEpoch().equals(in.readUTF())) throw new IOException("bootstrap epochs cannot rewrite persisted state");
            buckets.clear(); int bucketCount = in.readInt(); for (int i=0;i<bucketCount;i++) { BucketNamespace b = new BucketNamespace(in.readUTF(), in.readLong()); buckets.put(b.bucket(), b); }
            references.clear(); int referenceCount = in.readInt(); for (int i=0;i<referenceCount;i++) { ObjectReferenceGeneration r = ControlPlaneCodec.decodeReference(in.readUTF()); references.put(r.namespaceKey(), r); }
        }
        setLastAppliedTermIndex(snapshot.getTermIndex());
    }

    private static String error(ControlPlaneException.Code code, String message) { return "ERR\t" + code + "\t" + message; }
}
