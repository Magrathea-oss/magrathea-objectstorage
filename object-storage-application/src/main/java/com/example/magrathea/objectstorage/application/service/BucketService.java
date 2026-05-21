package com.example.magrathea.objectstorage.application.service;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.repository.BucketRepository;
import com.example.magrathea.objectstorage.domain.valueobject.BucketConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.Region;
import com.example.magrathea.objectstorage.domain.valueobject.StorageClass;
import com.example.magrathea.objectstorage.application.dto.CreateBucketCommand;
import com.example.magrathea.objectstorage.application.dto.BucketResponse;
import com.example.magrathea.objectstorage.application.dto.CorsConfigurationCommand;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Application service for bucket operations.
 * CAN use Spring annotations (@Service).
 * Orchestrates domain logic and repository calls.
 * Repository returns CompletableFuture — service bridges to reactive Mono.
 */
@Service
public class BucketService {

    private final BucketRepository repository;

    public BucketService(BucketRepository repository) {
        this.repository = repository;
    }

    public BucketResponse createBucket(CreateBucketCommand command) {
        var id = Bucket.Id.generate();
        var region = findRegion(command.region());
        var storageClass = findStorageClass(command.storageClass());

        var bucket = Bucket.create(id, command.name(), region, storageClass);
        repository.save(bucket).join();

        return toResponse(bucket);
    }

    public BucketResponse findById(String id) {
        var bucket = repository.findById(Bucket.Id.of(id))
            .join()
            .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + id));
        return toResponse(bucket);
    }

    public Mono<List<BucketResponse>> findAllReactive() {
        return Mono.fromFuture(repository.findAll())
            .map(buckets -> buckets.stream().map(this::toResponse).toList());
    }

    public List<BucketResponse> findAll() {
        return repository.findAll().join().stream()
            .map(this::toResponse)
            .toList();
    }

    public BucketResponse putBucketVersioning(String bucketName, boolean enabled) {
        var bucket = repository.findByName(bucketName)
            .join()
            .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketName));
        var updated = enabled ? bucket.withVersioningEnabled() : bucket.withVersioningSuspended();
        repository.save(updated).join();
        return toResponse(updated);
    }

    public void deleteBucket(String id) {
        repository.delete(Bucket.Id.of(id)).join();
    }

    // ── CORS ──

    public Optional<BucketConfiguration> getCorsConfiguration(String bucketName) {
        return repository.findConfiguration(bucketName).join();
    }

    public void putCorsConfiguration(CorsConfigurationCommand command) {
        var rules = command.corsRules().stream()
            .map(r -> new BucketConfiguration.CorsRule(
                r.allowedOrigins(), r.allowedMethods(), r.allowedHeaders(),
                r.maxAgeSeconds(), r.exposeHeaders(), r.id()))
            .toList();
        var config = new BucketConfiguration(command.bucketName(), rules);
        repository.saveConfiguration(config).join();
    }

    public void deleteCorsConfiguration(String bucketName) {
        repository.deleteConfiguration(bucketName).join();
    }

    // ── internal ──

    private BucketResponse toResponse(Bucket bucket) {
        return new BucketResponse(
            bucket.id().value(),
            bucket.name(),
            bucket.region().id(),
            bucket.storageClass().name(),
            bucket.versioningEnabled(),
            bucket.encryptionEnabled()
        );
    }

    private Region findRegion(String regionId) {
        switch (regionId) {
            case "eu-west-1": return Region.EU_WEST_1;
            case "eu-central-1": return Region.EU_CENTRAL_1;
            case "us-east-1": return Region.US_EAST_1;
            case "us-west-2": return Region.US_WEST_2;
            default: throw new IllegalArgumentException("Unknown region: " + regionId);
        }
    }

    private StorageClass findStorageClass(String name) {
        switch (name) {
            case "STANDARD": return StorageClass.STANDARD;
            case "STANDARD_IA": return StorageClass.STANDARD_IA;
            case "GLACIER": return StorageClass.GLACIER;
            case "INTELLIGENT_TIERING": return StorageClass.INTELLIGENT_TIERING;
            default: throw new IllegalArgumentException("Unknown storage class: " + name);
        }
    }
}
