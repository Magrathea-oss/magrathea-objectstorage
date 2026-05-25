package com.example.magrathea.objectstorage.reactive.repository.application;

import com.example.magrathea.objectstorage.domain.aggregate.MultipartUpload;
import reactor.core.publisher.Mono;

public interface MultipartUploadCommandRepository {
    Mono<CommandResult<MultipartUpload>> save(MultipartUpload upload);
    Mono<CommandResult<MultipartUpload>> delete(MultipartUpload upload);
}
