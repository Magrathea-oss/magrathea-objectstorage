package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.reactive.application.service.ReactiveObjectService;
import com.example.magrathea.s3api.dto.command.DeleteObjectsCommand;
import com.example.magrathea.s3api.dto.command.RestoreObjectCommand;
import com.example.magrathea.s3api.dto.command.SelectObjectContentCommand;
import com.example.magrathea.s3api.dto.query.CopyObjectResultQuery;
import com.example.magrathea.s3api.dto.query.DeleteResultQuery;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
import com.example.magrathea.s3api.dto.query.SelectObjectContentQuery;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.util.DigestUtils;

/**
 * Object-context S3 operations.
 * Owns single-object CRUD, object copy, and multi-object deletion.
 * Uses Jackson XML codec for request body deserialization.
 * All content is handled via DataBuffer/Flux<Byte> — no byte[] in DTOs.
 */
public class S3ObjectOperationsHandler {

    private static final DataBufferFactory DATA_BUFFER_FACTORY = new DefaultDataBufferFactory();

    private final ReactiveBucketService bucketService;
    private final ReactiveObjectService objectService;
    private final Map<S3Object.Id, byte[]> contentStore = new ConcurrentHashMap<>();

    public S3ObjectOperationsHandler(ReactiveBucketService bucketService,
                                      ReactiveObjectService objectService) {
        this.bucketService = bucketService;
        this.objectService = objectService;
    }

    /** PUT /{bucket}/{key} — PutObject */
    public Mono<ServerResponse> putObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var contentType = request.headers().contentType()
            .map(MediaType::toString)
            .orElse("application/octet-stream");
        var contentLength = request.headers().contentLength().orElse(0L);
        var storageClass = request.headers().firstHeader("x-amz-storage-class");

        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var objectId = S3Object.Id.generate();
                var objectKey = ObjectKey.of(key);
                var s3Object = S3Object.create(objectId, b.id(), objectKey,
                    contentType, null, null, contentLength, Map.of());

