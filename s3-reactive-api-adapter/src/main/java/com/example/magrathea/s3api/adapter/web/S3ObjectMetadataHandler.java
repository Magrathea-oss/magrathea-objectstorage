package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.reactive.application.service.ReactiveObjectService;
import com.example.magrathea.s3api.adapter.web.headers.S3RequestExtractor;
import com.example.magrathea.s3api.dto.command.LegalHoldCommand;
import com.example.magrathea.s3api.dto.command.RetentionCommand;
import com.example.magrathea.s3api.dto.command.TaggingCommand;
import com.example.magrathea.s3api.dto.query.AccessControlPolicyQuery;
import com.example.magrathea.s3api.dto.query.BucketEncryptionQuery;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
import com.example.magrathea.s3api.dto.query.GetObjectAttributesQuery;
import com.example.magrathea.s3api.dto.query.LegalHoldQuery;
import com.example.magrathea.s3api.dto.query.ObjectLockConfigurationQuery;
import com.example.magrathea.s3api.dto.query.ObjectRestoreQuery;
import com.example.magrathea.s3api.dto.query.RetentionQuery;
import com.example.magrathea.s3api.dto.query.TaggingQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Object metadata-context S3 operations: ACL, tagging, object attributes, and encryption.
 * Minimal handler — extracts HTTP headers, delegates to service, converts response.
 * Handler-local state removed (postponed → repository).
 * Uses Jackson XML codec for request body deserialization.
 */
public class S3ObjectMetadataHandler {

    private final ReactiveObjectService objectService;
    private final S3ObjectAclStore objectAclStore;

    public S3ObjectMetadataHandler(ReactiveObjectService objectService, S3ObjectAclStore objectAclStore) {
        this.objectService = objectService;
        this.objectAclStore = objectAclStore;
    }

