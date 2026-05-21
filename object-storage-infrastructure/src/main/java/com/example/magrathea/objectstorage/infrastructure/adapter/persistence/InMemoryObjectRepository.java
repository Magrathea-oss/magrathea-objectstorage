package com.example.magrathea.objectstorage.infrastructure.adapter.persistence;

import com.example.magrathea.objectstorage.application.service.ContentStore;
import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.repository.S3ObjectRepository;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of S3ObjectRepository.
 * Stores object metadata and raw content in ConcurrentHashMap.
 * Implements ContentStore for direct byte[] access by application layer.
 */
@Repository
public class InMemoryObjectRepository implements S3ObjectRepository, ContentStore {

    private final ConcurrentHashMap<String, S3ObjectMetadata> metadataStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> contentStore = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Optional<S3Object>> findById(S3Object.Id id) {
        var meta = metadataStore.get(id.value());
        if (meta == null) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.completedFuture(Optional.of(S3Object.restore(
            id, meta.bucketId, ObjectKey.of(meta.key),
            meta.etag, meta.size, meta.storageClass, meta.lastModified,
            meta.contentType, null, null, meta.metadata
        )));
    }

    @Override
    public CompletableFuture<Optional<S3Object>> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key) {
        var m = metadataStore.values().stream()
            .filter(x -> x.bucketId.equals(bucketId) && x.key.equals(key.value()))
            .findFirst();
        if (m.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        var meta = m.get();
        var id = S3Object.Id.of(meta.id);
        return CompletableFuture.completedFuture(Optional.of(S3Object.restore(
            id, meta.bucketId, ObjectKey.of(meta.key),
            meta.etag, meta.size, meta.storageClass, meta.lastModified,
            meta.contentType, null, null, meta.metadata
        )));
    }

    @Override
    public CompletableFuture<List<S3Object>> findByBucket(Bucket.Id bucketId) {
        var result = metadataStore.values().stream()
            .filter(m -> m.bucketId.equals(bucketId))
            .map(m -> {
                var id = S3Object.Id.of(m.id);
                return S3Object.restore(
                    id, m.bucketId, ObjectKey.of(m.key),
                    m.etag, m.size, m.storageClass, m.lastModified,
                    m.contentType, null, null, m.metadata
                );
            })
            .toList();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Void> save(S3Object object) {
        metadataStore.put(object.id().value(), new S3ObjectMetadata(
            object.id().value(),
            object.bucketId(),
            object.key().value(),
            object.etag(),
            object.size(),
            object.lastModified(),
            object.storageClass(),
            object.hasContentType() ? object.contentType() : "application/octet-stream",
            object.metadata()
        ));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void storeContent(String objectId, byte[] data) {
        contentStore.put(objectId, data);
    }

    @Override
    public CompletableFuture<Optional<byte[]>> getContent(String objectId) {
        return CompletableFuture.completedFuture(
            Optional.ofNullable(contentStore.get(objectId))
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
