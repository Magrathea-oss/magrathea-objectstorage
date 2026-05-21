package com.example.magrathea.objectstorage.application.service;

import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.repository.S3ObjectRepository;
import com.example.magrathea.objectstorage.domain.repository.BucketRepository;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstorage.application.dto.PutObjectCommand;
import com.example.magrathea.objectstorage.application.dto.ObjectResponse;
import java.util.Optional;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Application service for S3 object operations.
 * Repository returns CompletableFuture — service handles async.
 */
@Service
public class ObjectService {

    private final S3ObjectRepository s3ObjectRepository;
    private final BucketRepository bucketRepository;
    private final ContentStore contentStore;

    public ObjectService(S3ObjectRepository s3ObjectRepository,
                         BucketRepository bucketRepository,
                         ContentStore contentStore) {
        this.s3ObjectRepository = s3ObjectRepository;
        this.bucketRepository = bucketRepository;
        this.contentStore = contentStore;
    }

    public ObjectResponse putObject(PutObjectCommand command) {
        var bucketId = Bucket.Id.of(command.bucketId());
        var bucket = bucketRepository.findById(bucketId)
            .join()
            .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + command.bucketId()));

        var id = S3Object.Id.generate();
        var key = ObjectKey.of(command.key());
        var object = S3Object.create(id, bucketId, key,
            command.contentType(), command.contentDisposition(),
            command.contentEncoding(), command.size(),
            command.metadata() != null ? command.metadata() : Map.of());
        s3ObjectRepository.save(object).join();

        return toResponse(object);
    }

    public ObjectResponse findById(String id) {
        var object = s3ObjectRepository.findById(S3Object.Id.of(id))
            .join()
            .orElseThrow(() -> new IllegalArgumentException("Object not found: " + id));
        return toResponse(object);
    }

    public List<ObjectResponse> findByBucket(String bucketId) {
        return s3ObjectRepository.findByBucket(Bucket.Id.of(bucketId))
            .join()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public void deleteObject(String id) {
        s3ObjectRepository.delete(S3Object.Id.of(id)).join();
    }

    public void storeContent(String objectId, byte[] data) {
        contentStore.storeContent(objectId, data);
    }

    public CompletableFuture<Optional<byte[]>> getContent(String objectId) {
        return contentStore.getContent(objectId);
    }

    private ObjectResponse toResponse(S3Object object) {
        return new ObjectResponse(
            object.id().value(),
            object.bucketId().value(),
            object.key().value(),
            object.size(),
            object.hasContentType() ? object.contentType() : "application/octet-stream",
            object.hasEtag() ? object.etag() : null,
            object.hasStorageClass() ? object.storageClass() : null
        );
    }
}
