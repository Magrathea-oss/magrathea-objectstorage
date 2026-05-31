package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.StorageClass;
import java.util.Set;
import java.util.stream.Collectors;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.reactive.application.service.ReactiveObjectService;
import com.example.magrathea.s3api.adapter.web.headers.S3Header;
import com.example.magrathea.s3api.adapter.web.headers.S3RequestExtractor;
import com.example.magrathea.s3api.adapter.web.headers.S3ResponseBuilder;
import com.example.magrathea.reactive.infrastructure.adapter.persistence.BucketNotFoundException;
import com.example.magrathea.s3api.dto.command.DeleteObjectsCommand;
import com.example.magrathea.s3api.dto.command.RestoreObjectCommand;
import com.example.magrathea.s3api.dto.command.SelectObjectContentCommand;
import com.example.magrathea.s3api.dto.query.CopyObjectResultQuery;
import com.example.magrathea.s3api.dto.query.DeleteResultQuery;
import com.example.magrathea.s3api.dto.query.SelectObjectContentQuery;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Object-context S3 operations.
 * Minimal handler — all extraction via {@link S3RequestExtractor},
 * all response building via {@link S3ResponseBuilder}.
 * Performs bucket validation and input validation before delegating to service.
 */
public class S3ObjectOperationsHandler {

    private final ReactiveBucketService bucketService;
    private final ReactiveObjectService objectService;

    // Temporary ACL storage (per-object ACL map)
    private final ConcurrentHashMap<String, String> objectAclStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.List<String>> objectGrantStore = new ConcurrentHashMap<>();

    /** Expose ACL store for sharing with S3ObjectMetadataHandler */
    public ConcurrentHashMap<String, String> getObjectAclStore() { return objectAclStore; }

    /** Expose grant store for sharing with S3ObjectMetadataHandler */
    public ConcurrentHashMap<String, java.util.List<String>> getObjectGrantStore() { return objectGrantStore; }

    public S3ObjectOperationsHandler(ReactiveBucketService bucketService,
                                      ReactiveObjectService objectService) {
        this.bucketService = bucketService;
        this.objectService = objectService;
    }

    // ─────────────────────────────────────────────────────
    //  Validation helpers
    // ─────────────────────────────────────────────────────

    private static boolean isValidBucketName(String bucketName) {
        if (bucketName == null || bucketName.isBlank()) return false;
        if (bucketName.length() < 3 || bucketName.length() > 63) return false;
        return bucketName.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$");
    }

    private static boolean isValidStorageClass(String storageClass) {
        if (storageClass == null) return true; // null is allowed (default)
        var knownClasses = Set.of("STANDARD", "STANDARD_IA", "ONEZONE_IA", "GLACIER",
            "GLACIER_DEEP_ARCHIVE", "INTELLIGENT_TIERING", "PARANOIC_MODE");
        return knownClasses.contains(storageClass);
    }

    private static boolean isValidSdkChecksumAlgorithm(String algorithm) {
        if (algorithm == null) return true;
        return ChecksumAlgorithm.fromApiName(algorithm).isPresent();
    }

    private static boolean hasCorrespondingChecksumHeader(String sdkAlgorithm, ServerRequest request) {
        if (sdkAlgorithm == null) return true;
        var algo = ChecksumAlgorithm.fromApiName(sdkAlgorithm).orElse(null);
        if (algo == null) return false;
        if (algo == ChecksumAlgorithm.MD5) {
            return request.headers().firstHeader(S3Header.CONTENT_MD5.headerName()) != null;
        }
        var headerName = "x-amz-checksum-" + algo.apiName();
        return request.headers().firstHeader(headerName) != null;
    }

    private static String storeKey(String bucketName, String key) {
        return bucketName + "/" + key;
    }

    // ─────────────────────────────────────────────────────
    //  PutObject
    // ─────────────────────────────────────────────────────

