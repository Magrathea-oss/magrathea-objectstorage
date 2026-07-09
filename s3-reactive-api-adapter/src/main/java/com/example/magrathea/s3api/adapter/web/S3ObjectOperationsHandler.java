package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.ActiveS3Object;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.reactive.application.service.ReactiveObjectService;
import com.example.magrathea.s3api.adapter.web.headers.S3Header;
import com.example.magrathea.s3api.adapter.web.headers.S3RequestExtractor;
import com.example.magrathea.s3api.adapter.web.headers.S3ResponseBuilder;
import com.example.magrathea.objectstore.reactive.repository.application.BucketNotFoundException;
import com.example.magrathea.objectstore.reactive.repository.application.StorageObjectIntegrityException;
import com.example.magrathea.s3api.dto.command.DeleteObjectsCommand;
import com.example.magrathea.s3api.dto.command.RestoreObjectCommand;
import com.example.magrathea.s3api.dto.command.SelectObjectContentCommand;
import com.example.magrathea.s3api.dto.query.CopyObjectResultQuery;
import com.example.magrathea.s3api.dto.query.DeleteResultQuery;
import com.example.magrathea.s3api.dto.query.SelectObjectContentQuery;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Object-context S3 operations.
 * Minimal handler -- all extraction via {@link S3RequestExtractor},
 * all response building via {@link S3ResponseBuilder}.
 * No queries before commands -- bucket validation is handled by the repository layer.
 */
public class S3ObjectOperationsHandler {

    private final ReactiveObjectService objectService;

    public S3ObjectOperationsHandler(ReactiveObjectService objectService) {
        this.objectService = objectService;
    }

    // ─────────────────────────────────────────────────────
    //  PutObject
    // ─────────────────────────────────────────────────────

    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-(\\d+)");

