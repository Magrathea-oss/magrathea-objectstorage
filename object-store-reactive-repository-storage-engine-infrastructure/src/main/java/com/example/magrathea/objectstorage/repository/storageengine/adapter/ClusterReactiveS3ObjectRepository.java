package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.LegalHold;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.RestoreConfiguration;
import com.example.magrathea.objectstore.reactive.repository.application.BucketNotFoundException;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.S3ObjectQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.StorageObjectIntegrityException;
import com.example.magrathea.storageengine.cluster.application.ClusterControlPlanePort;
import com.example.magrathea.storageengine.cluster.application.ClusterMember;
import com.example.magrathea.storageengine.cluster.application.ClusterObjectMetadata;
import com.example.magrathea.storageengine.cluster.application.ClusterWriteCoordinator;
import com.example.magrathea.storageengine.cluster.application.ControlPlaneException;
import com.example.magrathea.storageengine.cluster.application.LocalArtifactPort;
import com.example.magrathea.storageengine.cluster.application.NodeIdentity;
import com.example.magrathea.storageengine.cluster.application.ObjectReferenceGeneration;
import com.example.magrathea.storageengine.cluster.application.PreparedArtifact;
import com.example.magrathea.storageengine.cluster.application.ReplicaReadPort;
import com.example.magrathea.storageengine.cluster.application.TransferRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Cluster-profile repository limited to unconditional whole-object PUT and GET. */
@Repository
@Profile("storage-engine & cluster")
public final class ClusterReactiveS3ObjectRepository
        implements S3ObjectCommandRepository, S3ObjectQueryRepository {
    private static final int FRAME_BYTES = 65_536;
    private static final DefaultDataBufferFactory BUFFERS = new DefaultDataBufferFactory();

    private final NodeIdentity localNode;
    private final ClusterControlPlanePort controlPlane;
    private final LocalArtifactPort localArtifacts;
    private final ClusterWriteCoordinator writeCoordinator;
    private final ReplicaReadPort replicaReads;
    private final Duration readDeadline;
    private final AtomicLong versions = new AtomicLong(1);

    public ClusterReactiveS3ObjectRepository(
            NodeIdentity localNode,
            ClusterControlPlanePort controlPlane,
            LocalArtifactPort localArtifacts,
            ClusterWriteCoordinator writeCoordinator,
            ReplicaReadPort replicaReads,
            @Qualifier("clusterReadDeadline") Duration clusterReadDeadline) {
        this.localNode = localNode;
        this.controlPlane = controlPlane;
        this.localArtifacts = localArtifacts;
        this.writeCoordinator = writeCoordinator;
        this.replicaReads = replicaReads;
        this.readDeadline = clusterReadDeadline;
    }

    @Override
    public Mono<CommandResult<S3Object>> save(S3Object object) {
        return controlPlane.objectReference(object.key().bucket(), object.key().key())
                .flatMap(reference -> {
                    String requested = normalizeEtag(object.etag());
                    ClusterObjectMetadata committedMetadata = reference.metadata();
                    boolean sameMetadata = object.size() == reference.length()
                            && (requested.isEmpty() || requested.equals(committedMetadata.etag()))
                            && effectiveStorageClass(object.storageClass(), null)
                                    .equals(committedMetadata.storageClass())
                            && object.userMetadata().equals(committedMetadata.userMetadata())
                            && object.objectTags().equals(committedMetadata.objectTags());
                    if (!sameMetadata) {
                        return Mono.error(new UnsupportedOperationException(
                                "cluster metadata/config update is outside the EP-10 whole-object slice"));
                    }
                    S3Object committed = restore(reference);
                    return Mono.just((CommandResult<S3Object>) new CommandResult.Updated<>(
                            committed, object.domainEvents(), versions.getAndIncrement()));
                })
                .onErrorResume(ClusterReactiveS3ObjectRepository::notFound,
                        ignored -> unsupported("metadata-only object save"));
    }

    @Override
    public Mono<CommandResult<S3Object>> saveWithContent(
            S3Object object, Flux<DataBuffer> content, String storageClass) {
        ObjectKey key = object.key();
        return requireBucket(key.bucket())
                .then(stageIncoming(object, content, storageClass))
                .flatMap(prepared -> writeCoordinator.publish(key.bucket(), key.key(), prepared))
                .map(reference -> {
                    S3Object committed = restore(reference);
                    CommandResult<S3Object> result = reference.generation() == 1
                            ? new CommandResult.Created<>(committed, object.domainEvents(), versions.getAndIncrement())
                            : new CommandResult.Updated<>(committed, object.domainEvents(), versions.getAndIncrement());
                    return result;
                });
    }

    @Override
    public Mono<CommandResult<S3Object>> saveWithContent(
            ObjectKey objectKey, Flux<DataBuffer> content, String storageClass) {
        ActiveS3Object pending = ActiveS3Object.createPending(
                objectKey, effectiveStorageClass(storageClass, null), Map.of(), null);
        return saveWithContent(pending, content, storageClass);
    }

    @Override
    public Mono<CommandResult<S3Object>> delete(S3Object object) {
        return unsupported("cluster object delete");
    }

    @Override
    public Mono<S3Object> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key) {
        return findByBucketAndKey(key);
    }

    @Override
    public Mono<S3Object> findByBucketAndKey(ObjectKey key) {
        return controlPlane.objectReference(key.bucket(), key.key())
                .map(ClusterReactiveS3ObjectRepository::restore)
                .onErrorResume(ClusterReactiveS3ObjectRepository::notFound, ignored -> Mono.empty());
    }

    @Override
    public Flux<S3Object> findByBucket(String bucketName) {
        return Flux.error(unsupportedFailure("cluster object listing"));
    }

    @Override
    public Flux<DataBuffer> getContent(ObjectKey key) {
        return controlPlane.objectReference(key.bucket(), key.key())
                .flatMap(reference -> ensureLocal(reference).thenReturn(reference))
                .flatMapMany(this::verifiedLocalStream);
    }

    @Override
    public Mono<Void> validateContentIntegrity(ObjectKey key) {
        return getContent(key).doOnNext(DataBufferUtils::release).then();
    }

    @Override
    public Mono<LegalHold> findLegalHold(String bucketName, ObjectKey key) {
        return unsupported("cluster legal hold");
    }

    @Override
    public Mono<ObjectLockConfiguration> findObjectLockConfiguration(String bucketName, ObjectKey key) {
        return unsupported("cluster object lock configuration");
    }

    @Override
    public Mono<ObjectLockConfiguration.RetentionPeriod> findRetention(String bucketName, ObjectKey key) {
        return unsupported("cluster retention");
    }

    @Override
    public Mono<EncryptionConfiguration> findEncryption(String bucketName, ObjectKey key) {
        return unsupported("cluster object encryption configuration");
    }

    @Override
    public Mono<RestoreConfiguration> findRestore(String bucketName, ObjectKey key) {
        return unsupported("cluster restore");
    }

    @Override
    public Flux<DataBuffer> findTorrent(String bucketName, ObjectKey key) {
        return Flux.error(unsupportedFailure("cluster torrent"));
    }

    @Override
    public Mono<Void> saveLegalHold(String bucketName, ObjectKey key, LegalHold hold) {
        return unsupported("cluster legal hold");
    }

    @Override
    public Mono<Void> saveObjectLockConfiguration(
            String bucketName, ObjectKey key, ObjectLockConfiguration config) {
        return unsupported("cluster object lock configuration");
    }

    @Override
    public Mono<Void> saveRetention(
            String bucketName, ObjectKey key, ObjectLockConfiguration.RetentionPeriod retention) {
        return unsupported("cluster retention");
    }

    @Override
    public Mono<Void> saveRestore(String bucketName, ObjectKey key, RestoreConfiguration config) {
        return unsupported("cluster restore");
    }

    @Override
    public Mono<Void> saveEncryption(
            String bucketName, ObjectKey key, EncryptionConfiguration encryption) {
        return unsupported("cluster object encryption configuration");
    }

    @Override
    public Mono<Void> renameObject(String bucketName, ObjectKey oldKey, ObjectKey newKey) {
        return unsupported("cluster object rename");
    }

    private Mono<Void> requireBucket(String bucket) {
        return controlPlane.bucket(bucket)
                .then()
                .onErrorMap(ClusterReactiveS3ObjectRepository::notFound,
                        ignored -> new BucketNotFoundException(bucket));
    }

    private Mono<PreparedArtifact> stageIncoming(
            S3Object object, Flux<DataBuffer> content, String storageClass) {
        String operationId = "put-" + UUID.randomUUID();
        String artifactId = "whole-" + UUID.randomUUID();
        return Mono.usingWhen(
                Mono.fromCallable(() -> localArtifacts.beginIncoming(operationId, artifactId))
                        .subscribeOn(Schedulers.boundedElastic()),
                sink -> content.concatMap(buffer -> writeBuffer(sink, buffer), 1)
                        .then(Mono.fromCallable(sink::publish)
                                .subscribeOn(Schedulers.boundedElastic())),
                sink -> closeIncoming(sink, false),
                (sink, error) -> closeIncoming(sink, true),
                sink -> closeIncoming(sink, true))
                .map(incoming -> {
                    ClusterObjectMetadata metadata = new ClusterObjectMetadata(
                            effectiveStorageClass(storageClass, object.storageClass()),
                            object.userMetadata(),
                            object.objectTags(),
                            incoming.md5(),
                            object.createdAt().toInstant());
                    PreparedArtifact artifact = incoming.artifact();
                    return new PreparedArtifact(
                            artifact.operationId(), artifact.artifactId(), artifact.localNode(),
                            artifact.length(), artifact.sha256(), metadata);
                });
    }

    private static Mono<Void> writeBuffer(
            LocalArtifactPort.IncomingSink sink, DataBuffer dataBuffer) {
        return Mono.fromRunnable(() -> {
                    try (DataBuffer.ByteBufferIterator iterator = dataBuffer.readableByteBuffers()) {
                        while (iterator.hasNext()) {
                            ByteBuffer source = iterator.next();
                            while (source.hasRemaining()) {
                                int count = Math.min(FRAME_BYTES, source.remaining());
                                ByteBuffer frame = source.slice();
                                frame.limit(count);
                                sink.accept(frame);
                                source.position(source.position() + count);
                            }
                        }
                    } catch (IOException failure) {
                        throw new RuntimeException(failure);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Void> ensureLocal(ObjectReferenceGeneration reference) {
        return Mono.fromCallable(() -> {
                    try (LocalArtifactPort.Source ignored =
                                 localArtifacts.openPublished(reference.artifactId())) {
                        return true;
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(IOException.class, ignored -> fetchOneReplica(reference));
    }

    private Mono<Void> fetchOneReplica(ObjectReferenceGeneration reference) {
        return controlPlane.membership().flatMap(snapshot -> Flux.fromIterable(reference.replicas())
                .filter(replica -> !replica.equals(localNode))
                .concatMap(replica -> fetchReplica(snapshot.member(replica), reference)
                        .onErrorResume(ignored -> Mono.empty()), 1)
                .next()
                .switchIfEmpty(Mono.error(new IOException(
                        "no committed replica could provide artifact " + reference.artifactId())))
                .then());
    }

    private Mono<?> fetchReplica(ClusterMember source, ObjectReferenceGeneration reference) {
        TransferRequest request = new TransferRequest(
                "read-" + UUID.randomUUID(),
                reference.artifactId(),
                localNode,
                reference.length(),
                HexFormat.of().parseHex(reference.sha256()),
                reference.topologyEpoch(),
                reference.policyEpoch(),
                readDeadline);
        return Mono.usingWhen(
                Mono.fromCallable(() -> localArtifacts.beginUnpublished(request))
                        .subscribeOn(Schedulers.boundedElastic()),
                sink -> Mono.fromCompletionStage(replicaReads.fetch(source, request, sink)),
                sink -> closeSink(sink, false),
                (sink, error) -> closeSink(sink, true),
                sink -> closeSink(sink, true));
    }

    private Flux<DataBuffer> verifiedLocalStream(ObjectReferenceGeneration reference) {
        return Flux.using(
                        () -> new VerifiedReader(
                                localArtifacts.openPublished(reference.artifactId()), reference),
                        reader -> Flux.<DataBuffer>generate(sink -> reader.read(sink)),
                        VerifiedReader::close)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static S3Object restore(ObjectReferenceGeneration reference) {
        byte[] sha = HexFormat.of().parseHex(reference.sha256());
        ObjectChecksum checksum = ObjectChecksum.of(Set.of(new ChecksumValue(
                ChecksumAlgorithm.SHA256, Base64.getEncoder().encodeToString(sha))));
        ClusterObjectMetadata metadata = reference.metadata();
        ZonedDateTime createdAt = ZonedDateTime.ofInstant(metadata.createdAt(), ZoneOffset.UTC);
        return ActiveS3Object.restoreActive(
                        ObjectKey.of(reference.bucket(), reference.objectKey()),
                        metadata.storageClass(),
                        metadata.userMetadata(),
                        null,
                        checksum,
                        reference.length(),
                        createdAt,
                        List.of())
                .withEtag(metadata.etag().isEmpty() ? null : '"' + metadata.etag() + '"')
                .withObjectTags(metadata.objectTags());
    }

    private static Mono<Void> closeIncoming(
            LocalArtifactPort.IncomingSink sink, boolean abort) {
        return Mono.fromRunnable(() -> {
                    if (abort) sink.abort();
                    try {
                        sink.close();
                    } catch (IOException ignored) {
                        // The primary terminal signal owns the outcome.
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private static Mono<Void> closeSink(LocalArtifactPort.Sink sink, boolean abort) {
        return Mono.fromRunnable(() -> {
                    if (abort) sink.abort();
                    try {
                        sink.close();
                    } catch (IOException ignored) {
                        // The primary terminal signal owns the outcome.
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private static String effectiveStorageClass(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) return primary.trim();
        if (fallback != null && !fallback.isBlank()) return fallback.trim();
        return "STANDARD";
    }

    private static String normalizeEtag(String etag) {
        return etag == null ? "" : etag.replace("\"", "");
    }

    private static boolean notFound(Throwable failure) {
        return failure instanceof ControlPlaneException exception
                && exception.code() == ControlPlaneException.Code.NOT_FOUND;
    }

    private static <T> Mono<T> unsupported(String capability) {
        return Mono.error(unsupportedFailure(capability));
    }

    private static UnsupportedOperationException unsupportedFailure(String capability) {
        return new UnsupportedOperationException(
                capability + " is not implemented by the EP-10 cluster profile");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static final class VerifiedReader implements AutoCloseable {
        private final LocalArtifactPort.Source source;
        private final ObjectReferenceGeneration reference;
        private final MessageDigest digest = sha256();
        private long length;
        private boolean terminal;

        private VerifiedReader(
                LocalArtifactPort.Source source, ObjectReferenceGeneration reference) {
            this.source = source;
            this.reference = reference;
        }

        private void read(reactor.core.publisher.SynchronousSink<DataBuffer> sink) {
            if (terminal) {
                sink.complete();
                return;
            }
            try {
                ByteBuffer target = ByteBuffer.allocate(FRAME_BYTES);
                int count = source.read(target);
                if (count < 0) {
                    terminal = true;
                    byte[] expected = HexFormat.of().parseHex(reference.sha256());
                    if (length != reference.length()
                            || !MessageDigest.isEqual(digest.digest(), expected)) {
                        sink.error(new StorageObjectIntegrityException(
                                "cluster artifact length or SHA-256 differs from committed reference"));
                    } else {
                        sink.complete();
                    }
                    return;
                }
                if (count == 0) return;
                target.flip();
                byte[] bytes = new byte[count];
                target.get(bytes);
                digest.update(bytes);
                length += count;
                sink.next(BUFFERS.wrap(bytes));
            } catch (IOException failure) {
                terminal = true;
                sink.error(new StorageObjectIntegrityException(
                        "cannot stream committed cluster artifact", failure));
            }
        }

        @Override
        public void close() {
            try {
                source.close();
            } catch (IOException ignored) {
                // Stream termination already owns the observable signal.
            }
        }
    }
}