    /** PUT /{bucket}/{key} — PutObject */
    public Mono<ServerResponse> putObject(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var storageClass = S3RequestExtractor.extractStorageClass(request);
        var checksum = S3RequestExtractor.extractChecksum(request);
        var sdkAlgorithm = checksum != null ? checksum.sdkAlgorithm() : null;
        var contentLength = S3RequestExtractor.extractContentLength(request);

        // Input validation
        if (key == null || key.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidArgument", "Key is empty");
        }
        if (!isValidBucketName(bucketName)) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidBucketName", "Bucket name is invalid");
        }
        if (storageClass != null && !isValidStorageClass(storageClass)) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidStorageClass", "Storage class is invalid");
        }
        if (sdkAlgorithm != null && !isValidSdkChecksumAlgorithm(sdkAlgorithm)) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidChecksumAlgorithm", "Unknown SDK checksum algorithm");
        }
        if (sdkAlgorithm != null && !hasCorrespondingChecksumHeader(sdkAlgorithm, request)) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingChecksumHeader", "Missing hash header for SDK checksum algorithm");
        }
        if (contentLength < 0) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidContentLength", "Content length is negative");
        }

        // Verify bucket exists before saving
        return bucketService.findByName(bucketName)
            .flatMap(bucket -> objectService.saveObjectWithContent(
                S3RequestExtractor.extractObjectKey(request),
                storageClass,
                checksum,
                S3RequestExtractor.extractEncryption(request),
                S3RequestExtractor.extractContentType(request),
                contentLength,
                S3RequestExtractor.extractUserMetadata(request),
                request.bodyToFlux(DataBuffer.class))
            .flatMap(result -> {
                var obj = result.aggregate();
                var resp = S3ResponseBuilder.ok(obj);
                // Store ACL from request headers
                var aclHeader = request.headers().firstHeader("x-amz-acl");
                if (aclHeader != null && !aclHeader.isBlank()) {
                    objectAclStore.put(storeKey(bucketName, key), aclHeader);
                }
                // Store grant headers
                var grantRead = request.headers().firstHeader("x-amz-grant-read");
                var grantFullControl = request.headers().firstHeader("x-amz-grant-full-control");
                if (grantRead != null || grantFullControl != null) {
                    var grants = new java.util.ArrayList<String>();
                    if (grantRead != null) grants.add("Grant");
                    if (grantFullControl != null) grants.add("FullControl");
                    objectGrantStore.put(storeKey(bucketName, key), grants);
                }
                return resp;
            }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
            .onErrorResume(BucketNotFoundException.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage()));
    }

    // ─────────────────────────────────────────────────────
    //  CopyObject
    // ─────────────────────────────────────────────────────

    /** PUT /{bucket}/{key} with x-amz-copy-source — CopyObject */
    public Mono<ServerResponse> copyObject(ServerRequest request) {
        var sourceHeader = request.headers().firstHeader(
            S3Header.X_AMZ_COPY_SOURCE.headerName());
        var source = S3WebSupport.decodeCopySource(sourceHeader);
        if (source.isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidArgument",
                "Invalid copy source");
        }
        var sourceObjectKey = ObjectKey.of(source.get()[0], source.get()[1]);
        var targetKey = S3RequestExtractor.extractObjectKey(request);

        return objectService.getObject(sourceObjectKey)
            .flatMap(sourceObject -> copyObjectWithContent(
                sourceObject, targetKey, request))
            .switchIfEmpty(S3WebSupport.xmlError(
                HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"))
            .onErrorResume(BucketNotFoundException.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    private Mono<ServerResponse> copyObjectWithContent(
            S3Object sourceObject, ObjectKey targetKey, ServerRequest request) {
        var size = sourceObject.size();
        var checksum = sourceObject.checksum();
        var encryption = sourceObject.encryption();
        var metadata = sourceObject.userMetadata();
        var storageClass = sourceObject.storageClass();

        var etag = checksum != null ? S3ResponseBuilder.computeEtagFromChecksum(checksum) : "\"\"";
        return objectService.saveObjectWithContent(
                targetKey,
                storageClass,
                checksum != null ? checksum : ObjectChecksum.of(java.util.Set.of()),
                encryption,
                null,
                size,
                metadata != null
                    ? com.example.magrathea.objectstore.domain.valueobject.UserMetadata.of(metadata)
                    : com.example.magrathea.objectstore.domain.valueobject.UserMetadata.of(java.util.Map.of()),
                objectService.getContent(sourceObject.key()))
            .flatMap(result -> S3ResponseBuilder.okXml(etag,
                CopyObjectResultQuery.from(Instant.now().toString(), etag)));
    }

    // ─────────────────────────────────────────────────────
    //  DeleteObjects
    // ─────────────────────────────────────────────────────

    /** POST /{bucket}?delete — DeleteObjects */
    public Mono<ServerResponse> deleteObjects(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return request.bodyToMono(DeleteObjectsCommand.class)
            .flatMap(cmd -> {
                Flux<String> keys = Flux.fromIterable(cmd.objects()).map(obj -> obj.key());
                Mono<DeleteResultQuery> xmlMono = DeleteResultQuery.from(keys);
                Mono<Void> deleteAll = Flux.fromIterable(cmd.objects())
                    .flatMap(obj -> objectService.deleteObject(
                        ObjectKey.of(bucket, obj.key())))
                    .then();
                return deleteAll.then(xmlMono)
                    .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(result));
            })
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  GetObject
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}/{key} — GetObject */
    public Mono<ServerResponse> getObject(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        // First find bucket for CORS validation and website config, then get object with content
        return bucketService.findByName(objectKey.bucket())
            .flatMap(bucket -> {
                var baseResponse = objectService.getObjectWithContent(objectKey)
                    .flatMap(oc -> S3ResponseBuilder.okWithBodyAndContentType(
                        oc.object(), oc.content(),
                        oc.object().userMetadata() != null
                            ? oc.object().userMetadata().getOrDefault("Content-Type", null)
                            : null))
                    .switchIfEmpty(Mono.defer(() -> {
                        // Check if bucket has website configuration with error document
                        var websiteConfig = bucket.bucketConfig() != null
                            ? bucket.bucketConfig().getWebsiteConfiguration().orElse(null)
                            : null;
                        if (websiteConfig != null && websiteConfig.errorDocument() != null
                            && !websiteConfig.errorDocument().isBlank()) {
                            return ServerResponse.status(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.TEXT_HTML)
                                .bodyValue("<html><body><h1>404 - " + websiteConfig.errorDocument() + "</h1></body></html>");
                        }
                        return S3WebSupport.xmlError(
                            HttpStatus.NOT_FOUND, "NoSuchKey", "Key not found");
                    }));
                // Apply CORS validation if Origin header is present
                return S3WebSupport.withCorsValidation(request, bucket, baseResponse);
            })
            .switchIfEmpty(S3WebSupport.xmlError(
                HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  HeadObject
    // ─────────────────────────────────────────────────────

    /** HEAD /{bucket}/{key} — HeadObject */
    public Mono<ServerResponse> headObject(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        return bucketService.findByName(objectKey.bucket())
            .flatMap(bucket -> {
                var baseResponse = objectService.getObject(objectKey)
                    .flatMap(obj -> {
                        var builder = ServerResponse.ok();
                        S3ResponseBuilder.applyHeaders(builder, obj);
                        var contentType = S3RequestExtractor.extractContentType(request);
                        if (contentType != null && !contentType.isBlank()) {
                            builder.header("Content-Type", contentType);
                        } else {
                            builder.header("Content-Type", "application/octet-stream");
                        }
                        return builder.build();
                    })
                    .switchIfEmpty(Mono.defer(() -> ServerResponse.notFound().build()));
                return S3WebSupport.withCorsValidation(request, bucket, baseResponse);
            })
            .switchIfEmpty(Mono.defer(() -> ServerResponse.notFound().build()));
    }

    // ─────────────────────────────────────────────────────
    //  RenameObject
    // ─────────────────────────────────────────────────────

    /** PUT /{bucket}/{key}?rename — RenameObject */
    public Mono<ServerResponse> renameObject(ServerRequest request) {
        var sourceKey = S3RequestExtractor.extractObjectKey(request);
        var destinationKey = request.headers().firstHeader(
            S3Header.X_AMZ_RENAME_DESTINATION.headerName());
        if (destinationKey == null || destinationKey.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidArgument",
                S3Header.X_AMZ_RENAME_DESTINATION.headerName() + " header is required");
        }

        return objectService.getObject(sourceKey)
            .flatMap(sourceObject -> {
                var targetObjectKey = ObjectKey.of(sourceKey.bucket(), destinationKey);
                var size = sourceObject.size();
                var metadata = sourceObject.userMetadata();
                var checksum = sourceObject.checksum();
                var encryption = sourceObject.encryption();
                var storageClass = sourceObject.storageClass();

                return objectService.saveObjectWithContent(
                        targetObjectKey,
                        storageClass,
                        checksum != null ? checksum : ObjectChecksum.of(java.util.Set.of()),
                        encryption,
                        null,
                        size,
                        metadata != null
                            ? com.example.magrathea.objectstore.domain.valueobject.UserMetadata.of(metadata)
                            : com.example.magrathea.objectstore.domain.valueobject.UserMetadata.of(java.util.Map.of()),
                        objectService.getContent(sourceObject.key()))
                    .then(objectService.deleteObject(sourceKey))
                    .then(S3ResponseBuilder.okWithEtag("\"\""));
            })
            .switchIfEmpty(S3WebSupport.xmlError(
                HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"))
            .onErrorResume(BucketNotFoundException.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  GetObjectTorrent
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}/{key}?torrent — GetObjectTorrent */
    public Mono<ServerResponse> getObjectTorrent(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        return objectService.getObjectTorrent(objectKey)
            .flatMap(torrentData -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(BodyInserters.fromDataBuffers(torrentData)))
            .switchIfEmpty(S3WebSupport.xmlError(
                HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"))
            .onErrorResume(BucketNotFoundException.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  RestoreObject
    // ─────────────────────────────────────────────────────

    /** POST /{bucket}/{key}?restore — RestoreObject */
    public Mono<ServerResponse> restoreObject(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        return objectService.getObject(objectKey)
            .flatMap(obj -> request.bodyToMono(RestoreObjectCommand.class)
                .flatMap(cmd -> objectService.saveObject(obj)
                    .then(ServerResponse.ok().build()))
                .switchIfEmpty(Mono.defer(() -> ServerResponse.ok().build())))
            .switchIfEmpty(S3WebSupport.xmlError(
                HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"))
            .onErrorResume(BucketNotFoundException.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  DeleteObject
    // ─────────────────────────────────────────────────────

    /** DELETE /{bucket}/{key} — DeleteObject */
    public Mono<ServerResponse> deleteObject(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        return objectService.deleteObject(objectKey)
            .then(ServerResponse.noContent().build())
            .onErrorResume(BucketNotFoundException.class, e -> ServerResponse.noContent().build())
            .onErrorResume(Throwable.class, e -> ServerResponse.noContent().build());
    }

    // ─────────────────────────────────────────────────────
    //  SelectObjectContent
    // ─────────────────────────────────────────────────────

    /** POST /{bucket}/{key}?select — SelectObjectContent */
    public Mono<ServerResponse> selectObjectContent(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        return objectService.getObject(objectKey)
            .flatMap(obj -> request.bodyToMono(SelectObjectContentCommand.class)
                .flatMap(cmd -> {
                    var expression = cmd.expression() != null ? cmd.expression() : "";
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(SelectObjectContentQuery.placeholder(expression));
                })
                .switchIfEmpty(Mono.defer(() -> ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(SelectObjectContentQuery.placeholder("")))))
            .switchIfEmpty(S3WebSupport.xmlError(
                HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"))
            .onErrorResume(BucketNotFoundException.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  WriteGetObjectResponse
    // ─────────────────────────────────────────────────────

    /** PUT /{bucket}/{key}?x-id=WriteGetObjectResponse — WriteGetObjectResponse */
    public Mono<ServerResponse> writeGetObjectResponse(ServerRequest request) {
        var requestId = request.headers().firstHeader(
            S3Header.X_AMZ_REQUEST_ID.headerName());

        return request.bodyToFlux(DataBuffer.class)
            .then()
            .then(ServerResponse.ok()
                .header(S3Header.X_AMZ_REQUEST_ID.headerName(),
                    requestId != null ? requestId : "")
                .build())
            .onErrorResume(BucketNotFoundException.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }
}
