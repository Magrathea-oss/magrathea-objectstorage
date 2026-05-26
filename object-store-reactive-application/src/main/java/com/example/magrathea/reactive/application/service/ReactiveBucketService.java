package com.example.magrathea.reactive.application.service;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.reactive.repository.application.BucketCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.BucketQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.BucketAlreadyExistsException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;

@Service
public class ReactiveBucketService {

    private final BucketCommandRepository commandRepository;
    private final BucketQueryRepository queryRepository;

    public ReactiveBucketService(BucketCommandRepository commandRepository,
                                  BucketQueryRepository queryRepository) {
        this.commandRepository = commandRepository;
        this.queryRepository = queryRepository;
    }

    public Mono<CommandResult<Bucket>> createBucket(Bucket bucket) {
        return queryRepository.findByName(bucket.name())
            .flatMap(existing -> Mono.<CommandResult<Bucket>>error(new BucketAlreadyExistsException(bucket.name())))
            .switchIfEmpty(Mono.defer(() -> commandRepository.save(bucket)));
    }

    public Mono<CommandResult<Bucket>> updateBucket(Bucket bucket) {
        return commandRepository.save(bucket);
    }

    public Mono<CommandResult<Bucket>> deleteBucket(Bucket bucket) {
        return commandRepository.delete(bucket.withDeleted());
    }

    public Mono<Bucket> findById(Bucket.Id bucketId) {
        return queryRepository.findById(bucketId);
    }

    public Mono<Bucket> findByName(String bucketName) {
        return queryRepository.findByName(bucketName);
    }

    public Flux<Bucket> findAllBuckets() {
        return queryRepository.findAll();
    }
}
