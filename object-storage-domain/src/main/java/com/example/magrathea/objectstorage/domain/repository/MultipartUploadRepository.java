package com.example.magrathea.objectstorage.domain.repository;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstorage.domain.valueobject.UploadId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for MultipartUpload aggregate.
 * Defined in domain — NO implementation here.
 * Uses CompletableFuture for async (Java SE standard, no framework).
 */
public interface MultipartUploadRepository {
    CompletableFuture<Optional<MultipartUpload>> findById(MultipartUpload.Id id);
    CompletableFuture<Optional<MultipartUpload>> findByUploadId(UploadId uploadId);
    CompletableFuture<List<MultipartUpload>> findByBucket(Bucket.Id bucketId);
    CompletableFuture<List<MultipartUpload>> findAll();
    CompletableFuture<Void> save(MultipartUpload upload);
    CompletableFuture<Void> delete(MultipartUpload.Id id);
}
