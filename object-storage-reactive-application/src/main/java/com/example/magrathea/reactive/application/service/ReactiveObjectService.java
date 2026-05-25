package com.example.magrathea.reactive.application.service;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.aggregate.S3Object;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstorage.reactive.repository.application.S3ObjectCommandRepository;
import com.example.magrathea.objectstorage.reactive.repository.application.S3ObjectQueryRepository;
import com.example.magrathea.objectstorage.reactive.repository.application.CommandResult;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;

@Service
public class ReactiveObjectService {

    private final S3ObjectCommandRepository commandRepository;
    private final S3ObjectQueryRepository queryRepository;

    public ReactiveObjectService(S3ObjectCommandRepository commandRepository,
                                  S3ObjectQueryRepository queryRepository) {
        this.commandRepository = commandRepository;
        this.queryRepository = queryRepository;
    }

    public Mono<CommandResult<S3Object>> saveObject(S3Object object) {
        return commandRepository.save(object);
    }

    public Mono<CommandResult<S3Object>> deleteObject(S3Object object) {
        return commandRepository.delete(object);
    }

    public Mono<S3Object> findById(S3Object.Id objectId) {
        return queryRepository.findById(objectId);
    }

    public Mono<S3Object> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key) {
        return queryRepository.findByBucketAndKey(bucketId, key);
    }

    public Flux<S3Object> findByBucket(Bucket.Id bucketId) {
        return queryRepository.findByBucket(bucketId);
    }

    public Flux<Byte> getContent(S3Object.Id objectId) {
        return queryRepository.getContent(objectId);
    }
}