                // Accumulate DataBuffer bytes and create a ContentDescriptor
                // Run blocking conversion off the event loop
                return request.bodyToFlux(DataBuffer.class)
                    .reduceWith(
                        () -> new byte[0],
                        (acc, buf) -> {
                            var bytes = new byte[acc.length + buf.readableByteCount()];
                            System.arraycopy(acc, 0, bytes, 0, acc.length);
                            buf.read(bytes, acc.length, buf.readableByteCount());
                            return bytes;
                        }
                    )
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(bytes -> {
                        var md5Hash = DigestUtils.md5DigestAsHex(bytes);
                        var descriptor = ContentDescriptor.of(bytes.length, md5Hash, objectId.value());
                        var withContent = s3Object.withContent(descriptor);
                        contentStore.put(objectId, bytes.clone());
                        return objectService.saveObject(withContent)
                            .then(ServerResponse.ok()
                                .header("ETag", "\"" + md5Hash + "\"")
                                .build());
                    });
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND)
                .bodyValue(ErrorQuery.from("NoSuchBucket", "Bucket not found")));
    }

    /** PUT /{bucket}/{key} with x-amz-copy-source — CopyObject */
    public Mono<ServerResponse> copyObject(ServerRequest request) {
        var targetBucket = request.pathVariable("bucket");
        var targetKey = request.pathVariable("key");
        var source = S3WebSupport.decodeCopySource(request.headers().firstHeader("x-amz-copy-source"));
        if (source.isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidArgument", "Invalid copy source");
        }
        var sourceBucketName = source.get()[0];
        var sourceKey = source.get()[1];

        return Mono.zip(
                bucketService.findByName(sourceBucketName),
                bucketService.findByName(targetBucket)
            )
            .flatMap(tuple -> {
                var sourceBucket = tuple.getT1();
                var targetBucketInfo = tuple.getT2();
                return objectService.findByBucketAndKey(sourceBucket.id(), ObjectKey.of(sourceKey))
                    .flatMap(sourceObject -> {
                        var contentDescriptor = sourceObject.content();
                        var targetObjectId = S3Object.Id.generate();
                        var targetObjectKey = ObjectKey.of(targetKey);
                        var size = contentDescriptor != null ? contentDescriptor.size() : sourceObject.size();
                        var s3Object = S3Object.create(targetObjectId, targetBucketInfo.id(), targetObjectKey,
                            sourceObject.contentType(), null, null, size, Map.of());
                        var copiedDescriptor = contentDescriptor != null
                            ? ContentDescriptor.of(contentDescriptor.size(), contentDescriptor.md5Hash(), targetObjectId.value())
                            : null;
                        var withContent = copiedDescriptor != null ? s3Object.withContent(copiedDescriptor) : s3Object;
                        var sourceBytes = contentStore.get(sourceObject.id());
                        if (sourceBytes != null) {
                            contentStore.put(targetObjectId, sourceBytes.clone());
                        }
                        var etag = copiedDescriptor != null && copiedDescriptor.md5Hash() != null
                            ? "\"" + copiedDescriptor.md5Hash() + "\""
                            : "\"\"";
                        return objectService.saveObject(withContent)
                            .then(ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_XML)
                                .header("ETag", etag)
                                .bodyValue(CopyObjectResultQuery.from(Instant.now().toString(), etag)));
                    })
                    .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** POST /{bucket}?delete — DeleteObjects */
    public Mono<ServerResponse> deleteObjects(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(DeleteObjectsCommand.class)
                .flatMap(cmd -> {
                    Flux<String> keys = Flux.fromIterable(cmd.objects()).map(obj -> obj.key());
                    // Build DeleteResult XML reactively from keys Flux
                    Mono<DeleteResultQuery> xmlMono = DeleteResultQuery.from(keys);
                    // Delete all objects in parallel
                    Mono<Void> deleteAll = Flux.fromIterable(cmd.objects())
                        .flatMap(obj -> objectService.findByBucketAndKey(b.id(), ObjectKey.of(obj.key()))
                            .flatMap(object -> objectService.deleteObject(object)
                                .doOnSuccess(ignored -> contentStore.remove(object.id()))))
                        .then();
                    return deleteAll.then(xmlMono)
                        .flatMap(result -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_XML)
                            .bodyValue(result));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}/{key} — GetObject */
    public Mono<ServerResponse> getObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> {
                        var storedBytes = contentStore.get(obj.id());
                        var dataBufferFlux = storedBytes != null
                            ? Flux.just(DATA_BUFFER_FACTORY.wrap(storedBytes))
                            : objectService.getContent(obj.id())
                                .buffer(8192)
                                .map(bytes -> {
                                    var arr = new byte[bytes.size()];
                                    for (int i = 0; i < bytes.size(); i++) {
                                        arr[i] = bytes.get(i);
                                    }
                                    return DATA_BUFFER_FACTORY.wrap(arr);
                                });
                        var contentType = obj.contentType() != null ? obj.contentType() : "application/octet-stream";
                        var etag = obj.contentDescriptor() != null && obj.contentDescriptor().md5Hash() != null
                            ? "\"" + obj.contentDescriptor().md5Hash() + "\""
                            : "\"\"";
                        return ServerResponse.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header("ETag", etag)
                            .body(BodyInserters.fromDataBuffers(dataBufferFlux));
                    })
                    .switchIfEmpty(Mono.defer(() -> ServerResponse.notFound().build()));
            })
            .switchIfEmpty(Mono.defer(() -> ServerResponse.notFound().build()));
    }

    /** HEAD /{bucket}/{key} — HeadObject */
    public Mono<ServerResponse> headObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(obj -> ServerResponse.ok().build())
                    .switchIfEmpty(Mono.defer(() -> ServerResponse.notFound().build()));
            })
            .switchIfEmpty(Mono.defer(() -> ServerResponse.notFound().build()));
    }

    /** PUT /{bucket}/{key}?rename — RenameObject */
    public Mono<ServerResponse> renameObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var sourceKey = request.pathVariable("key");
        var destinationKey = request.headers().firstHeader("x-amz-rename-destination");
        if (destinationKey == null || destinationKey.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidArgument",
                "x-amz-rename-destination header is required");
        }

        return bucketService.findByName(bucket)
            .flatMap(b -> objectService.findByBucketAndKey(b.id(), ObjectKey.of(sourceKey))
                .flatMap(sourceObject -> {
                    // Create a new object with the destination key, copying source metadata
                    var targetObjectId = S3Object.Id.generate();
                    var targetObjectKey = ObjectKey.of(destinationKey);
                    var contentType = sourceObject.contentType();
                    var contentDisposition = sourceObject.contentDisposition();
                    var contentEncoding = sourceObject.contentEncoding();
                    var size = sourceObject.size();
                    var metadata = sourceObject.metadata();

                    var s3Object = S3Object.create(targetObjectId, b.id(), targetObjectKey,
                        contentType, contentDisposition, contentEncoding, size, metadata);

                    var contentDescriptor = sourceObject.content();
                    var renamedDescriptor = contentDescriptor != null
                        ? ContentDescriptor.of(contentDescriptor.size(), contentDescriptor.md5Hash(), targetObjectId.value())
                        : null;
                    var withContent = renamedDescriptor != null
                        ? s3Object.withContent(renamedDescriptor)
                        : s3Object;
                    var sourceBytes = contentStore.get(sourceObject.id());
                    if (sourceBytes != null) {
                        contentStore.put(targetObjectId, sourceBytes.clone());
                    }

                    var etag = renamedDescriptor != null && renamedDescriptor.md5Hash() != null
                        ? "\"" + renamedDescriptor.md5Hash() + "\""
                        : "\"\"";

                    // Save new object and delete original
                    return objectService.saveObject(withContent)
                        .then(objectService.deleteObject(sourceObject)
                            .doOnSuccess(ignored -> contentStore.remove(sourceObject.id())))
                        .then(ServerResponse.ok()
                            .header("ETag", etag)
                            .build());
                })
                .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"))
            )
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}/{key}?torrent — GetObjectTorrent */
    public Mono<ServerResponse> getObjectTorrent(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucket)
            .flatMap(b -> objectService.getObjectTorrent(bucket, ObjectKey.of(key))
                .flatMap(torrentData -> ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(BodyInserters.fromDataBuffers(torrentData)))
                .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found")))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** POST /{bucket}/{key}?restore — RestoreObject */
    public Mono<ServerResponse> restoreObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucket)
            .flatMap(b -> objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                .flatMap(obj -> request.bodyToMono(RestoreObjectCommand.class)
                    .flatMap(cmd -> {
                        var tier = cmd.restoreRequest() != null
                            ? cmd.restoreRequest().tier()
                            : cmd.restoreRequest() != null && cmd.restoreRequest().glacierJobParameters() != null
                                ? cmd.restoreRequest().glacierJobParameters().tier()
                                : "Standard";
                        var days = cmd.restoreRequest() != null
                            ? cmd.restoreRequest().days()
                            : 0;
                        // Update storage class to indicate restoration
                        var updatedObject = obj.withStorageClass("%s_RESTORE_%s_%d".formatted(tier, tier, days));
                        return objectService.saveObject(updatedObject)
                            .then(ServerResponse.ok().build());
                    })
                    .switchIfEmpty(Mono.defer(() -> ServerResponse.ok().build())))
                .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found"))
            )
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}/{key} — DeleteObject */
    public Mono<ServerResponse> deleteObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                return objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                    .flatMap(object -> objectService.deleteObject(object)
                        .doOnSuccess(ignored -> contentStore.remove(object.id()))
                        .then(ServerResponse.noContent().build()))
                    .switchIfEmpty(Mono.defer(() -> ServerResponse.noContent().build()));
            })
            .switchIfEmpty(Mono.defer(() -> ServerResponse.noContent().build()));
    }

    // ─────────────────────────────────────────────────────
    //  SelectObjectContent
    // ─────────────────────────────────────────────────────

    /**
     * POST /{bucket}/{key}?select — SelectObjectContent
     * <p>
     * S3 Select API: receives a SQL expression in XML body and returns CSV/JSON content.
     * Simplified placeholder implementation that acknowledges the request.
     */
    public Mono<ServerResponse> selectObjectContent(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucket)
            .flatMap(b -> objectService.findByBucketAndKey(b.id(), ObjectKey.of(key))
                .flatMap(obj -> request.bodyToMono(SelectObjectContentCommand.class)
                    .flatMap(cmd -> {
                        var expression = cmd.expression() != null ? cmd.expression() : "";
                        var result = SelectObjectContentQuery.placeholder(expression);
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_XML)
                            .bodyValue(result);
                    })
                    .switchIfEmpty(Mono.defer(() -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(SelectObjectContentQuery.placeholder("")))))
                .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found")))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  WriteGetObjectResponse
    // ─────────────────────────────────────────────────────

    /**
     * PUT /{bucket}/{key}?x-id=WriteGetObjectResponse — WriteGetObjectResponse
     * <p>
     * S3 Object Lambda API: accepts a binary response body and stores it.
     * Simplified implementation that acknowledges receipt of the response data.
     */
    public Mono<ServerResponse> writeGetObjectResponse(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var requestId = request.headers().firstHeader("x-amz-request-id");
        var statusCode = request.headers().firstHeader("x-amz-fwd-status");
        var errorMessage = request.headers().firstHeader("x-amz-fwd-error-message");

        return bucketService.findByName(bucket)
            .flatMap(b -> {
                // Read the binary body as Flux<DataBuffer> and accumulate to acknowledge receipt
                return request.bodyToFlux(DataBuffer.class)
                    .reduceWith(
                        () -> new byte[0],
                        (acc, buf) -> {
                            var bytes = new byte[acc.length + buf.readableByteCount()];
                            System.arraycopy(acc, 0, bytes, 0, acc.length);
                            buf.read(bytes, acc.length, buf.readableByteCount());
                            return bytes;
                        }
                    )
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(bytes -> ServerResponse.ok()
                        .header("x-amz-request-id", requestId != null ? requestId : "")
                        .build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    public void resetInMemoryContent() {
        contentStore.clear();
    }
}
