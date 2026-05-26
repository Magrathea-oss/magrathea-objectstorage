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
import com.example.magrathea.s3api.dto.query.LegalHoldQuery;
import com.example.magrathea.s3api.dto.query.RetentionQuery;
import com.example.magrathea.s3api.dto.command.LegalHoldCommand;
import com.example.magrathea.s3api.dto.command.RetentionCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Object metadata-context S3 operations: ACL, tagging, object attributes, and encryption.
 * Uses Jackson XML codec for request body deserialization.
 */
public class S3ObjectMetadataHandler {

    private final ReactiveBucketService bucketService;
    private final ReactiveObjectService objectService;
    private final ConcurrentHashMap<String, String> objectAclStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TaggingQuery.TagEntry>> objectTagStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, String>> objectEncryptionStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> objectLegalHoldStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RetentionRecord> objectRetentionStore = new ConcurrentHashMap<>();

    /**
     * In-memory retention record for an object.
     */
    private record RetentionRecord(String mode, String retainUntilDate) {}

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

    /** PUT /{bucket}/{key}?encryption — UpdateObjectEncryption */
    public Mono<ServerResponse> updateObjectEncryption(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> {
                        // Read encryption headers and store them
                        var encryptionHeaders = new java.util.HashMap<String, String>();
                        var sse = request.headers().firstHeader("x-amz-server-side-encryption");
                        var kmsKeyId = request.headers().firstHeader("x-amz-server-side-encryption-aws-kms-key-id");
                        var customerAlgorithm = request.headers().firstHeader("x-amz-server-side-encryption-customer-algorithm");
                        var customerKeyMd5 = request.headers().firstHeader("x-amz-server-side-encryption-customer-key-MD5");
                        if (sse != null) encryptionHeaders.put("x-amz-server-side-encryption", sse);
                        if (kmsKeyId != null) encryptionHeaders.put("x-amz-server-side-encryption-aws-kms-key-id", kmsKeyId);
                        if (customerAlgorithm != null) encryptionHeaders.put("x-amz-server-side-encryption-customer-algorithm", customerAlgorithm);
                        if (customerKeyMd5 != null) encryptionHeaders.put("x-amz-server-side-encryption-customer-key-MD5", customerKeyMd5);
                        objectEncryptionStore.put(objectStoreKey(bucketName, key), encryptionHeaders);
                        return ServerResponse.ok().build();
                    })
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  Legal Hold
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}/{key}?legal-hold — GetObjectLegalHold */
    public Mono<ServerResponse> getObjectLegalHold(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> {
                        var active = objectLegalHoldStore.getOrDefault(objectStoreKey(bucketName, key), false);
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_XML)
                            .bodyValue(LegalHoldQuery.from(active));
                    })
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}/{key}?legal-hold — PutObjectLegalHold */
    public Mono<ServerResponse> putObjectLegalHold(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> request.bodyToMono(LegalHoldCommand.class)
                        .flatMap(cmd -> {
                            objectLegalHoldStore.put(objectStoreKey(bucketName, key), cmd.isActive());
                            return ServerResponse.ok().build();
                        }))
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  Retention
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}/{key}?retention — GetObjectRetention */
    public Mono<ServerResponse> getObjectRetention(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> {
                        var retention = objectRetentionStore.get(objectStoreKey(bucketName, key));
                        if (retention == null) {
                            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchRetentionConfiguration",
                                "The retention configuration does not exist");
                        }
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_XML)
                            .bodyValue(RetentionQuery.from(retention.mode(), retention.retainUntilDate()));
                    })
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}/{key}?retention — PutObjectRetention */
    public Mono<ServerResponse> putObjectRetention(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> request.bodyToMono(RetentionCommand.class)
                        .flatMap(cmd -> {
                            objectRetentionStore.put(
                                objectStoreKey(bucketName, key),
                                new RetentionRecord(cmd.mode(), cmd.retainUntilDate()));
                            return ServerResponse.ok().build();
                        }))
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    private static String objectStoreKey(String bucketName, String key) {
        return bucketName + "/" + key;
    }
}
