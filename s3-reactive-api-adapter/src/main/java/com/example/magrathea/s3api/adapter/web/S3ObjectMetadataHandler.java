package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.reactive.application.service.ReactiveObjectService;
import com.example.magrathea.s3api.dto.command.TaggingCommand;
import com.example.magrathea.s3api.dto.query.AccessControlPolicyQuery;
import com.example.magrathea.s3api.dto.query.TaggingQuery;
import com.example.magrathea.s3api.dto.query.GetObjectAttributesQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Object metadata-context S3 operations: ACL, tagging, and object attributes.
 * Uses Jackson XML codec for request body deserialization.
 */
public class S3ObjectMetadataHandler {

    private final ReactiveBucketService bucketService;
    private final ReactiveObjectService objectService;
    private final ConcurrentHashMap<String, String> objectAclStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TaggingQuery.TagEntry>> objectTagStore = new ConcurrentHashMap<>();

    public S3ObjectMetadataHandler(ReactiveBucketService bucketService,
                                    ReactiveObjectService objectService) {
        this.bucketService = bucketService;
        this.objectService = objectService;
    }

    /** GET /{bucket}/{key}?acl — GetObjectAcl */
    public Mono<ServerResponse> getObjectAcl(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(AccessControlPolicyQuery.canned(
                            objectAclStore.getOrDefault(objectStoreKey(bucketName, key), "private"))))
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}/{key}?acl — PutObjectAcl */
    public Mono<ServerResponse> putObjectAcl(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> {
                        objectAclStore.put(objectStoreKey(bucketName, key),
                            request.headers().firstHeader("x-amz-acl") != null
                                ? request.headers().firstHeader("x-amz-acl")
                                : "private");
                        return ServerResponse.ok().build();
                    })
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}/{key}?tagging — GetObjectTagging */
    public Mono<ServerResponse> getObjectTagging(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(new TaggingQuery(new TaggingQuery.TagSet(
                            objectTagStore.getOrDefault(objectStoreKey(bucketName, key), List.of())))))
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}/{key}?tagging — PutObjectTagging */
    public Mono<ServerResponse> putObjectTagging(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> request.bodyToMono(TaggingCommand.class)
                        .flatMap(cmd -> {
                            objectTagStore.put(objectStoreKey(bucketName, key),
                                cmd.tagSet().tags().stream()
                                    .map(t -> new TaggingQuery.TagEntry(t.key(), t.value()))
                                    .toList());
                            return ServerResponse.ok().build();
                        }))
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}/{key}?tagging — DeleteObjectTagging */
    public Mono<ServerResponse> deleteObjectTagging(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> {
                        objectTagStore.remove(objectStoreKey(bucketName, key));
                        return ServerResponse.noContent().build();
                    })
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}/{key}?attributes — GetObjectAttributes */
    public Mono<ServerResponse> getObjectAttributes(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(GetObjectAttributesQuery.from(obj)))
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    private static String objectStoreKey(String bucketName, String key) {
        return bucketName + "/" + key;
    }
}
