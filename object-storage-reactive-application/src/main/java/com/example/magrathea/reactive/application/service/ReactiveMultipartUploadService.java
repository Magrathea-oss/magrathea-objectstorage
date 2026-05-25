package com.example.magrathea.reactive.application.service;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstorage.domain.valueobject.UploadId;
import com.example.magrathea.objectstorage.domain.valueobject.UploadPart;
import com.example.magrathea.objectstorage.reactive.repository.application.MultipartUploadCommandRepository;
import com.example.magrathea.objectstorage.reactive.repository.application.MultipartUploadQueryRepository;
import com.example.magrathea.objectstorage.reactive.repository.application.CommandResult;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;

@Service
public class ReactiveMultipartUploadService {

    private final MultipartUploadCommandRepository commandRepository;
    private final MultipartUploadQueryRepository queryRepository;

    public ReactiveMultipartUploadService(MultipartUploadCommandRepository commandRepository,
                                           MultipartUploadQueryRepository queryRepository) {
        this.commandRepository = commandRepository;
        this.queryRepository = queryRepository;
    }

    public Mono<CommandResult<MultipartUpload>> saveUpload(MultipartUpload upload) {
        return commandRepository.save(upload);
    }

    public Mono<CommandResult<MultipartUpload>> deleteUpload(MultipartUpload upload) {
        return commandRepository.delete(upload);
    }

    public Mono<MultipartUpload> findById(UploadId uploadId) {
        return queryRepository.findById(uploadId);
    }

    public Flux<MultipartUpload> findByBucket(Bucket.Id bucketId) {
        return queryRepository.findByBucket(bucketId);
    }

    public Flux<UploadPart> findParts(UploadId uploadId) {
        return queryRepository.findParts(uploadId);
    }
}
