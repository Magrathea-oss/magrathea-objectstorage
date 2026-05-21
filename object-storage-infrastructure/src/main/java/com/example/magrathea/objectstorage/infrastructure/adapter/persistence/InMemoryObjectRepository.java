package com.example.magrathea.objectstorage.infrastructure.adapter.persistence;

import com.example.magrathea.objectstorage.application.service.DefaultS3ObjectContent;
import com.example.magrathea.objectstorage.application.service.DefaultS3ObjectWrite;
import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.aggregate.S3ObjectContent;
import com.example.magrathea.objectstorage.domain.aggregate.S3ObjectWrite;
import com.example.magrathea.objectstorage.domain.repository.S3ObjectRepository;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of S3ObjectRepository.
 * Stores object metadata and raw content in ConcurrentHashMap.
 */
@Repository
public class InMemoryObjectRepository implements S3ObjectRepository {

    private static final DefaultDataBufferFactory DATA_BUFFER_FACTORY = new DefaultDataBufferFactory();

    private final ConcurrentHashMap<String, S3ObjectMetadata> metadataStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> contentStore = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Optional<S3Object>> findById(S3Object.Id id) {
        var meta = metadataStore.get(id.value());
        if (meta == null) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.completedFuture(Optional.of(toS3Object(id, meta)));
    }

    @Override
    public CompletableFuture<Optional<S3Object>> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key) {
        var metadata = metadataStore.values().stream()
            .filter(x -> x.bucketId.equals(bucketId) && x.key.equals(key.value()))
            .findFirst();
        if (metadata.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        var meta = metadata.get();
        return CompletableFuture.completedFuture(Optional.of(toS3Object(S3Object.Id.of(meta.id), meta)));
    }

    @Override
    public CompletableFuture<List<S3Object>> findByBucket(Bucket.Id bucketId) {
        var result = metadataStore.values().stream()
            .filter(metadata -> metadata.bucketId.equals(bucketId))
            .map(metadata -> toS3Object(S3Object.Id.of(metadata.id), metadata))
            .toList();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Void> save(S3ObjectWrite object) {
        return save(cast(object));
    }

    @Override
    public CompletableFuture<Optional<S3ObjectContent>> getContent(S3Object.Id id) {
        return CompletableFuture.completedFuture(
            Optional.ofNullable(contentStore.get(id.value()))
                .map(bytes -> new DefaultS3ObjectContent(id, reactor.core.publisher.Flux.just(DATA_BUFFER_FACTORY.wrap(bytes))))
        );
    }

    @Override
    public CompletableFuture<Void> delete(S3Object.Id id) {
        metadataStore.remove(id.value());
        contentStore.remove(id.value());
        return CompletableFuture.completedFuture(null);
    }

    /** Reset state — used by test cleanup hooks. */
    public void reset() {
        metadataStore.clear();
        contentStore.clear();
    }

    private CompletableFuture<Void> save(DefaultS3ObjectWrite write) {
        var object = write.s3Object();
        return DataBufferUtils.join(write.content())
            .doOnNext(dataBuffer -> {
                try {
                    var bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    metadataStore.put(object.id().value(), new S3ObjectMetadata(
                        object.id().value(),
                        object.bucketId(),
                        object.key().value(),
                        object.etag(),
                        bytes.length,
                        object.lastModified(),
                        object.storageClass(),
                        object.hasContentType() ? object.contentType() : "application/octet-stream",
                        object.metadata()
                    ));
                    contentStore.put(object.id().value(), bytes);
                } finally {
                    DataBufferUtils.release(dataBuffer);
                }
            })
            .then()
            .toFuture();
    }

    private DefaultS3ObjectWrite cast(S3ObjectWrite object) {
        if (object instanceof DefaultS3ObjectWrite write) {
            return write;
        }
        throw new IllegalArgumentException("Unsupported S3ObjectWrite implementation: " + object.getClass().getName());
    }

    private S3Object toS3Object(S3Object.Id id, S3ObjectMetadata meta) {
        return S3Object.restore(
            id, meta.bucketId, ObjectKey.of(meta.key),
            meta.etag, meta.size, meta.storageClass, meta.lastModified,
            meta.contentType, null, null, meta.metadata
        );
    }

    private record S3ObjectMetadata(
        String id,
        Bucket.Id bucketId,
        String key,
        String etag,
        long size,
        Instant lastModified,
        String storageClass,
        String contentType,
        java.util.Map<String, String> metadata
    ) {}
}
