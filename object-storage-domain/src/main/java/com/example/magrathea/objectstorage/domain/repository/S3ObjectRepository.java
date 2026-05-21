package com.example.magrathea.objectstorage.domain.repository;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.aggregate.S3ObjectContent;
import com.example.magrathea.objectstorage.domain.aggregate.S3ObjectWrite;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for S3Object aggregate.
 * Defined in domain — NO implementation here.
 * Uses CompletableFuture for async (Java SE standard, no framework).
 * Implementations persist S3 object metadata and content without exposing framework types to the domain.
 */
public interface S3ObjectRepository {
    CompletableFuture<Optional<S3Object>> findById(S3Object.Id id);
    CompletableFuture<Optional<S3Object>> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key);
    CompletableFuture<List<S3Object>> findByBucket(Bucket.Id bucketId);
    CompletableFuture<Void> save(S3ObjectWrite object);
    CompletableFuture<Optional<S3ObjectContent>> getContent(S3Object.Id id);
    CompletableFuture<Void> delete(S3Object.Id id);
}
