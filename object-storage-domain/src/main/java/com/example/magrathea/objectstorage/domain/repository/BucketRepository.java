package com.example.magrathea.objectstorage.domain.repository;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.BucketConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for Bucket aggregate.
 * Defined in domain — NO implementation here.
 * Uses CompletableFuture for async operations (Java SE standard, no framework).
 */
public interface BucketRepository {
    CompletableFuture<Optional<Bucket>> findById(Bucket.Id id);
    CompletableFuture<Optional<Bucket>> findByName(String name);
    CompletableFuture<List<Bucket>> findAll();
    CompletableFuture<Void> save(Bucket bucket);
    CompletableFuture<Void> delete(Bucket.Id id);
    CompletableFuture<Optional<BucketConfiguration>> findConfiguration(String bucketName);
    CompletableFuture<Void> saveConfiguration(BucketConfiguration configuration);
    CompletableFuture<Void> deleteConfiguration(String bucketName);
}
