package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface MultipartUploadQueryRepository {
    Mono<MultipartUpload> findById(UploadId uploadId);
    Flux<MultipartUpload> findByBucket(Bucket.Id bucketId);
    Flux<UploadPart> findParts(UploadId uploadId);
}
