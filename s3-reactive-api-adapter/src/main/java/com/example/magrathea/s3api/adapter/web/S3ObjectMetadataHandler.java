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
 *
 * Phase E operations (ACL, tagging, encryption) use local in-memory maps as simplified
 * adapter storage. Phase F operations (legal hold, retention, lock config) delegate to
 * ReactiveObjectService for domain/application ownership.
 */
public class S3ObjectMetadataHandler {

    private final ReactiveBucketService bucketService;
    private final ReactiveObjectService objectService;
    private final ConcurrentHashMap<String, String> objectAclStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TaggingQuery.TagEntry>> objectTagStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, String>> objectEncryptionStore = new ConcurrentHashMap<>();

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
    //  Legal Hold (Phase F — delegated to service)
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}/{key}?legal-hold — GetObjectLegalHold */
    public Mono<ServerResponse> getObjectLegalHold(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.getObjectLegalHold(bucketName, ObjectKey.of(key))
                    .flatMap(hold -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(LegalHoldQuery.from(hold.status())))
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
                return request.bodyToMono(LegalHoldCommand.class)
                    .flatMap(cmd -> objectService.putObjectLegalHold(bucketName, ObjectKey.of(key),
                        cmd.isActive()
                            ? com.example.magrathea.objectstore.domain.valueobject.LegalHold.apply()
                            : com.example.magrathea.objectstore.domain.valueobject.LegalHold.remove(java.time.Instant.now())))
                    .then(ServerResponse.ok().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  Retention (Phase F — delegated to service)
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}/{key}?retention — GetObjectRetention */
    public Mono<ServerResponse> getObjectRetention(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                return objectService.getObjectLockConfiguration(bucketName, ObjectKey.of(key))
                    .flatMap(lockConfig -> {
                        var mode = lockConfig.mode().name();
                        var untilDate = lockConfig.retention().appliedAt()
                            .plus(lockConfig.retention().duration()).toString();
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_XML)
                            .bodyValue(RetentionQuery.from(mode, untilDate));
                    })
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchRetentionConfiguration",
                        "The retention configuration does not exist"))
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
                return request.bodyToMono(RetentionCommand.class)
                    .flatMap(cmd -> {
                        var mode = "COMPLIANCE".equals(cmd.mode())
                            ? com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration.ObjectLockMode.COMPLIANCE
                            : com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration.ObjectLockMode.GOVERNANCE;
                        var retainUntil = java.time.Instant.parse(cmd.retainUntilDate());
                        var duration = java.time.Duration.between(java.time.Instant.now(), retainUntil);
                        var retention = com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration.RetentionPeriod.of(duration, java.time.Instant.now());
                        var lockConfig = com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration.of(mode, retention);
                        return objectService.putObjectLockConfiguration(bucketName, ObjectKey.of(key), lockConfig)
                            .then(objectService.putObjectRetention(bucketName, ObjectKey.of(key), retention));
                    })
                    .then(ServerResponse.ok().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    private static String objectStoreKey(String bucketName, String key) {
        return bucketName + "/" + key;
    }
}
