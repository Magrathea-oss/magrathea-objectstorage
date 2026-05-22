package com.example.magrathea.objectstorage.infrastructure.adapter.persistence;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstorage.domain.repository.MultipartUploadRepository;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstorage.domain.valueobject.UploadId;
import com.example.magrathea.objectstorage.domain.valueobject.UploadPart;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of MultipartUploadRepository.
 * Stores multipart upload sessions in ConcurrentHashMap.
 */
@Repository
public class InMemoryMultipartUploadRepository implements MultipartUploadRepository {

    private final ConcurrentHashMap<String, MultipartUpload> store = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Optional<MultipartUpload>> findById(MultipartUpload.Id id) {
        return CompletableFuture.completedFuture(
            Optional.ofNullable(store.get(id.value()))
        );
    }

    @Override
    public CompletableFuture<Optional<MultipartUpload>> findByUploadId(UploadId uploadId) {
        return CompletableFuture.completedFuture(
            store.values().stream()
                .filter(u -> u.uploadId().equals(uploadId))
                .findFirst()
        );
    }

    @Override
    public CompletableFuture<List<MultipartUpload>> findByBucket(Bucket.Id bucketId) {
        var result = store.values().stream()
            .filter(u -> u.bucketId().equals(bucketId))
            .toList();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<MultipartUpload>> findAll() {
        return CompletableFuture.completedFuture(List.copyOf(store.values()));
    }

    @Override
    public CompletableFuture<Void> save(MultipartUpload upload) {
        store.put(upload.id().value(), upload);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> delete(MultipartUpload.Id id) {
        store.remove(id.value());
        return CompletableFuture.completedFuture(null);
    }
}
