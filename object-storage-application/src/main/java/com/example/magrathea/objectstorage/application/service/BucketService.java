package com.example.magrathea.objectstorage.application.service;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.repository.BucketRepository;
import com.example.magrathea.objectstorage.domain.valueobject.Region;
import com.example.magrathea.objectstorage.domain.valueobject.StorageClass;
import com.example.magrathea.objectstorage.application.dto.CreateBucketCommand;
import com.example.magrathea.objectstorage.application.dto.BucketResponse;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Application service for bucket operations.
 * CAN use Spring annotations (@Service).
 * Orchestrates domain logic and repository calls.
 * Repository returns CompletableFuture — service handles async.
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

        return new BucketResponse(
            bucket.id().value(),
            bucket.name(),
            bucket.region().name(),
            bucket.storageClass().name(),
            bucket.versioningEnabled(),
            bucket.encryptionEnabled()
        );
    }

    public BucketResponse findById(String id) {
        var bucket = repository.findById(Bucket.Id.of(id))
            .join()
            .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + id));
        return new BucketResponse(
            bucket.id().value(),
            bucket.name(),
            bucket.region().name(),
            bucket.storageClass().name(),
            bucket.versioningEnabled(),
            bucket.encryptionEnabled()
        );
    }

    public List<BucketResponse> findAll() {
        return repository.findAll().join().stream()
            .map(b -> new BucketResponse(
                b.id().value(), b.name(),
                b.region().name(), b.storageClass().name(),
                b.versioningEnabled(), b.encryptionEnabled()))
            .toList();
    }

    public void deleteBucket(String id) {
        repository.delete(Bucket.Id.of(id)).join();
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