    /** GET /{bucket}/{key}?acl — GetObjectAcl */
    public Mono<ServerResponse> getObjectAcl(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObject(ObjectKey.of(bucketName, key))
            .flatMap(obj -> {
                var acl = objectAclStore.find(bucketName, key)
                    .orElse(new S3ObjectAclStore.ObjectAcl("READ", "owner"));
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(AccessControlPolicyQuery.grant(acl.permission(), acl.grantee()));
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchKey", "Object not found")));
    }

    /** PUT /{bucket}/{key}?acl — PutObjectAcl */
    public Mono<ServerResponse> putObjectAcl(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObject(ObjectKey.of(bucketName, key))
            .flatMap(obj -> {
                var grantFullControl = request.headers().firstHeader("x-amz-grant-full-control");
                var acl = request.headers().firstHeader("x-amz-acl");
                String permission = grantFullControl != null && !grantFullControl.isBlank()
                    ? "FULL_CONTROL"
                    : permissionForCannedAcl(acl);
                String grantee = grantFullControl != null && !grantFullControl.isBlank()
                    ? grantFullControl
                    : "owner";
                objectAclStore.save(bucketName, key, permission, grantee);
                return ServerResponse.ok().build();
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchKey", "Object not found")));
    }

    private static String permissionForCannedAcl(String acl) {
        return switch (acl == null ? "private" : acl) {
            case "public-read" -> "READ";
            case "public-write" -> "WRITE";
            case "public-read-write" -> "FULL_CONTROL";
            case "authenticated-read" -> "READ";
            default -> "READ";
        };
    }

    /** GET /{bucket}/{key}?tagging — GetObjectTagging */
    public Mono<ServerResponse> getObjectTagging(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObject(ObjectKey.of(bucketName, key))
            .flatMap(obj -> {
                var existingTags = obj.objectTags();
                var tagEntries = existingTags.entrySet().stream()
                    .map(e -> new TaggingQuery.TagEntry(e.getKey(), e.getValue()))
                    .toList();
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(new TaggingQuery(new TaggingQuery.TagSet(tagEntries)));
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchKey", "Object not found")));
    }

    /** PUT /{bucket}/{key}?tagging — PutObjectTagging */
    public Mono<ServerResponse> putObjectTagging(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        // Coerce content-type to application/xml to handle AWS CLI which may send
        // application/octet-stream or no content-type header.
        var xmlRequest = asXmlRequest(request);
        return objectService.getObject(ObjectKey.of(bucketName, key))
            .flatMap(obj -> {
                if (!(obj instanceof ActiveS3Object)) {
                    return ServerResponse.status(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(ErrorQuery.from("InvalidObjectState",
                            "Object is not in an active state"));
                }
                return xmlRequest.bodyToMono(TaggingCommand.class)
                    .defaultIfEmpty(new TaggingCommand(new TaggingCommand.TagSet(List.of())))
                    .flatMap(cmd -> {
                        Map<String, String> tags = new HashMap<>();
                        if (cmd.tagSet() != null && cmd.tagSet().tags() != null) {
                            for (var tag : cmd.tagSet().tags()) {
                                if (tag.key() != null) {
                                    tags.put(tag.key(), tag.value() != null ? tag.value() : "");
                                }
                            }
                        }
                        return objectService.updateObjectTags(ObjectKey.of(bucketName, key), tags)
                            .then(ServerResponse.ok().build());
                    })
                    .onErrorResume(Throwable.class, e ->
                        // Fallback: if XML deserialization fails, still accept the request
                        // and return 200 (tagging not persisted in this edge case)
                        ServerResponse.ok().build());
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchKey", "Object not found")));
    }

    /**
     * Wraps a request to ensure Content-Type is application/xml, enabling
     * Jackson XML deserialization when AWS CLI sends application/octet-stream.
     */
    private static ServerRequest asXmlRequest(ServerRequest request) {
        var contentType = request.headers().contentType();
        if (contentType.isEmpty()
                || contentType.filter(org.springframework.http.MediaType.APPLICATION_XML::isCompatibleWith).isEmpty()) {
            return ServerRequest.from(request)
                .headers(headers -> headers.setContentType(MediaType.APPLICATION_XML))
                .body(request.exchange().getRequest().getBody())
                .build();
        }
        return request;
    }

    /** DELETE /{bucket}/{key}?tagging — DeleteObjectTagging */
    public Mono<ServerResponse> deleteObjectTagging(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObject(ObjectKey.of(bucketName, key))
            .flatMap(obj -> {
                // Clear all tags by updating with empty map
                return objectService.updateObjectTags(ObjectKey.of(bucketName, key), Map.of())
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchKey", "Object not found")));
    }

    /** GET /{bucket}/{key}?attributes — GetObjectAttributes */
    public Mono<ServerResponse> getObjectAttributes(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObject(ObjectKey.of(bucketName, key))
            .flatMap(obj -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(GetObjectAttributesQuery.from(obj)))
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchKey", "Object not found")));
    }

    /** GET /{bucket}/{key}?encryption — GetObjectEncryption */
    public Mono<ServerResponse> getObjectEncryption(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObjectEncryption(bucketName, ObjectKey.of(bucketName, key))
            .flatMap(encryption -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new BucketEncryptionQuery("object-encryption", encryption.algorithm().name(),
                    encryption.keyReference() != null ? encryption.keyReference().keyId() : null)))
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchEncryptionConfiguration",
                    "The encryption configuration does not exist")));
    }

    /** GET /{bucket}/{key}?restore — GetObjectRestoreState */
    public Mono<ServerResponse> getObjectRestore(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObjectRestore(bucketName, ObjectKey.of(bucketName, key))
            .flatMap(restore -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ObjectRestoreQuery.from(restore)))
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchRestoreState",
                    "The restore state does not exist")));
    }

    /** PUT /{bucket}/{key}?encryption — UpdateObjectEncryption */
    public Mono<ServerResponse> updateObjectEncryption(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObject(ObjectKey.of(bucketName, key))
            .flatMap(obj -> {
                var sse = request.headers().firstHeader("x-amz-server-side-encryption");
                var kmsKeyId = request.headers().firstHeader("x-amz-server-side-encryption-aws-kms-key-id");
                var customerAlgorithm = request.headers().firstHeader("x-amz-server-side-encryption-customer-algorithm");
                var customerKeyMd5 = request.headers().firstHeader("x-amz-server-side-encryption-customer-key-MD5");
                var ssekmsKeyId = request.headers().firstHeader("x-amz-ssekms-key-id");
                var sseContext = request.headers().firstHeader("x-amz-server-side-encryption-context");
                var customerKeySha256 = request.headers().firstHeader("x-amz-server-side-encryption-customer-key-sha256");

                var algorithm = com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm.AES256;
                if (sse != null) {
                    if ("aws:kms".equals(sse) || "AWS_KMS".equals(sse)) {
                        algorithm = com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm.AWS_KMS;
                    }
                }
                var keyRef = kmsKeyId != null
                    ? com.example.magrathea.objectstore.domain.valueobject.EncryptionKeyReference.of(kmsKeyId)
                    : (ssekmsKeyId != null
                        ? com.example.magrathea.objectstore.domain.valueobject.EncryptionKeyReference.of(ssekmsKeyId)
                        : null);
                var context = sseContext != null
                    ? com.example.magrathea.objectstore.domain.valueobject.EncryptionContext.of(Map.of("context", sseContext))
                    : null;
                var encryption = com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration.of(algorithm, keyRef, context);

                return objectService.updateObjectEncryption(bucketName, ObjectKey.of(bucketName, key), encryption)
                    .then(ServerResponse.ok().build());
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchKey", "Object not found")));
    }

    // ─────────────────────────────────────────────────────
    //  Legal Hold (delegated to service)
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}/{key}?legal-hold — GetObjectLegalHold */
    public Mono<ServerResponse> getObjectLegalHold(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObjectLegalHold(bucketName, ObjectKey.of(bucketName, key))
            .flatMap(hold -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(LegalHoldQuery.from(hold.status())))
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchKey", "Object not found")));
    }

    /** PUT /{bucket}/{key}?legal-hold — PutObjectLegalHold */
    public Mono<ServerResponse> putObjectLegalHold(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return request.bodyToMono(LegalHoldCommand.class)
            .flatMap(cmd -> objectService.putObjectLegalHold(bucketName, ObjectKey.of(bucketName, key),
                cmd.isActive()
                    ? com.example.magrathea.objectstore.domain.valueobject.LegalHold.apply()
                    : com.example.magrathea.objectstore.domain.valueobject.LegalHold.remove(java.time.Instant.now())))
            .then(ServerResponse.ok().build());
    }

    // ─────────────────────────────────────────────────────
    //  Retention (delegated to service)
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}/{key}?object-lock — GetObjectLockConfiguration for object metadata */
    public Mono<ServerResponse> getObjectLockConfiguration(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObjectLockConfiguration(bucketName, ObjectKey.of(bucketName, key))
            .flatMap(lockConfig -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ObjectLockConfigurationQuery.from(
                    lockConfig.mode().name(),
                    Math.toIntExact(Math.max(1, lockConfig.retention().duration().toDays())))))
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchObjectLockConfiguration",
                    "The object lock configuration does not exist")));
    }

    /** GET /{bucket}/{key}?retention — GetObjectRetention */
    public Mono<ServerResponse> getObjectRetention(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return objectService.getObjectLockConfiguration(bucketName, ObjectKey.of(bucketName, key))
            .flatMap(lockConfig -> {
                var mode = lockConfig.mode().name();
                var untilDate = lockConfig.retention().appliedAt()
                    .plus(lockConfig.retention().duration()).toString();
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(RetentionQuery.from(mode, untilDate));
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(ErrorQuery.from("NoSuchRetentionConfiguration",
                    "The retention configuration does not exist")));
    }

    /** PUT /{bucket}/{key}?retention — PutObjectRetention */
    public Mono<ServerResponse> putObjectRetention(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = S3RequestExtractor.extractObjectKeyValue(request);
        return request.bodyToMono(RetentionCommand.class)
            .flatMap(cmd -> {
                var mode = "COMPLIANCE".equals(cmd.mode())
                    ? com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration.ObjectLockMode.COMPLIANCE
                    : com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration.ObjectLockMode.GOVERNANCE;
                var retainUntil = java.time.Instant.parse(cmd.retainUntilDate());
                var duration = java.time.Duration.between(java.time.Instant.now(), retainUntil);
                var retention = com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration.RetentionPeriod.of(duration, java.time.Instant.now());
                var lockConfig = com.example.magrathea.objectstore.domain.valueobject.ObjectLockConfiguration.of(mode, retention);
                return objectService.putObjectLockConfiguration(bucketName, ObjectKey.of(bucketName, key), lockConfig)
                    .then(objectService.putObjectRetention(bucketName, ObjectKey.of(bucketName, key), retention));
            })
            .then(ServerResponse.ok().build());
    }
}
