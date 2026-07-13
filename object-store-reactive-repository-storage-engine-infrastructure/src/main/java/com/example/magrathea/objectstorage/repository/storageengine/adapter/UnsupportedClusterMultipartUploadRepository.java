package com.example.magrathea.objectstorage.repository.storageengine.adapter;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadQueryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Explicitly rejects multipart rather than falling through to single-node persistence. */
@Repository
@Profile("storage-engine & cluster")
public final class UnsupportedClusterMultipartUploadRepository
        implements MultipartUploadCommandRepository, MultipartUploadQueryRepository {
    @Override
    public Mono<CommandResult<MultipartUpload>> save(MultipartUpload upload) {
        return unsupported();
    }

    @Override
    public Mono<CommandResult<MultipartUpload>> delete(MultipartUpload upload) {
        return unsupported();
    }

    @Override
    public Mono<MultipartUpload> findById(UploadId uploadId) {
        return unsupported();
    }

    @Override
    public Flux<MultipartUpload> findByBucket(Bucket.Id bucketId) {
        return Flux.error(failure());
    }

    @Override
    public Flux<UploadPart> findParts(UploadId uploadId) {
        return Flux.error(failure());
    }

    private static <T> Mono<T> unsupported() {
        return Mono.error(failure());
    }

    private static UnsupportedOperationException failure() {
        return new UnsupportedOperationException(
                "multipart is not implemented by the EP-10 cluster profile");
    }
}
