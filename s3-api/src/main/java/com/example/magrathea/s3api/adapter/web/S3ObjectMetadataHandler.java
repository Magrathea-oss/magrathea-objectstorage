package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.dto.BucketResponse;
import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.objectstorage.application.service.ObjectService;
import com.example.magrathea.s3api.dto.command.TaggingCommand;
import com.example.magrathea.s3api.dto.query.AccessControlPolicyQuery;
import com.example.magrathea.s3api.dto.query.TaggingQuery;
import com.example.magrathea.s3api.dto.query.GetObjectAttributesQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Object metadata-context S3 operations: ACL, tagging, and object attributes.
 * Uses Jackson XML codec for request body deserialization.
 */
public class S3ObjectMetadataHandler {

    private final BucketService bucketService;
    private final ObjectService objectService;
    private final ConcurrentHashMap<String, String> objectAclStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TaggingQuery.TagEntry>> objectTagStore = new ConcurrentHashMap<>();

    public S3ObjectMetadataHandler(BucketService bucketService, ObjectService objectService) {
        this.bucketService = bucketService;
        this.objectService = objectService;
    }

    /** GET /{bucket}/{key}?acl — GetObjectAcl */
    public Mono<ServerResponse> getObjectAcl(ServerRequest request) {
        return Mono.fromCallable(() -> {
            var bucket = findBucketOrError(request);
            if (bucket.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            if (objectMissing(request, bucket.get())) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(AccessControlPolicyQuery.canned(objectAclStore.getOrDefault(objectStoreKey(request), "private")));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}/{key}?acl — PutObjectAcl */
    public Mono<ServerResponse> putObjectAcl(ServerRequest request) {
        return Mono.fromCallable(() -> {
            var bucket = findBucketOrError(request);
            if (bucket.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            if (objectMissing(request, bucket.get())) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found");
            }
            objectAclStore.put(objectStoreKey(request), request.headers().firstHeader("x-amz-acl") != null
                ? request.headers().firstHeader("x-amz-acl")
                : "private");
            return ServerResponse.ok().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** GET /{bucket}/{key}?tagging — GetObjectTagging */
    public Mono<ServerResponse> getObjectTagging(ServerRequest request) {
        return Mono.fromCallable(() -> {
            var bucket = findBucketOrError(request);
            if (bucket.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            if (objectMissing(request, bucket.get())) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new TaggingQuery(new TaggingQuery.TagSet(objectTagStore.getOrDefault(objectStoreKey(request), List.of()))));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}/{key}?tagging — PutObjectTagging */
    public Mono<ServerResponse> putObjectTagging(ServerRequest request) {
        var bucket = findBucketOrError(request);
        if (bucket.isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        if (objectMissing(request, bucket.get())) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found");
        }
        return request.bodyToMono(TaggingCommand.class)
            .flatMap(cmd -> {
                objectTagStore.put(objectStoreKey(request),
                    cmd.tagSet().tags().stream()
                        .map(t -> new TaggingQuery.TagEntry(t.key(), t.value()))
                        .toList());
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}/{key}?tagging — DeleteObjectTagging */
    public Mono<ServerResponse> deleteObjectTagging(ServerRequest request) {
        return Mono.fromCallable(() -> {
            var bucket = findBucketOrError(request);
            if (bucket.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            if (objectMissing(request, bucket.get())) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found");
            }
            objectTagStore.remove(objectStoreKey(request));
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** GET /{bucket}/{key}?attributes — GetObjectAttributes */
    public Mono<ServerResponse> getObjectAttributes(ServerRequest request) {
        return Mono.fromCallable(() -> {
            var bucket = findBucketOrError(request);
            if (bucket.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var object = S3WebSupport.findObject(objectService, bucket.get(), request.pathVariable("key"));
            if (object.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(GetObjectAttributesQuery.from(object.get()));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    private Optional<BucketResponse> findBucketOrError(ServerRequest request) {
        return S3WebSupport.findBucket(bucketService, request.pathVariable("bucket"));
    }

    private boolean objectMissing(ServerRequest request, BucketResponse bucket) {
        return S3WebSupport.findObject(objectService, bucket, request.pathVariable("key")).isEmpty();
    }

    private static String objectStoreKey(ServerRequest request) {
        return request.pathVariable("bucket") + "/" + request.pathVariable("key");
    }
}
