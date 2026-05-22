package com.example.magrathea.objectstorage.application.service;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstorage.domain.repository.BucketRepository;
import com.example.magrathea.objectstorage.domain.repository.MultipartUploadRepository;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstorage.domain.valueobject.PartNumber;
import com.example.magrathea.objectstorage.domain.valueobject.UploadId;
import com.example.magrathea.objectstorage.domain.valueobject.UploadPart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Application service for multipart upload operations.
 * Bridges CompletableFuture (domain) to Mono (reactive).
 */
@Service
public class MultipartUploadService {

    private final MultipartUploadRepository repository;
    private final BucketRepository bucketRepository;

    public MultipartUploadService(MultipartUploadRepository repository,
                                  BucketRepository bucketRepository) {
        this.repository = repository;
        this.bucketRepository = bucketRepository;
    }

    /**
     * Create a new multipart upload session.
     */
    public MultipartUpload createUpload(String bucketName, String key) {
        var bucketId = resolveBucket(bucketName);
        var objectKey = ObjectKey.of(key);
        var uploadId = UploadId.generate();
        var id = MultipartUpload.Id.generate();
        var upload = MultipartUpload.create(id, bucketId, objectKey, uploadId);
        repository.save(upload).join();
        return upload;
    }

    /**
     * Upload a part to an existing multipart upload session.
     */
    public UploadPart uploadPart(UploadId uploadId, int partNumber, String etag, long size) {
        var upload = findActiveUpload(uploadId);
        var part = UploadPart.create(PartNumber.of(partNumber), etag, size);
        var updated = upload.withPart(part);
        repository.save(updated).join();
        return part;
    }

    /**
     * Complete a multipart upload — assembles parts into final object.
     * Returns the final object info: key, uploadId, etag.
     */
    public CompletableFuture<Optional<MultipartUpload>> completeUpload(UploadId uploadId) {
        return repository.findByUploadId(uploadId).thenApply(maybeUpload ->
            maybeUpload.map(upload -> {
                if (!upload.hasParts()) throw new IllegalStateException("No parts to complete");
                return upload.withCompleted();
            }).map(completed -> {
                repository.save(completed).join();
                return completed;
            })
        );
    }

    /**
     * Abort a multipart upload.
     */
    public CompletableFuture<Optional<MultipartUpload>> abortUpload(UploadId uploadId) {
        return repository.findByUploadId(uploadId).thenApply(maybeUpload ->
            maybeUpload.map(upload -> {
                var aborted = upload.withAborted();
                repository.save(aborted).join();
                return aborted;
            })
        );
    }

    /**
     * List ongoing multipart uploads for a bucket.
     */
    public CompletableFuture<List<MultipartUpload>> listUploads(String bucketName) {
        var bucketId = resolveBucket(bucketName);
        return repository.findByBucket(bucketId);
    }

    /**
     * List parts for a specific multipart upload.
     */
    public CompletableFuture<Optional<MultipartUpload>> listParts(UploadId uploadId) {
        return repository.findByUploadId(uploadId);
    }

    private Bucket.Id resolveBucket(String bucketName) {
        return bucketRepository.findByName(bucketName)
            .join()
            .map(Bucket::id)
            .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + bucketName));
    }

    private MultipartUpload findActiveUpload(UploadId uploadId) {
        return repository.findByUploadId(uploadId)
            .join()
            .filter(MultipartUpload::isActive)
            .orElseThrow(() -> new IllegalArgumentException("Active multipart upload not found: " + uploadId.value()));
    }
}
