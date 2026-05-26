package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.s3api.dto.command.TaggingCommand;
import com.example.magrathea.s3api.dto.query.AccessControlPolicyQuery;
import com.example.magrathea.s3api.dto.query.TaggingQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bucket metadata-context S3 operations: ACL and tagging.
 * Uses Jackson XML codec for request body deserialization.
 */
public class S3BucketMetadataHandler {

    private final ReactiveBucketService bucketService;
    private final ConcurrentHashMap<String, String> bucketAclStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TaggingQuery.TagEntry>> bucketTagStore = new ConcurrentHashMap<>();

    public S3BucketMetadataHandler(ReactiveBucketService bucketService) {
        this.bucketService = bucketService;
    }

    /** GET /{bucket}?acl — GetBucketAcl */
    public Mono<ServerResponse> getBucketAcl(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(AccessControlPolicyQuery.canned(bucketAclStore.getOrDefault(bucket, "private"))))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?acl — PutBucketAcl */
    public Mono<ServerResponse> putBucketAcl(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                bucketAclStore.put(bucket, request.headers().firstHeader("x-amz-acl") != null
                    ? request.headers().firstHeader("x-amz-acl")
                    : "private");
                return ServerResponse.ok().build();
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}?tagging — GetBucketTagging */
    public Mono<ServerResponse> getBucketTagging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new TaggingQuery(new TaggingQuery.TagSet(bucketTagStore.getOrDefault(bucket, List.of())))))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?tagging — PutBucketTagging */
    public Mono<ServerResponse> putBucketTagging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(TaggingCommand.class)
                .flatMap(cmd -> {
                    bucketTagStore.put(bucket,
                        cmd.tagSet().tags().stream()
                            .map(t -> new TaggingQuery.TagEntry(t.key(), t.value()))
                            .toList());
                    return ServerResponse.ok().build();
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?tagging — DeleteBucketTagging */
    public Mono<ServerResponse> deleteBucketTagging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                bucketTagStore.remove(bucket);
                return ServerResponse.noContent().build();
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }
}
