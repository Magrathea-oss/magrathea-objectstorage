package com.example.magrathea.objectstorage.reactive.repository.application;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstorage.domain.valueobject.UploadId;
import com.example.magrathea.objectstorage.domain.valueobject.UploadPart;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface MultipartUploadQueryRepository {
    Mono<MultipartUpload> findById(UploadId uploadId);
    Flux<MultipartUpload> findByBucket(Bucket.Id bucketId);
    Flux<UploadPart> findParts(UploadId uploadId);
}
