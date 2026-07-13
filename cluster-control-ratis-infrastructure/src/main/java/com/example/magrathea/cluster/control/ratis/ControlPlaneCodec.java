package com.example.magrathea.cluster.control.ratis;

import com.example.magrathea.storageengine.cluster.application.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class ControlPlaneCodec {
    private ControlPlaneCodec() {}

    static byte[] createBucket(String bucket) { return write(out -> { out.writeByte(1); out.writeUTF(bucket); }); }
    static byte[] publish(PublicationProposal proposal) {
        PublicationProposal p = ReferencePublicationService.validate(proposal);
        return write(out -> {
        out.writeByte(2); out.writeUTF(p.bucket()); out.writeUTF(p.objectKey()); out.writeLong(p.priorGeneration());
        out.writeUTF(p.operationId()); out.writeUTF(p.artifactId()); out.writeLong(p.length()); out.writeUTF(p.sha256());
        out.writeUTF(p.topologyEpoch()); out.writeUTF(p.policyEpoch());
        List<NodeIdentity> replicas = p.acknowledgements().stream().map(ReplicaAcknowledgement::node).distinct().sorted().toList();
        out.writeInt(replicas.size()); for (NodeIdentity replica : replicas) out.writeUTF(replica.toString());
        writeMetadata(out, p.metadata());
    });
    }
    static byte[] queryMembership() { return new byte[]{11}; }
    static byte[] queryBucket(String bucket) { return write(out -> { out.writeByte(12); out.writeUTF(bucket); }); }
    static byte[] queryReference(String bucket, String key) { return write(out -> { out.writeByte(13); out.writeUTF(bucket); out.writeUTF(key); }); }
    static byte[] queryBuckets() { return new byte[]{14}; }

    static Command readCommand(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int type = in.readUnsignedByte();
            if (type == 1) return new CreateBucket(in.readUTF());
            if (type == 2) {
                String bucket = in.readUTF(), key = in.readUTF(); long prior = in.readLong();
                String operation = in.readUTF(), artifact = in.readUTF(); long length = in.readLong();
                String sha = in.readUTF(), topology = in.readUTF(), policy = in.readUTF();
                int count = in.readInt(); List<NodeIdentity> replicas = new ArrayList<>();
                for (int i = 0; i < count; i++) replicas.add(NodeIdentity.parse(in.readUTF()));
                ClusterObjectMetadata metadata = in.available() == 0
                        ? ClusterObjectMetadata.EMPTY : readMetadata(in);
                return new Publish(bucket, key, prior, operation, artifact, length, sha,
                        topology, policy, replicas, metadata);
            }
            if (type == 11) return new QueryMembership();
            if (type == 12) return new QueryBucket(in.readUTF());
            if (type == 13) return new QueryReference(in.readUTF(), in.readUTF());
            if (type == 14) return new QueryBuckets();
            throw new IOException("unknown command " + type);
        } catch (IOException e) { throw new IllegalArgumentException("invalid control command", e); }
    }

    static byte[] result(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    static String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
    static String encode(BucketNamespace b) { return b.bucket() + "\t" + b.generation(); }
    static BucketNamespace decodeBucket(String value) { String[] f = value.split("\\t", -1); return new BucketNamespace(f[0], Long.parseLong(f[1])); }
    static String encodeBuckets(List<BucketNamespace> buckets) {
        return buckets.stream().map(ControlPlaneCodec::encode).reduce((left, right) -> left + "\\n" + right).orElse("");
    }
    static List<BucketNamespace> decodeBuckets(String value) {
        if (value.isEmpty()) return List.of();
        return java.util.Arrays.stream(value.split("\\n", -1)).map(ControlPlaneCodec::decodeBucket).toList();
    }
    static String encode(ObjectReferenceGeneration r) {
        return String.join("\t", r.bucket(), r.objectKey(), Long.toString(r.generation()), r.operationId(), r.artifactId(),
                Long.toString(r.length()), r.sha256(), r.topologyEpoch(), r.policyEpoch(),
                r.replicas().stream().sorted().map(NodeIdentity::toString).reduce((a,b) -> a + "," + b).orElse(""),
                encodeMetadata(r.metadata()));
    }
    static ObjectReferenceGeneration decodeReference(String value) {
        String[] f = value.split("\\t", -1);
        List<NodeIdentity> replicas = f[9].isEmpty() ? List.of() : java.util.Arrays.stream(f[9].split(",")).map(NodeIdentity::parse).toList();
        ClusterObjectMetadata metadata = f.length > 10
                ? decodeMetadata(f[10]) : ClusterObjectMetadata.EMPTY;
        return new ObjectReferenceGeneration(f[0], f[1], Long.parseLong(f[2]), f[3], f[4],
                Long.parseLong(f[5]), f[6], f[7], f[8], replicas, metadata);
    }

    private static String encodeMetadata(ClusterObjectMetadata metadata) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(write(out -> writeMetadata(out, metadata)));
    }

    private static ClusterObjectMetadata decodeMetadata(String encoded) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                Base64.getUrlDecoder().decode(encoded)))) {
            return readMetadata(in);
        } catch (IOException failure) {
            throw new IllegalArgumentException("invalid committed object metadata", failure);
        }
    }

    private static void writeMetadata(DataOutputStream out, ClusterObjectMetadata metadata) throws IOException {
        out.writeUTF(metadata.storageClass());
        out.writeUTF(metadata.etag());
        out.writeLong(metadata.createdAt().toEpochMilli());
        writeMap(out, metadata.userMetadata());
        writeMap(out, metadata.objectTags());
    }

    private static ClusterObjectMetadata readMetadata(DataInputStream in) throws IOException {
        String storageClass = in.readUTF();
        String etag = in.readUTF();
        Instant createdAt = Instant.ofEpochMilli(in.readLong());
        return new ClusterObjectMetadata(storageClass, readMap(in), readMap(in), etag, createdAt);
    }

    private static void writeMap(DataOutputStream out, Map<String, String> values) throws IOException {
        Map<String, String> sorted = new TreeMap<>(values);
        out.writeInt(sorted.size());
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeUTF(entry.getValue());
        }
    }

    private static Map<String, String> readMap(DataInputStream in) throws IOException {
        int count = in.readInt();
        Map<String, String> values = new TreeMap<>();
        for (int index = 0; index < count; index++) values.put(in.readUTF(), in.readUTF());
        return values;
    }

    private static byte[] write(IoWriter writer) {
        try { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (DataOutputStream out = new DataOutputStream(bytes)) { writer.write(out); } return bytes.toByteArray(); }
        catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }
    @FunctionalInterface private interface IoWriter { void write(DataOutputStream out) throws IOException; }
    sealed interface Command permits CreateBucket, Publish, QueryMembership, QueryBucket, QueryReference, QueryBuckets {}
    record CreateBucket(String bucket) implements Command {}
    record Publish(String bucket, String key, long priorGeneration, String operationId, String artifactId, long length,
                   String sha256, String topologyEpoch, String policyEpoch, List<NodeIdentity> replicas,
                   ClusterObjectMetadata metadata) implements Command {
        Publish { replicas = replicas.stream().distinct().sorted(Comparator.naturalOrder()).toList(); }
    }
    record QueryMembership() implements Command {}
    record QueryBucket(String bucket) implements Command {}
    record QueryReference(String bucket, String key) implements Command {}
    record QueryBuckets() implements Command {}
}
