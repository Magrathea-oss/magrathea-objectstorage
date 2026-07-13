package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.Region;
import com.example.magrathea.objectstore.domain.valueobject.StorageClass;
import com.example.magrathea.objectstore.reactive.repository.application.BucketCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.BucketQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.storageengine.cluster.application.BucketNamespace;
import com.example.magrathea.storageengine.cluster.application.ClusterControlPlanePort;
import com.example.magrathea.storageengine.cluster.application.ControlPlaneException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Consensus-backed bucket adapter for the bounded EP-10 cluster profile. */
@Repository
@Profile("storage-engine & cluster")
public final class ClusterReactiveBucketRepository
        implements BucketCommandRepository, BucketQueryRepository {
    private final ClusterControlPlanePort controlPlane;
    private final AtomicLong versions = new AtomicLong(1);

    public ClusterReactiveBucketRepository(ClusterControlPlanePort controlPlane) {
        this.controlPlane = controlPlane;
    }

    @Override
    public Mono<CommandResult<Bucket>> save(Bucket bucket) {
        Mono<Boolean> existed = controlPlane.bucket(bucket.name())
                .map(ignored -> true)
                .onErrorResume(ClusterReactiveBucketRepository::notFound, ignored -> Mono.just(false));
        return existed.flatMap(previous -> controlPlane.createBucket(bucket.name())
                .map(namespace -> {
                    Bucket committed = restore(namespace);
                    long version = versions.getAndIncrement();
                    return previous
                            ? new CommandResult.Updated<Bucket>(committed, bucket.domainEvents(), version)
                            : new CommandResult.Created<Bucket>(committed, bucket.domainEvents(), version);
                }));
    }

    @Override
    public Mono<CommandResult<Bucket>> delete(Bucket bucket) {
        return unsupported("cluster bucket delete");
    }

    @Override
    public Mono<Bucket> findById(Bucket.Id bucketId) {
        return findAll().filter(bucket -> bucket.id().equals(bucketId)).next();
    }

    @Override
    public Mono<Bucket> findByName(String bucketName) {
        return controlPlane.bucket(bucketName)
                .map(ClusterReactiveBucketRepository::restore)
                .onErrorResume(ClusterReactiveBucketRepository::notFound, ignored -> Mono.empty());
    }

    @Override
    public Flux<Bucket> findAll() {
        return controlPlane.buckets().map(ClusterReactiveBucketRepository::restore);
    }

    @Override
    public Mono<AbacConfiguration> findAbacConfiguration(String bucketName) {
        return unsupported("cluster bucket ABAC configuration");
    }

    @Override
    public Mono<BucketMetadataConfiguration> findMetadataConfiguration(String bucketName) {
        return unsupported("cluster bucket metadata configuration");
    }

    @Override
    public Mono<BucketMetadataTableConfiguration> findMetadataTableConfiguration(String bucketName) {
        return unsupported("cluster bucket metadata-table configuration");
    }

    @Override
    public Mono<Void> saveAbacConfiguration(String bucketName, AbacConfiguration config) {
        return unsupported("cluster bucket ABAC configuration");
    }

    @Override
    public Mono<Void> saveMetadataConfiguration(String bucketName, BucketMetadataConfiguration config) {
        return unsupported("cluster bucket metadata configuration");
    }

    @Override
    public Mono<Void> saveMetadataTableConfiguration(
            String bucketName, BucketMetadataTableConfiguration config) {
        return unsupported("cluster bucket metadata-table configuration");
    }

    private static Bucket restore(BucketNamespace namespace) {
        UUID deterministicId = UUID.nameUUIDFromBytes(
                ("cluster-bucket:" + namespace.bucket()).getBytes(StandardCharsets.UTF_8));
        return Bucket.restore(Bucket.Id.of(deterministicId.toString()), namespace.bucket(),
                Region.US_EAST_1, StorageClass.STANDARD, false, false, false, BucketConfig.EMPTY);
    }

    private static boolean notFound(Throwable failure) {
        return failure instanceof ControlPlaneException exception
                && exception.code() == ControlPlaneException.Code.NOT_FOUND;
    }

    private static <T> Mono<T> unsupported(String capability) {
        return Mono.error(new UnsupportedOperationException(
                capability + " is not implemented by the EP-10 cluster profile"));
    }
}
