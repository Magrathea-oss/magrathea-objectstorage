package com.example.magrathea.cluster.control.ratis;

import com.example.magrathea.storageengine.cluster.application.*;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.retry.RetryPolicies;
import org.apache.ratis.util.TimeDuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Leader-mediated Ratis adapter. Every blocking client operation is isolated on boundedElastic. */
public final class RatisControlPlaneAdapter implements ClusterControlPlanePort {
    private final RaftGroup group;
    private final RaftProperties properties;
    private final Parameters parameters;

    RatisControlPlaneAdapter(RaftGroup group, RaftProperties properties, Parameters parameters) {
        this.group = group; this.properties = properties; this.parameters = parameters;
    }

    @Override public Mono<MembershipSnapshot> membership() {
        return query(ControlPlaneCodec.queryMembership()).map(this::membershipResult);
    }
    @Override public Mono<BucketNamespace> createBucket(String bucket) {
        return write(ControlPlaneCodec.createBucket(bucket)).map(this::bucketResult);
    }
    @Override public Mono<BucketNamespace> bucket(String bucket) {
        return query(ControlPlaneCodec.queryBucket(bucket)).map(this::bucketResult);
    }
    @Override public Flux<BucketNamespace> buckets() {
        return query(ControlPlaneCodec.queryBuckets()).flatMapMany(result ->
                Flux.fromIterable(ControlPlaneCodec.decodeBuckets(success(result, "L"))));
    }
    @Override public Mono<ObjectReferenceGeneration> compareAndPublish(PublicationProposal proposal) {
        return Mono.defer(() -> write(ControlPlaneCodec.publish(proposal)).map(this::referenceResult));
    }
    @Override public Mono<ObjectReferenceGeneration> objectReference(String bucket, String objectKey) {
        return query(ControlPlaneCodec.queryReference(bucket, objectKey)).map(this::referenceResult);
    }
    @Override public Mono<ReferencePage> currentReferences(ReferencePageQuery pageQuery) {
        return query(ControlPlaneCodec.queryReferences(pageQuery)).map(result ->
                ControlPlaneCodec.decodeReferencePage(success(result, "P")));
    }
    @Override public Mono<RepairCommandResult> ensureRepair(RepairCommands.Ensure command) { return repair(command); }
    @Override public Mono<RepairCommandResult> claimRepair(RepairCommands.Claim command) { return repair(command); }
    @Override public Mono<RepairCommandResult> renewRepair(RepairCommands.Renew command) { return repair(command); }
    @Override public Mono<RepairCommandResult> retryRepair(RepairCommands.Retry command) { return repair(command); }
    @Override public Mono<RepairCommandResult> blockRepair(RepairCommands.Block command) { return repair(command); }
    @Override public Mono<RepairCommandResult> succeedRepair(RepairCommands.Succeed command) { return repair(command); }
    @Override public Mono<RepairCommandResult> obsoleteRepair(RepairCommands.Obsolete command) { return repair(command); }
    @Override public Mono<RepairCommandResult> reevaluateRepair(RepairCommands.Reevaluate command) { return repair(command); }
    @Override public Mono<RepairJob> repairJob(RepairJobId jobId) {
        return query(ControlPlaneCodec.queryRepair(jobId)).map(result -> ControlPlaneCodec.decodeRepairJob(success(result, "W")));
    }
    @Override public Flux<RepairJob> repairJobs(RepairJobQuery filter) {
        return query(ControlPlaneCodec.queryRepairs(filter)).flatMapMany(result ->
                Flux.fromIterable(ControlPlaneCodec.decodeRepairJobs(success(result, "X"))));
    }

    private Mono<RepairCommandResult> repair(RepairCommands.Command command) {
        return write(ControlPlaneCodec.repair(command)).map(result -> ControlPlaneCodec.decodeRepairResult(success(result, "J")));
    }
    private Mono<String> write(byte[] command) { return invoke(command, false); }
    private Mono<String> query(byte[] command) { return invoke(command, true); }

    private Mono<String> invoke(byte[] command, boolean readOnly) {
        return Mono.fromCallable(() -> {
            try (RaftClient client = RaftClient.newBuilder().setRaftGroup(group).setProperties(properties)
                    .setParameters(parameters).setRetryPolicy(RetryPolicies.retryUpToMaximumCountWithFixedSleep(30, TimeDuration.valueOf(100, TimeUnit.MILLISECONDS))).build()) {
                RaftClientReply reply = readOnly
                        ? client.io().sendReadOnly(Message.valueOf(org.apache.ratis.thirdparty.com.google.protobuf.ByteString.copyFrom(command)))
                        : client.io().send(Message.valueOf(org.apache.ratis.thirdparty.com.google.protobuf.ByteString.copyFrom(command)));
                if (!reply.isSuccess()) throw new IOException(String.valueOf(reply.getException()));
                return reply.getMessage().getContent().toStringUtf8();
            } catch (IOException | RuntimeException failure) {
                if (failure instanceof ControlPlaneException exception) throw exception;
                throw new ControlPlaneException(ControlPlaneException.Code.QUORUM_UNAVAILABLE,
                        "Ratis leader/quorum operation failed", failure);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private BucketNamespace bucketResult(String result) { String payload = success(result, "B"); return ControlPlaneCodec.decodeBucket(payload); }
    private ObjectReferenceGeneration referenceResult(String result) { return ControlPlaneCodec.decodeReference(success(result, "R")); }
    private MembershipSnapshot membershipResult(String result) {
        String payload = success(result, "M"); String[] fields = payload.split("\\t", 3);
        List<ClusterMember> voters = Arrays.stream(fields[2].split(";")).map(member -> {
            String[] f = member.split(",", -1);
            if (f.length == 4) {
                return new ClusterMember(f[0], NodeIdentity.parse(f[1]), f[2], Integer.parseInt(f[3]));
            }
            if (f.length != 7) {
                throw new ControlPlaneException(ControlPlaneException.Code.INTERNAL_FAILURE,
                        "invalid committed member declaration");
            }
            return new ClusterMember(f[0], NodeIdentity.parse(f[1]), f[2], Integer.parseInt(f[3]),
                    f[4], Integer.parseInt(f[5]), f[6]);
        }).toList();
        return new MembershipSnapshot(voters, fields[0], fields[1]);
    }
    private static String success(String result, String expectedType) {
        String[] fields = result.split("\\t", 3);
        if (fields[0].equals("ERR")) throw new ControlPlaneException(ControlPlaneException.Code.valueOf(fields[1]), fields.length > 2 ? fields[2] : fields[1]);
        if (fields.length < 3 || !fields[0].equals("OK") || !fields[1].equals(expectedType)) throw new ControlPlaneException(ControlPlaneException.Code.INTERNAL_FAILURE, "unexpected Ratis response");
        return fields[2];
    }
}
