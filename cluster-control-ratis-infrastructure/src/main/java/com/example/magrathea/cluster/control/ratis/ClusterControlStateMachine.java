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

/** Deterministic replicated state machine for fixed metadata and fenced repair control facts. */
final class ClusterControlStateMachine extends BaseStateMachine {
    private static final int SNAPSHOT_VERSION_1 = 1;
    private static final int SNAPSHOT_VERSION_2 = 2;
    private final MembershipSnapshot membership;
    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();
    private final NavigableMap<String, BucketNamespace> buckets = new TreeMap<>();
    private final NavigableMap<String, ObjectReferenceGeneration> references = new TreeMap<>();
    private final NavigableMap<RepairJobId, MutableRepairJob> repairJobs = new TreeMap<>();
    private final NavigableMap<String, RepairCommandResult> commandResults = new TreeMap<>();

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
            obsoleteSupersededJobs(next, "publish:" + publish.operationId(), publish.metadata().createdAt());
            return "OK\tR\t" + ControlPlaneCodec.encode(next);
        }
        if (command instanceof ControlPlaneCodec.RepairWrite repair) {
            return "OK\tJ\t" + ControlPlaneCodec.encode(applyRepair(repair.command()));
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
        if (command instanceof ControlPlaneCodec.QueryRepair query) {
            MutableRepairJob job = repairJobs.get(query.jobId());
            return job == null ? error(ControlPlaneException.Code.NOT_FOUND, "repair job not found")
                    : "OK\tW\t" + ControlPlaneCodec.encode(view(job));
        }
        if (command instanceof ControlPlaneCodec.QueryRepairs query) {
            List<RepairJob> jobs = repairJobs.values().stream().filter(job -> matches(job, query.query()))
                    .map(this::view).toList();
            return "OK\tX\t" + ControlPlaneCodec.encodeRepairJobs(jobs);
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
            out.writeInt(SNAPSHOT_VERSION_2); out.writeUTF(membership.topologyEpoch()); out.writeUTF(membership.policyEpoch());
            out.writeInt(buckets.size()); for (BucketNamespace bucket : buckets.values()) { out.writeUTF(bucket.bucket()); out.writeLong(bucket.generation()); }
            out.writeInt(references.size()); for (ObjectReferenceGeneration reference : references.values()) out.writeUTF(ControlPlaneCodec.encode(reference));
            out.writeInt(repairJobs.size()); for (MutableRepairJob job : repairJobs.values()) ControlPlaneCodec.writeRepairJob(out, view(job));
            out.writeInt(commandResults.size()); for (RepairCommandResult result : commandResults.values()) ControlPlaneCodec.writeRepairResult(out, result);
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        var digest = MD5FileUtil.computeAndSaveMd5ForFile(target);
        storage.updateLatestSnapshot(new SingleFileSnapshotInfo(new FileInfo(target.toPath(), digest), applied));
        return applied.getIndex();
    }

    private synchronized void loadSnapshot(SingleFileSnapshotInfo snapshot) throws IOException {
        if (snapshot == null) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(snapshot.getFile().getPath().toFile())))) {
            int version = in.readInt();
            if (version != SNAPSHOT_VERSION_1 && version != SNAPSHOT_VERSION_2) {
                throw new IOException("unsupported control snapshot version " + version);
            }
            if (!membership.topologyEpoch().equals(in.readUTF()) || !membership.policyEpoch().equals(in.readUTF())) throw new IOException("bootstrap epochs cannot rewrite persisted state");
            buckets.clear(); int bucketCount = readCount(in, "bucket"); for (int i=0;i<bucketCount;i++) { BucketNamespace b = new BucketNamespace(in.readUTF(), in.readLong()); buckets.put(b.bucket(), b); }
            references.clear(); int referenceCount = readCount(in, "reference"); for (int i=0;i<referenceCount;i++) { ObjectReferenceGeneration r = ControlPlaneCodec.decodeReference(in.readUTF()); references.put(r.namespaceKey(), r); }
            repairJobs.clear(); commandResults.clear();
            if (version == SNAPSHOT_VERSION_2) {
                int repairCount = readCount(in, "repair");
                for (int i = 0; i < repairCount; i++) {
                    RepairJob restored = ControlPlaneCodec.readRepairJob(in); MutableRepairJob job = new MutableRepairJob(restored);
                    if (repairJobs.put(job.specification.jobId(), job) != null) throw new IOException("duplicate repair job in snapshot");
                    for (RepairCommandResult result : restored.commandResults()) mergeCommandResult(result);
                }
                int commandResultCount = readCount(in, "repair command result");
                for (int i = 0; i < commandResultCount; i++) mergeCommandResult(ControlPlaneCodec.readRepairResult(in));
            }
            if (in.read() != -1) throw new IOException("trailing control snapshot data");
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid control snapshot", invalid);
        }
        setLastAppliedTermIndex(snapshot.getTermIndex());
    }

    private RepairCommandResult applyRepair(RepairCommands.Command command) {
        RepairCommandResult duplicate = commandResults.get(command.commandId());
        if (duplicate != null) return duplicate;
        if (command instanceof RepairCommands.Ensure ensure) return ensure(ensure);
        MutableRepairJob job = repairJobs.get(command.jobId());
        if (job == null) return remember(result(command, false, RepairCommandResult.Code.NOT_FOUND, null, 0, 0, "repair job not found"));
        if (command instanceof RepairCommands.Claim claim) return claim(job, claim);
        if (command instanceof RepairCommands.Renew renew) return renew(job, renew);
        if (command instanceof RepairCommands.Retry retry) return retry(job, retry);
        if (command instanceof RepairCommands.Block block) return block(job, block);
        if (command instanceof RepairCommands.Succeed succeed) return succeed(job, succeed);
        if (command instanceof RepairCommands.Obsolete obsolete) return obsolete(job, obsolete);
        if (command instanceof RepairCommands.Reevaluate reevaluate) return reevaluate(job, reevaluate);
        return remember(result(command, false, RepairCommandResult.Code.ILLEGAL_TRANSITION,
                job.state, job.attemptNumber, job.claimGeneration, "unsupported repair command"));
    }

    private RepairCommandResult ensure(RepairCommands.Ensure command) {
        RepairSpecification specification = command.specification();
        MutableRepairJob existing = repairJobs.get(specification.jobId());
        if (!isExactCurrent(specification)) {
            return remember(result(command, false, RepairCommandResult.Code.INVALID_REFERENCE,
                    existing == null ? null : existing.state, existing == null ? 0 : existing.attemptNumber,
                    existing == null ? 0 : existing.claimGeneration, "repair specification is not the exact current reference target"));
        }
        if (existing != null) {
            if (!existing.specification.equals(specification)) {
                return remember(result(command, false, RepairCommandResult.Code.INVALID_REFERENCE, existing.state,
                        existing.attemptNumber, existing.claimGeneration, "canonical repair identity has different immutable facts"));
            }
            return remember(result(command, true, RepairCommandResult.Code.NO_CHANGE, existing.state,
                    existing.attemptNumber, existing.claimGeneration, "logical repair job already exists"));
        }
        MutableRepairJob job = new MutableRepairJob(specification, command.occurredAt());
        job.history.add(history(command, "ENSURE", null, RepairState.READY, job, "exact current obligation", command.sourceHint()));
        repairJobs.put(specification.jobId(), job);
        return remember(result(command, true, RepairCommandResult.Code.APPLIED, job.state, 0, 0, "repair job ensured"));
    }

    private RepairCommandResult claim(MutableRepairJob job, RepairCommands.Claim command) {
        if (!isExactCurrent(job.specification)) return transitionObsolete(job, command, "bound reference is no longer current");
        if (!membership.voterIdentities().contains(command.owner())) return reject(job, command, RepairCommandResult.Code.ILLEGAL_TRANSITION, "claim owner is not a fixed voter");
        if (!command.deadline().isAfter(command.occurredAt())
                || command.deadline().isAfter(command.occurredAt().plus(job.specification.retryPolicy().maximumClaimDuration()))) {
            return reject(job, command, RepairCommandResult.Code.INVALID_DEADLINE, "claim deadline exceeds committed finite bound");
        }
        boolean eligible = job.state == RepairState.READY
                || job.state == RepairState.RETRY_WAIT && !command.occurredAt().isBefore(job.nextEligibleAt)
                || job.state == RepairState.CLAIMED && !command.occurredAt().isBefore(job.claim.deadline());
        if (!eligible) return reject(job, command, RepairCommandResult.Code.ILLEGAL_TRANSITION, "job is not claimable at command timestamp");
        if (!job.specification.retryPolicy().permitsAttempt(job.attemptNumber)) {
            return transition(job, command, RepairState.BLOCKED, "retry attempts exhausted", null,
                    RepairCommandResult.Code.RETRY_EXHAUSTED, true, command.sourceHint());
        }
        RepairState from = job.state; job.attemptNumber++; job.claimGeneration++;
        job.state = RepairState.CLAIMED; job.claim = new RepairClaim(command.owner(), command.processSession(), command.deadline(),
                job.attemptNumber, job.claimGeneration); job.nextEligibleAt = null; job.reason = "claimed"; job.updatedAt = command.occurredAt();
        job.history.add(history(command, from == RepairState.CLAIMED ? "RECLAIM" : "CLAIM", from, job.state, job, job.reason, command.sourceHint()));
        return remember(result(command, true, RepairCommandResult.Code.APPLIED, job.state, job.attemptNumber, job.claimGeneration, job.reason));
    }

    private RepairCommandResult renew(MutableRepairJob job, RepairCommands.Renew command) {
        if (!matchesClaim(job, command.claimGeneration(), command.owner(), command.processSession())) return stale(job, command);
        if (!isExactCurrent(job.specification)) return transitionObsolete(job, command, "bound reference is no longer current");
        if (!command.deadline().isAfter(command.occurredAt())
                || command.deadline().isAfter(command.occurredAt().plus(job.specification.retryPolicy().maximumClaimDuration()))) {
            return reject(job, command, RepairCommandResult.Code.INVALID_DEADLINE, "renewed deadline exceeds committed finite bound");
        }
        job.claim = new RepairClaim(job.claim.owner(), job.claim.processSession(), command.deadline(), job.attemptNumber, job.claimGeneration);
        job.updatedAt = command.occurredAt(); job.history.add(history(command, "RENEW", job.state, job.state, job, "claim renewed", null));
        return remember(result(command, true, RepairCommandResult.Code.APPLIED, job.state, job.attemptNumber, job.claimGeneration, "claim renewed"));
    }

    private RepairCommandResult retry(MutableRepairJob job, RepairCommands.Retry command) {
        if (!matchesClaim(job, command.claimGeneration(), command.owner(), command.processSession())) return stale(job, command);
        if (!isExactCurrent(job.specification)) return transitionObsolete(job, command, "bound reference is no longer current");
        if (!job.specification.retryPolicy().permitsAttempt(job.attemptNumber)) {
            return transition(job, command, RepairState.BLOCKED, "retry attempts exhausted: " + command.reason(), null,
                    RepairCommandResult.Code.RETRY_EXHAUSTED, true, null);
        }
        return transition(job, command, RepairState.RETRY_WAIT, command.reason(),
                job.specification.retryPolicy().nextEligibleAt(command.occurredAt(), job.attemptNumber),
                RepairCommandResult.Code.APPLIED, true, null);
    }

    private RepairCommandResult block(MutableRepairJob job, RepairCommands.Block command) {
        if (!matchesClaim(job, command.claimGeneration(), command.owner(), command.processSession())) return stale(job, command);
        if (!isExactCurrent(job.specification)) return transitionObsolete(job, command, "bound reference is no longer current");
        return transition(job, command, RepairState.BLOCKED, command.reason(), null, RepairCommandResult.Code.APPLIED, true, null);
    }

    private RepairCommandResult succeed(MutableRepairJob job, RepairCommands.Succeed command) {
        if (!matchesClaim(job, command.claimGeneration(), command.owner(), command.processSession())) return stale(job, command);
        if (!isExactCurrent(job.specification)) return transitionObsolete(job, command, "bound reference is no longer current");
        if (command.durableLength() != job.specification.length()
                || !command.durableSha256().equalsIgnoreCase(job.specification.sha256())) {
            return reject(job, command, RepairCommandResult.Code.ILLEGAL_TRANSITION, "durable publication facts do not match immutable repair facts");
        }
        return transition(job, command, RepairState.SUCCEEDED, command.reason(), null, RepairCommandResult.Code.APPLIED, true, null);
    }

    private RepairCommandResult obsolete(MutableRepairJob job, RepairCommands.Obsolete command) {
        if (job.state == RepairState.OBSOLETE) return reject(job, command, RepairCommandResult.Code.ILLEGAL_TRANSITION, "obsolete is terminal");
        if (job.state == RepairState.CLAIMED
                && !matchesClaim(job, command.claimGeneration(), command.owner(), command.processSession())) return stale(job, command);
        if (isExactCurrent(job.specification)) return reject(job, command, RepairCommandResult.Code.INVALID_REFERENCE, "repair obligation is still current");
        return transitionObsolete(job, command, command.reason());
    }

    private RepairCommandResult reevaluate(MutableRepairJob job, RepairCommands.Reevaluate command) {
        if (job.state != RepairState.BLOCKED && job.state != RepairState.SUCCEEDED) {
            return reject(job, command, RepairCommandResult.Code.ILLEGAL_TRANSITION, "only blocked or succeeded work can be re-evaluated");
        }
        if (!isExactCurrent(job.specification)) return transitionObsolete(job, command, "bound reference is no longer current");
        return transition(job, command, RepairState.READY, command.reason(), null,
                RepairCommandResult.Code.APPLIED, true, command.sourceHint());
    }

    private RepairCommandResult transitionObsolete(MutableRepairJob job, RepairCommands.Command command, String reason) {
        if (job.state == RepairState.OBSOLETE) return reject(job, command, RepairCommandResult.Code.ILLEGAL_TRANSITION, "obsolete is terminal");
        return transition(job, command, RepairState.OBSOLETE, reason, null, RepairCommandResult.Code.APPLIED, true, null);
    }

    private RepairCommandResult transition(MutableRepairJob job, RepairCommands.Command command, RepairState to,
                                           String reason, java.time.Instant nextEligible,
                                           RepairCommandResult.Code code, boolean accepted, NodeIdentity sourceHint) {
        RepairState from = job.state; job.state = to; job.claim = null; job.nextEligibleAt = nextEligible;
        job.reason = reason; job.updatedAt = command.occurredAt();
        job.history.add(history(command, to.name(), from, to, job, reason, sourceHint));
        return remember(result(command, accepted, code, job.state, job.attemptNumber, job.claimGeneration, reason));
    }

    private RepairCommandResult reject(MutableRepairJob job, RepairCommands.Command command,
                                       RepairCommandResult.Code code, String reason) {
        return remember(result(command, false, code, job.state, job.attemptNumber, job.claimGeneration, reason));
    }

    private RepairCommandResult stale(MutableRepairJob job, RepairCommands.Command command) {
        return reject(job, command, RepairCommandResult.Code.STALE_TOKEN, "claim token or process session is stale");
    }

    private boolean matchesClaim(MutableRepairJob job, long generation, NodeIdentity owner, String session) {
        return job.state == RepairState.CLAIMED && job.claim != null && job.claimGeneration == generation
                && job.claim.owner().equals(owner) && job.claim.processSession().equals(session);
    }

    private void obsoleteSupersededJobs(ObjectReferenceGeneration current, String commandId, java.time.Instant occurredAt) {
        for (MutableRepairJob job : repairJobs.values()) {
            if (job.state != RepairState.OBSOLETE && job.specification.namespaceKey().equals(current.namespaceKey())
                    && !isExact(current, job.specification)) {
                RepairState from = job.state; job.state = RepairState.OBSOLETE; job.claim = null; job.nextEligibleAt = null;
                job.reason = "current reference no longer names exact repair obligation"; job.updatedAt = occurredAt;
                job.history.add(new RepairHistoryEntry(commandId, "PUBLISH_OBSOLETE", from, RepairState.OBSOLETE, occurredAt,
                        job.attemptNumber, job.claimGeneration, null, "", job.reason, null));
            }
        }
    }

    private boolean isExactCurrent(RepairSpecification specification) {
        ObjectReferenceGeneration current = references.get(specification.namespaceKey());
        return current != null && isExact(current, specification);
    }

    private static boolean isExact(ObjectReferenceGeneration reference, RepairSpecification specification) {
        return reference.generation() == specification.referenceGeneration()
                && reference.artifactId().equals(specification.artifactId())
                && reference.length() == specification.length()
                && reference.sha256().equalsIgnoreCase(specification.sha256())
                && reference.topologyEpoch().equals(specification.topologyEpoch())
                && reference.policyEpoch().equals(specification.policyEpoch())
                && reference.replicas().contains(specification.target());
    }

    private RepairHistoryEntry history(RepairCommands.Command command, String event, RepairState from, RepairState to,
                                       MutableRepairJob job, String reason, NodeIdentity sourceHint) {
        NodeIdentity owner = job.claim == null ? null : job.claim.owner(); String session = job.claim == null ? "" : job.claim.processSession();
        return new RepairHistoryEntry(command.commandId(), event, from, to, command.occurredAt(), job.attemptNumber,
                job.claimGeneration, owner, session, reason, sourceHint);
    }

    private RepairCommandResult result(RepairCommands.Command command, boolean accepted, RepairCommandResult.Code code,
                                       RepairState state, long attempt, long generation, String reason) {
        return new RepairCommandResult(command.commandId(), command.jobId(), accepted, code, state, attempt, generation,
                command.occurredAt(), reason);
    }

    private RepairCommandResult remember(RepairCommandResult result) {
        commandResults.put(result.commandId(), result); return result;
    }

    private void mergeCommandResult(RepairCommandResult result) throws IOException {
        RepairCommandResult duplicate = commandResults.put(result.commandId(), result);
        if (duplicate != null && !duplicate.equals(result)) throw new IOException("conflicting repair command result in snapshot");
    }

    private RepairJob view(MutableRepairJob job) {
        List<RepairCommandResult> results = commandResults.values().stream().filter(result -> result.jobId().equals(job.specification.jobId())).toList();
        return new RepairJob(job.specification, job.state, job.attemptNumber, job.claimGeneration, job.claim,
                job.nextEligibleAt, job.reason, job.createdAt, job.updatedAt, job.history, results);
    }

    private static boolean matches(MutableRepairJob job, RepairJobQuery query) {
        if (!query.states().isEmpty() && !query.states().contains(job.state)) return false;
        if (query.eligibleAt() == null) return true;
        return switch (job.state) {
            case READY -> true;
            case RETRY_WAIT -> !job.nextEligibleAt.isAfter(query.eligibleAt());
            case CLAIMED -> !job.claim.deadline().isAfter(query.eligibleAt());
            default -> false;
        };
    }

    private static int readCount(DataInputStream in, String name) throws IOException {
        int count = in.readInt(); if (count < 0 || count > 1_000_000) throw new IOException("invalid " + name + " count"); return count;
    }

    private static final class MutableRepairJob {
        private final RepairSpecification specification;
        private RepairState state;
        private long attemptNumber;
        private long claimGeneration;
        private RepairClaim claim;
        private java.time.Instant nextEligibleAt;
        private String reason;
        private final java.time.Instant createdAt;
        private java.time.Instant updatedAt;
        private final List<RepairHistoryEntry> history;

        private MutableRepairJob(RepairSpecification specification, java.time.Instant createdAt) {
            this.specification = specification; this.state = RepairState.READY; this.reason = "exact current obligation";
            this.createdAt = createdAt; this.updatedAt = createdAt; this.history = new ArrayList<>();
        }

        private MutableRepairJob(RepairJob restored) {
            this.specification = restored.specification(); this.state = restored.state(); this.attemptNumber = restored.attemptNumber();
            this.claimGeneration = restored.claimGeneration(); this.claim = restored.claim(); this.nextEligibleAt = restored.nextEligibleAt();
            this.reason = restored.reason(); this.createdAt = restored.createdAt(); this.updatedAt = restored.updatedAt();
            this.history = new ArrayList<>(restored.history());
        }
    }

    private static String error(ControlPlaneException.Code code, String message) { return "ERR\t" + code + "\t" + message; }
}
