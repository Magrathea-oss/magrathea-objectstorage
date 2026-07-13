package com.example.magrathea.cluster.control.ratis;

import com.example.magrathea.storageengine.cluster.application.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class ControlPlaneCodec {
    private static final int REPAIR_COMMAND_VERSION = 1;

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

    static byte[] repair(RepairCommands.Command command) {
        return write(out -> {
            out.writeByte(switch (command) {
                case RepairCommands.Ensure ignored -> 21;
                case RepairCommands.Claim ignored -> 22;
                case RepairCommands.Renew ignored -> 23;
                case RepairCommands.Retry ignored -> 24;
                case RepairCommands.Block ignored -> 25;
                case RepairCommands.Succeed ignored -> 26;
                case RepairCommands.Obsolete ignored -> 27;
                case RepairCommands.Reevaluate ignored -> 28;
            });
            out.writeInt(REPAIR_COMMAND_VERSION);
            writeCommandHeader(out, command);
            switch (command) {
                case RepairCommands.Ensure ensure -> {
                    writeSpecification(out, ensure.specification());
                    writeNullableIdentity(out, ensure.sourceHint());
                }
                case RepairCommands.Claim claim -> {
                    out.writeUTF(claim.owner().toString()); out.writeUTF(claim.processSession());
                    writeInstant(out, claim.deadline()); writeNullableIdentity(out, claim.sourceHint());
                }
                case RepairCommands.Renew renew -> {
                    writeFence(out, renew.claimGeneration(), renew.owner(), renew.processSession());
                    writeInstant(out, renew.deadline());
                }
                case RepairCommands.Retry retry -> {
                    writeFence(out, retry.claimGeneration(), retry.owner(), retry.processSession()); out.writeUTF(retry.reason());
                }
                case RepairCommands.Block block -> {
                    writeFence(out, block.claimGeneration(), block.owner(), block.processSession()); out.writeUTF(block.reason());
                }
                case RepairCommands.Succeed succeed -> {
                    writeFence(out, succeed.claimGeneration(), succeed.owner(), succeed.processSession());
                    out.writeLong(succeed.durableLength()); out.writeUTF(succeed.durableSha256()); out.writeUTF(succeed.reason());
                }
                case RepairCommands.Obsolete obsolete -> {
                    out.writeLong(obsolete.claimGeneration()); writeNullableIdentity(out, obsolete.owner());
                    writeNullableText(out, obsolete.processSession()); out.writeUTF(obsolete.reason());
                }
                case RepairCommands.Reevaluate reevaluate -> {
                    out.writeUTF(reevaluate.reason()); writeNullableIdentity(out, reevaluate.sourceHint());
                }
            }
        });
    }

    static byte[] queryRepair(RepairJobId jobId) {
        return write(out -> { out.writeByte(31); out.writeInt(REPAIR_COMMAND_VERSION); out.writeUTF(jobId.value()); });
    }

    static byte[] queryRepairs(RepairJobQuery query) {
        return write(out -> {
            out.writeByte(32); out.writeInt(REPAIR_COMMAND_VERSION);
            List<RepairState> states = query.states().stream().sorted().toList();
            out.writeInt(states.size()); for (RepairState state : states) out.writeUTF(state.name());
            writeNullableInstant(out, query.eligibleAt());
        });
    }

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
            if (type >= 21 && type <= 28) {
                requireRepairVersion(in);
                String commandId = in.readUTF(); RepairJobId jobId = new RepairJobId(in.readUTF()); Instant at = readInstant(in);
                RepairCommands.Command command = switch (type) {
                    case 21 -> {
                        RepairSpecification specification = readSpecification(in);
                        if (!specification.jobId().equals(jobId)) throw new IOException("repair command job ID mismatch");
                        yield new RepairCommands.Ensure(commandId, specification, at, readNullableIdentity(in));
                    }
                    case 22 -> new RepairCommands.Claim(commandId, jobId, NodeIdentity.parse(in.readUTF()), in.readUTF(), at,
                            readInstant(in), readNullableIdentity(in));
                    case 23 -> new RepairCommands.Renew(commandId, jobId, in.readLong(), NodeIdentity.parse(in.readUTF()),
                            in.readUTF(), at, readInstant(in));
                    case 24 -> new RepairCommands.Retry(commandId, jobId, in.readLong(), NodeIdentity.parse(in.readUTF()),
                            in.readUTF(), at, in.readUTF());
                    case 25 -> new RepairCommands.Block(commandId, jobId, in.readLong(), NodeIdentity.parse(in.readUTF()),
                            in.readUTF(), at, in.readUTF());
                    case 26 -> new RepairCommands.Succeed(commandId, jobId, in.readLong(), NodeIdentity.parse(in.readUTF()),
                            in.readUTF(), at, in.readLong(), in.readUTF(), in.readUTF());
                    case 27 -> new RepairCommands.Obsolete(commandId, jobId, in.readLong(), readNullableIdentity(in),
                            readNullableText(in), at, in.readUTF());
                    case 28 -> new RepairCommands.Reevaluate(commandId, jobId, at, in.readUTF(), readNullableIdentity(in));
                    default -> throw new IOException("unknown repair command " + type);
                };
                return new RepairWrite(command);
            }
            if (type == 31) { requireRepairVersion(in); return new QueryRepair(new RepairJobId(in.readUTF())); }
            if (type == 32) {
                requireRepairVersion(in); int count = in.readInt(); java.util.Set<RepairState> states = new java.util.HashSet<>();
                for (int i = 0; i < count; i++) states.add(RepairState.valueOf(in.readUTF()));
                return new QueryRepairs(new RepairJobQuery(states, readNullableInstant(in)));
            }
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

    static String encode(RepairCommandResult result) { return encodeBinary(out -> writeRepairResult(out, result)); }
    static RepairCommandResult decodeRepairResult(String encoded) { return decodeBinary(encoded, ControlPlaneCodec::readRepairResult); }
    static String encode(RepairJob job) { return encodeBinary(out -> writeRepairJob(out, job)); }
    static RepairJob decodeRepairJob(String encoded) { return decodeBinary(encoded, ControlPlaneCodec::readRepairJob); }
    static String encodeRepairJobs(List<RepairJob> jobs) { return jobs.stream().map(ControlPlaneCodec::encode).reduce((a,b) -> a + "\n" + b).orElse(""); }
    static List<RepairJob> decodeRepairJobs(String encoded) {
        if (encoded.isEmpty()) return List.of();
        return java.util.Arrays.stream(encoded.split("\n", -1)).map(ControlPlaneCodec::decodeRepairJob).toList();
    }

    static void writeRepairJob(DataOutputStream out, RepairJob job) throws IOException {
        writeSpecification(out, job.specification()); out.writeUTF(job.state().name());
        out.writeLong(job.attemptNumber()); out.writeLong(job.claimGeneration());
        out.writeBoolean(job.claim() != null);
        if (job.claim() != null) {
            out.writeUTF(job.claim().owner().toString()); out.writeUTF(job.claim().processSession());
            writeInstant(out, job.claim().deadline()); out.writeLong(job.claim().attemptNumber()); out.writeLong(job.claim().claimGeneration());
        }
        writeNullableInstant(out, job.nextEligibleAt()); out.writeUTF(job.reason());
        writeInstant(out, job.createdAt()); writeInstant(out, job.updatedAt());
        out.writeInt(job.history().size()); for (RepairHistoryEntry entry : job.history()) writeHistory(out, entry);
        out.writeInt(job.commandResults().size()); for (RepairCommandResult result : job.commandResults()) writeRepairResult(out, result);
    }

    static RepairJob readRepairJob(DataInputStream in) throws IOException {
        RepairSpecification specification = readSpecification(in); RepairState state = RepairState.valueOf(in.readUTF());
        long attempt = in.readLong(), generation = in.readLong(); RepairClaim claim = null;
        if (in.readBoolean()) claim = new RepairClaim(NodeIdentity.parse(in.readUTF()), in.readUTF(), readInstant(in), in.readLong(), in.readLong());
        Instant eligible = readNullableInstant(in); String reason = in.readUTF(); Instant created = readInstant(in), updated = readInstant(in);
        int historyCount = readCount(in, "repair history"); List<RepairHistoryEntry> history = new ArrayList<>();
        for (int i = 0; i < historyCount; i++) history.add(readHistory(in));
        int resultCount = readCount(in, "repair results"); List<RepairCommandResult> results = new ArrayList<>();
        for (int i = 0; i < resultCount; i++) results.add(readRepairResult(in));
        return new RepairJob(specification, state, attempt, generation, claim, eligible, reason, created, updated, history, results);
    }

    static void writeRepairResult(DataOutputStream out, RepairCommandResult result) throws IOException {
        out.writeUTF(result.commandId()); out.writeUTF(result.jobId().value()); out.writeBoolean(result.accepted());
        out.writeUTF(result.code().name()); writeNullableText(out, result.state() == null ? null : result.state().name());
        out.writeLong(result.attemptNumber()); out.writeLong(result.claimGeneration()); writeInstant(out, result.occurredAt()); out.writeUTF(result.reason());
    }

    static RepairCommandResult readRepairResult(DataInputStream in) throws IOException {
        String commandId = in.readUTF(); RepairJobId jobId = new RepairJobId(in.readUTF()); boolean accepted = in.readBoolean();
        RepairCommandResult.Code code = RepairCommandResult.Code.valueOf(in.readUTF()); String stateName = readNullableText(in);
        return new RepairCommandResult(commandId, jobId, accepted, code, stateName == null ? null : RepairState.valueOf(stateName),
                in.readLong(), in.readLong(), readInstant(in), in.readUTF());
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
        int count = readCount(in, "map");
        Map<String, String> values = new TreeMap<>();
        for (int index = 0; index < count; index++) values.put(in.readUTF(), in.readUTF());
        return values;
    }

    private static void writeCommandHeader(DataOutputStream out, RepairCommands.Command command) throws IOException {
        out.writeUTF(command.commandId()); out.writeUTF(command.jobId().value()); writeInstant(out, command.occurredAt());
    }

    private static void writeFence(DataOutputStream out, long generation, NodeIdentity owner, String session) throws IOException {
        out.writeLong(generation); out.writeUTF(owner.toString()); out.writeUTF(session);
    }

    private static void writeSpecification(DataOutputStream out, RepairSpecification specification) throws IOException {
        out.writeUTF(specification.jobId().value()); out.writeUTF(specification.bucket()); out.writeUTF(specification.objectKey());
        out.writeLong(specification.referenceGeneration()); out.writeUTF(specification.artifactId()); out.writeUTF(specification.target().toString());
        out.writeLong(specification.length()); out.writeUTF(specification.sha256()); out.writeUTF(specification.topologyEpoch());
        out.writeUTF(specification.policyEpoch()); RepairRetryPolicy retry = specification.retryPolicy();
        out.writeInt(retry.maximumAttempts()); out.writeLong(retry.initialBackoff().toMillis());
        out.writeLong(retry.maximumBackoff().toMillis()); out.writeLong(retry.maximumClaimDuration().toMillis());
    }

    private static RepairSpecification readSpecification(DataInputStream in) throws IOException {
        RepairJobId id = new RepairJobId(in.readUTF()); String bucket = in.readUTF(), key = in.readUTF(); long generation = in.readLong();
        String artifact = in.readUTF(); NodeIdentity target = NodeIdentity.parse(in.readUTF()); long length = in.readLong();
        String sha = in.readUTF(), topology = in.readUTF(), policy = in.readUTF();
        RepairRetryPolicy retry = new RepairRetryPolicy(in.readInt(), Duration.ofMillis(in.readLong()),
                Duration.ofMillis(in.readLong()), Duration.ofMillis(in.readLong()));
        return new RepairSpecification(id, bucket, key, generation, artifact, target, length, sha, topology, policy, retry);
    }

    private static void writeHistory(DataOutputStream out, RepairHistoryEntry entry) throws IOException {
        out.writeUTF(entry.commandId()); out.writeUTF(entry.event());
        writeNullableText(out, entry.fromState() == null ? null : entry.fromState().name()); out.writeUTF(entry.toState().name());
        writeInstant(out, entry.occurredAt()); out.writeLong(entry.attemptNumber()); out.writeLong(entry.claimGeneration());
        writeNullableIdentity(out, entry.owner()); writeNullableText(out, entry.processSession()); out.writeUTF(entry.reason());
        writeNullableIdentity(out, entry.sourceHint());
    }

    private static RepairHistoryEntry readHistory(DataInputStream in) throws IOException {
        String command = in.readUTF(), event = in.readUTF(), fromName = readNullableText(in); RepairState to = RepairState.valueOf(in.readUTF());
        Instant occurred = readInstant(in); long attempt = in.readLong(), generation = in.readLong(); NodeIdentity owner = readNullableIdentity(in);
        String session = readNullableText(in), reason = in.readUTF(); NodeIdentity source = readNullableIdentity(in);
        return new RepairHistoryEntry(command, event, fromName == null ? null : RepairState.valueOf(fromName), to, occurred,
                attempt, generation, owner, session, reason, source);
    }

    private static void writeInstant(DataOutputStream out, Instant value) throws IOException {
        out.writeLong(value.getEpochSecond()); out.writeInt(value.getNano());
    }
    private static Instant readInstant(DataInputStream in) throws IOException { return Instant.ofEpochSecond(in.readLong(), in.readInt()); }
    private static void writeNullableInstant(DataOutputStream out, Instant value) throws IOException {
        out.writeBoolean(value != null); if (value != null) writeInstant(out, value);
    }
    private static Instant readNullableInstant(DataInputStream in) throws IOException { return in.readBoolean() ? readInstant(in) : null; }
    private static void writeNullableIdentity(DataOutputStream out, NodeIdentity value) throws IOException {
        writeNullableText(out, value == null ? null : value.toString());
    }
    private static NodeIdentity readNullableIdentity(DataInputStream in) throws IOException {
        String value = readNullableText(in); return value == null ? null : NodeIdentity.parse(value);
    }
    private static void writeNullableText(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null); if (value != null) out.writeUTF(value);
    }
    private static String readNullableText(DataInputStream in) throws IOException { return in.readBoolean() ? in.readUTF() : null; }
    private static int readCount(DataInputStream in, String name) throws IOException {
        int count = in.readInt(); if (count < 0 || count > 1_000_000) throw new IOException("invalid " + name + " count"); return count;
    }
    private static void requireRepairVersion(DataInputStream in) throws IOException {
        int version = in.readInt(); if (version != REPAIR_COMMAND_VERSION) throw new IOException("unsupported repair command version " + version);
    }

    private static String encodeBinary(IoWriter writer) { return Base64.getUrlEncoder().withoutPadding().encodeToString(write(writer)); }
    private static <T> T decodeBinary(String encoded, IoReader<T> reader) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(encoded)))) {
            T value = reader.read(in); if (in.available() != 0) throw new IOException("trailing encoded control data"); return value;
        } catch (IOException | IllegalArgumentException failure) { throw new IllegalArgumentException("invalid encoded control data", failure); }
    }

    private static byte[] write(IoWriter writer) {
        try { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (DataOutputStream out = new DataOutputStream(bytes)) { writer.write(out); } return bytes.toByteArray(); }
        catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }
    @FunctionalInterface private interface IoWriter { void write(DataOutputStream out) throws IOException; }
    @FunctionalInterface private interface IoReader<T> { T read(DataInputStream in) throws IOException; }
    sealed interface Command permits CreateBucket, Publish, QueryMembership, QueryBucket, QueryReference, QueryBuckets,
            RepairWrite, QueryRepair, QueryRepairs {}
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
    record RepairWrite(RepairCommands.Command command) implements Command {}
    record QueryRepair(RepairJobId jobId) implements Command {}
    record QueryRepairs(RepairJobQuery query) implements Command {}
}