    /** PUT /{bucket}/{key} -- PutObject */
    public Mono<ServerResponse> putObject(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        var inlineTags = S3RequestExtractor.extractObjectTagging(request);
        var storageClass = S3RequestExtractor.extractStorageClass(request);
        var effectiveStorageClass = storageClass == null || storageClass.isBlank() ? "STANDARD" : storageClass;
        var userMetadata = S3RequestExtractor.extractUserMetadata(request);
        var uploadDigest = new UploadDigest();
        var content = request.bodyToFlux(DataBuffer.class)
            .doOnNext(uploadDigest::update);
        var initialLength = request.headers().contentLength().orElse(0L);
        var active = ActiveS3Object.create(
                objectKey,
                effectiveStorageClass,
                userMetadata != null ? userMetadata.entries() : Map.of(),
                S3RequestExtractor.extractEncryption(request),
                S3RequestExtractor.extractChecksum(request),
                initialLength);
        return objectService.saveObjectWithContent(active, content, effectiveStorageClass)
            .flatMap(result -> persistComputedUploadMeasurements(result.aggregate(), uploadDigest))
            .flatMap(saved -> {
                if (!inlineTags.isEmpty() && saved instanceof ActiveS3Object) {
                    // Persist inline tags from x-amz-tagging header
                    return objectService.updateObjectTags(objectKey, inlineTags)
                        .flatMap(r -> S3ResponseBuilder.ok(r.aggregate()));
                }
                return S3ResponseBuilder.ok(saved);
            })
            .onErrorResume(BucketNotFoundException.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage()));
    }

    private Mono<S3Object> persistComputedUploadMeasurements(S3Object saved, UploadDigest uploadDigest) {
        if (!(saved instanceof ActiveS3Object active)) {
            return Mono.just(saved);
        }
        var checksum = active.checksum() != null ? active.checksum() : ObjectChecksum.of(Set.of());
        var measured = ActiveS3Object.restoreActive(
                active.key(),
                active.storageClass(),
                active.userMetadata(),
                active.encryption(),
                checksum,
                uploadDigest.length(),
                active.createdAt(),
                active.domainEvents())
            .withEtag(uploadDigest.etag())
            .withObjectTags(active.objectTags());
        return objectService.saveObject(measured)
            .map(result -> result.aggregate());
    }

    private static MessageDigest md5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }

    private static String quoteHex(byte[] digest) {
        return "\"" + ETagComputer.toHex(digest) + "\"";
    }

    private static final class UploadDigest {
        private final MessageDigest digest = md5Digest();
        private final AtomicLong length = new AtomicLong(0);

        void update(DataBuffer buffer) {
            length.addAndGet(buffer.readableByteCount());
            try (DataBuffer.ByteBufferIterator iterator = buffer.readableByteBuffers()) {
                while (iterator.hasNext()) {
                    digest.update(iterator.next());
                }
            }
        }

        long length() {
            return length.get();
        }

        String etag() {
            return quoteHex(digest.digest());
        }
    }

    // ─────────────────────────────────────────────────────
    //  CopyObject
    // ─────────────────────────────────────────────────────

    /** PUT /{bucket}/{key} with x-amz-copy-source -- CopyObject */
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

        var effectiveStorageClass = storageClass == null || storageClass.isBlank() ? "STANDARD" : storageClass;
        var copiedObject = ActiveS3Object.create(
                targetKey,
                effectiveStorageClass,
                metadata != null ? metadata : Map.of(),
                encryption,
                checksum != null ? checksum : ObjectChecksum.of(java.util.Set.of()),
                size)
            .withEtag(sourceObject.etag() != null ? sourceObject.etag() : "\"\"");

        return objectService.saveObjectWithContent(
                copiedObject,
                objectService.getContent(sourceObject.key()),
                effectiveStorageClass)
            .flatMap(result -> {
                var savedObj = result.aggregate();
                // Use the ETag computed by the repository; fall back to source ETag or empty
                var etag = savedObj.etag() != null ? savedObj.etag()
                    : (sourceObject.etag() != null ? sourceObject.etag() : "\"\"");
                return S3ResponseBuilder.okXml(etag,
                    CopyObjectResultQuery.from(Instant.now().toString(), etag));
            });
    }

    // ─────────────────────────────────────────────────────
    //  DeleteObjects
    // ─────────────────────────────────────────────────────

    /** POST /{bucket}?delete -- DeleteObjects */
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

    /** GET /{bucket}/{key} -- GetObject */
    public Mono<ServerResponse> getObject(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        var rangeHeader = request.headers().firstHeader("Range");
        return objectService.getObjectWithContent(objectKey)
            .flatMap(oc -> {
                var obj = oc.object();
                // Evaluate conditional headers first
                return evaluateConditionals(obj, request, () -> {
                    if (rangeHeader != null && !rangeHeader.isBlank()) {
                        return oc.content().collectList()
                            .flatMap(buffers -> serveRange(obj, buffers, rangeHeader));
                    }
                    return oc.content().collectList()
                        .flatMap(buffers -> S3ResponseBuilder.okWithBody(obj, Flux.fromIterable(buffers)));
                });
            })
            .switchIfEmpty(S3WebSupport.xmlError(
                HttpStatus.NOT_FOUND, "NoSuchKey", "Key not found"))
            .onErrorResume(StorageObjectIntegrityException.class,
                e -> S3WebSupport.xmlError(HttpStatus.INTERNAL_SERVER_ERROR,
                    "XAmzChecksumMismatch",
                    "Object integrity check failed: " + e.getMessage()))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    private Mono<ServerResponse> serveRange(S3Object obj,
            java.util.List<DataBuffer> buffers, String rangeHeader) {
        var matcher = RANGE_PATTERN.matcher(rangeHeader);
        if (!matcher.matches()) {
            return S3WebSupport.xmlError(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "InvalidRange", "Invalid range format");
        }
        long start = Long.parseLong(matcher.group(1));
        long end = Long.parseLong(matcher.group(2));
        long total = obj.size();

        if (start > end || start >= total) {
            return S3WebSupport.xmlError(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "InvalidRange", "Range not satisfiable");
        }
        long effectiveEnd = Math.min(end, total - 1);

        // Collect all buffer bytes
        int totalBytes = buffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
        byte[] fullBody = new byte[totalBytes];
        int offset = 0;
        for (var buf : buffers) {
            int readable = buf.readableByteCount();
            buf.read(fullBody, offset, readable);
            offset += readable;
        }
        byte[] rangeBody = Arrays.copyOfRange(fullBody, (int) start, (int) effectiveEnd + 1);
        var rangeDataBuffer = DefaultDataBufferFactory.sharedInstance.wrap(rangeBody);

        return ServerResponse.status(HttpStatus.PARTIAL_CONTENT)
            .header("Content-Range", "bytes " + start + "-" + effectiveEnd + "/" + total)
            .header("Content-Length", String.valueOf(rangeBody.length))
            .body(BodyInserters.fromDataBuffers(Flux.just(rangeDataBuffer)));
    }

    // ─────────────────────────────────────────────────────
    //  HeadObject
    // ─────────────────────────────────────────────────────

    /** HEAD /{bucket}/{key} -- HeadObject */
    public Mono<ServerResponse> headObject(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        return objectService.getObject(objectKey)
            .flatMap(obj -> evaluateConditionals(obj, request, () -> S3ResponseBuilder.headObject(obj)))
            .switchIfEmpty(Mono.defer(() -> ServerResponse.notFound().build()))
            .onErrorResume(Throwable.class, e -> ServerResponse.notFound().build());
    }

    // ─────────────────────────────────────────────────────
    //  Conditional Request Evaluation (REQ-S3-004)
    // ─────────────────────────────────────────────────────

    /**
     * Evaluates S3 conditional request headers (If-Match, If-None-Match, If-Modified-Since,
     * If-Unmodified-Since) against the stored object. If no condition fails, delegates to
     * {@code normalResponse}.
     */
    private Mono<ServerResponse> evaluateConditionals(S3Object obj, ServerRequest request,
            Supplier<Mono<ServerResponse>> normalResponse) {
        var ifMatch = request.headers().firstHeader("If-Match");
        var ifNoneMatch = request.headers().firstHeader("If-None-Match");
        var ifModifiedSince = request.headers().firstHeader("If-Modified-Since");
        var ifUnmodifiedSince = request.headers().firstHeader("If-Unmodified-Since");

        // Use the stored ETag (hex MD5 or checksum-derived) for comparison
        var etag = obj.etag() != null ? obj.etag() : null;
        var lastModified = obj.createdAt(); // createdAt serves as last-modified

        // If-Match: condition succeeds only if ETag matches
        if (ifMatch != null && etag != null && !etagMatches(etag, ifMatch)) {
            return ServerResponse.status(HttpStatus.PRECONDITION_FAILED).build();
        }
        // If-None-Match: condition fails (→ 304) if ETag matches
        if (ifNoneMatch != null && etag != null && etagMatches(etag, ifNoneMatch)) {
            return ServerResponse.status(HttpStatus.NOT_MODIFIED).build();
        }
        // If-Modified-Since: return 304 if the object was NOT modified after the given date
        if (ifModifiedSince != null && lastModified != null) {
            var since = parseHttpDate(ifModifiedSince);
            if (since != null && !lastModified.isAfter(since)) {
                return ServerResponse.status(HttpStatus.NOT_MODIFIED).build();
            }
        }
        // If-Unmodified-Since: return 412 if the object WAS modified after the given date
        if (ifUnmodifiedSince != null && lastModified != null) {
            var since = parseHttpDate(ifUnmodifiedSince);
            if (since != null && lastModified.isAfter(since)) {
                return ServerResponse.status(HttpStatus.PRECONDITION_FAILED).build();
            }
        }
        return normalResponse.get();
    }

    private boolean etagMatches(String etag, String headerValue) {
        if ("*".equals(headerValue)) {
            return true;
        }
        var clean1 = etag.replace("\"", "");
        var clean2 = headerValue.replace("\"", "");
        return clean1.equals(clean2);
    }

    private ZonedDateTime parseHttpDate(String headerValue) {
        try {
            return ZonedDateTime.parse(headerValue, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────
    //  RenameObject
    // ─────────────────────────────────────────────────────

    /** PUT /{bucket}/{key}?rename -- RenameObject */
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
                        metadata != null ? metadata : Map.of(),
                        objectService.getContent(sourceObject.key()))
                    .then(objectService.deleteObject(sourceKey))
                    .then(S3ResponseBuilder.okWithEtag("\"\""));
            })
            .switchIfEmpty(S3WebSupport.xmlError(
                HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  GetObjectTorrent
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}/{key}?torrent -- GetObjectTorrent */
    public Mono<ServerResponse> getObjectTorrent(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        return objectService.getObjectTorrent(objectKey)
            .flatMap(torrentData -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(BodyInserters.fromDataBuffers(torrentData)))
            .switchIfEmpty(S3WebSupport.xmlError(
                HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  RestoreObject
    // ─────────────────────────────────────────────────────

    /** POST /{bucket}/{key}?restore -- RestoreObject */
    public Mono<ServerResponse> restoreObject(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        return objectService.getObject(objectKey)
            .flatMap(obj -> request.bodyToMono(RestoreObjectCommand.class)
                .flatMap(cmd -> objectService.saveObject(obj)
                    .then(ServerResponse.ok().build()))
                .switchIfEmpty(Mono.defer(() -> ServerResponse.ok().build())))
            .switchIfEmpty(S3WebSupport.xmlError(
                HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"))
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  DeleteObject
    // ─────────────────────────────────────────────────────

    /** DELETE /{bucket}/{key} -- DeleteObject */
    public Mono<ServerResponse> deleteObject(ServerRequest request) {
        var objectKey = S3RequestExtractor.extractObjectKey(request);
        return objectService.deleteObject(objectKey)
            .then(ServerResponse.noContent().build())
            .onErrorResume(Throwable.class, e -> ServerResponse.noContent().build());
    }

    // ─────────────────────────────────────────────────────
    //  SelectObjectContent
    // ─────────────────────────────────────────────────────

    /** POST /{bucket}/{key}?select -- SelectObjectContent */
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
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  WriteGetObjectResponse
    // ─────────────────────────────────────────────────────

    /** PUT /{bucket}/{key}?x-id=WriteGetObjectResponse -- WriteGetObjectResponse */
    public Mono<ServerResponse> writeGetObjectResponse(ServerRequest request) {
        var requestId = request.headers().firstHeader(
            S3Header.X_AMZ_REQUEST_ID.headerName());

        return request.bodyToFlux(DataBuffer.class)
            .then()
            .then(ServerResponse.ok()
                .header(S3Header.X_AMZ_REQUEST_ID.headerName(),
                    requestId != null ? requestId : "")
                .build())
            .onErrorResume(Throwable.class,
                e -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }
}
