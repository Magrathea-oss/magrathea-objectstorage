package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.s3api.dto.query.AccessControlPolicyQuery;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
import com.example.magrathea.s3api.dto.query.TaggingQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Bucket metadata-context S3 operations: ACL and tagging.
 * Minimal handler — extracts HTTP headers, delegates to service, converts response.
 * Handler-local state removed (postponed → repository).
 * Uses Jackson XML codec for request body deserialization.
 */
public class S3BucketMetadataHandler {

    private final ReactiveBucketService bucketService;

    public S3BucketMetadataHandler(ReactiveBucketService bucketService) {
        this.bucketService = bucketService;
    }

    /** GET /{bucket}?acl — GetBucketAcl */
    public Mono<ServerResponse> getBucketAcl(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(AccessControlPolicyQuery.canned("private")))
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchBucket", "Bucket not found")));
    }

    /** PUT /{bucket}?acl — PutBucketAcl */
    public Mono<ServerResponse> putBucketAcl(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                // TODO: ACL persistence postponed → repository
                var acl = request.headers().firstHeader("x-amz-acl");
                return ServerResponse.ok().build();
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchBucket", "Bucket not found")));
    }

    /** GET /{bucket}?tagging — GetBucketTagging */
    public Mono<ServerResponse> getBucketTagging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                // TODO: tagging persistence postponed → repository
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(new TaggingQuery(new TaggingQuery.TagSet(List.of())));
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchBucket", "Bucket not found")));
    }

    /** PUT /{bucket}?tagging — PutBucketTagging */
    public Mono<ServerResponse> putBucketTagging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                // TODO: tagging persistence postponed → repository
                // Consume the request body without type-specific decoding to avoid
                // content-type negotiation issues with various AWS CLI versions.
                return request.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .then(ServerResponse.ok().build());
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchBucket", "Bucket not found")));
    }

    /** DELETE /{bucket}?tagging — DeleteBucketTagging */
    public Mono<ServerResponse> deleteBucketTagging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                // TODO: tagging persistence postponed → repository
                return ServerResponse.noContent().build();
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchBucket", "Bucket not found")));
    }
}
