package com.example.magrathea.reactive.application.service;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.SessionToken;
import com.example.magrathea.objectstore.reactive.repository.application.BucketCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.BucketQueryRepository;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.objectstore.reactive.repository.application.BucketAlreadyExistsException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ReactiveBucketService {

    private final BucketCommandRepository commandRepository;
    private final BucketQueryRepository queryRepository;

    public ReactiveBucketService(BucketCommandRepository commandRepository,
                                  BucketQueryRepository queryRepository) {
        this.commandRepository = commandRepository;
        this.queryRepository = queryRepository;
    }

    // ── Core bucket operations ──

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

    // ── Phase F: ABAC configuration ──

    /**
     * Get ABAC configuration for a bucket.
     */
    public Mono<AbacConfiguration> getAbacConfiguration(String bucketName) {
        return queryRepository.findAbacConfiguration(bucketName);
    }

    /**
     * Set ABAC configuration for a bucket.
     */
    public Mono<Void> putAbacConfiguration(String bucketName, AbacConfiguration config) {
        return commandRepository.saveAbacConfiguration(bucketName, config);
    }

    // ── Phase F: Bucket metadata configuration ──

    /**
     * Get bucket metadata configuration.
     */
    public Mono<BucketMetadataConfiguration> getBucketMetadataConfiguration(String bucketName) {
        return queryRepository.findMetadataConfiguration(bucketName);
    }

    /**
     * Set bucket metadata configuration.
     */
    public Mono<Void> putBucketMetadataConfiguration(String bucketName, BucketMetadataConfiguration config) {
        return commandRepository.saveMetadataConfiguration(bucketName, config);
    }

    /**
     * Delete bucket metadata configuration.
     */
    public Mono<Void> deleteBucketMetadataConfiguration(String bucketName) {
        return commandRepository.saveMetadataConfiguration(bucketName, BucketMetadataConfiguration.empty());
    }

    // ── Phase F: Bucket metadata table configuration ──

    /**
     * Get bucket metadata table configuration.
     */
    public Mono<BucketMetadataTableConfiguration> getBucketMetadataTableConfiguration(String bucketName) {
        return queryRepository.findMetadataTableConfiguration(bucketName);
    }

    /**
     * Set bucket metadata table configuration.
     */
    public Mono<Void> putBucketMetadataTableConfiguration(String bucketName, BucketMetadataTableConfiguration config) {
        return commandRepository.saveMetadataTableConfiguration(bucketName, config);
    }

    /**
     * Delete bucket metadata table configuration.
     */
    public Mono<Void> deleteBucketMetadataTableConfiguration(String bucketName) {
        return commandRepository.saveMetadataTableConfiguration(bucketName, BucketMetadataTableConfiguration.empty());
    }

    // ── Phase F: Directory buckets ──

    /**
     * List all directory buckets.
     */
    public Flux<Bucket> listDirectoryBuckets() {
        return queryRepository.findAll()
            .filter(Bucket::directoryBucket);
    }

    // ── Phase F: Session ──

    /**
     * Create a new session token.
     */
    public Mono<SessionToken> createSession() {
        return Mono.defer(() -> Mono.just(SessionToken.of(UUID.randomUUID().toString())));
    }
}
