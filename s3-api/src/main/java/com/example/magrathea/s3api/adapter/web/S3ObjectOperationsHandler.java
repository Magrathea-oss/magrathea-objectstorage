package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.dto.PutObjectCommand;
import com.example.magrathea.objectstorage.application.service.DefaultS3ObjectContent;
import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.objectstorage.application.service.ObjectService;
import com.example.magrathea.s3api.adapter.web.xml.S3XmlResponses;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Object-context S3 operations.
 * Owns single-object CRUD, object copy, and multi-object deletion.
 */
public class S3ObjectOperationsHandler {

    private static final Pattern DELETE_OBJECT_KEY_PATTERN = Pattern.compile("<Key>([^<]+)</Key>");
    private static final DefaultDataBufferFactory DATA_BUFFER_FACTORY = new DefaultDataBufferFactory();

    private final BucketService bucketService;
    private final ObjectService objectService;

    public S3ObjectOperationsHandler(BucketService bucketService, ObjectService objectService) {
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
        var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
        if (bucketInfo.isEmpty()) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                .bodyValue(S3XmlResponses.Error.from("NoSuchBucket", "Bucket not found"));
        }
        var contentLength = request.headers().contentLength().orElse(0L);
        var storageClass = request.headers().firstHeader("x-amz-storage-class");
        var cmd = new PutObjectCommand(bucketInfo.get().id(), key,
            contentType, null, null, contentLength, storageClass, Map.of());
        return Mono.fromCallable(() -> objectService.putObject(cmd, request.bodyToFlux(DataBuffer.class)))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(ignored -> ServerResponse.ok()
                .header("ETag", "\"\"")
                .build());
    }

    /** PUT /{bucket}/{key} with x-amz-copy-source — CopyObject */
    public Mono<ServerResponse> copyObject(ServerRequest request) {
        var targetBucket = request.pathVariable("bucket");
        var targetKey = request.pathVariable("key");
        var source = S3WebSupport.decodeCopySource(request.headers().firstHeader("x-amz-copy-source"));
        if (source.isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidArgument", "Invalid copy source");
        }
        var sourceBucket = source.get()[0];
        var sourceKey = source.get()[1];
        var sourceBucketInfo = S3WebSupport.findBucket(bucketService, sourceBucket);
        var targetBucketInfo = S3WebSupport.findBucket(bucketService, targetBucket);
        if (sourceBucketInfo.isEmpty() || targetBucketInfo.isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        var sourceObject = S3WebSupport.findObject(objectService, sourceBucketInfo.get(), sourceKey);
        if (sourceObject.isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object not found");
        }
        return Mono.fromFuture(objectService.getContent(sourceObject.get().id()))
            .flatMap(content -> {
                if (content.isEmpty()) {
                    return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchKey", "Object content not found");
                }
                if (!(content.get() instanceof DefaultS3ObjectContent sourceContent)) {
                    return S3WebSupport.xmlError(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", "Unsupported content implementation");
                }
                return DataBufferUtils.join(sourceContent.content())
                    .flatMap(dataBuffer -> {
                        try {
                            var bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            var sourceStorageClass = sourceObject.get().storageClass() != null
                                ? sourceObject.get().storageClass()
                                : null;
                            var cmd = new PutObjectCommand(targetBucketInfo.get().id(), targetKey,
                                sourceObject.get().contentType(), null, null, bytes.length, sourceStorageClass, Map.of());
                            return Mono.fromCallable(() -> objectService.putObject(cmd, Flux.just(DATA_BUFFER_FACTORY.wrap(bytes))))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(ignored -> ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_XML)
                                    .header("ETag", "\"\"")
                                    .bodyValue(new S3XmlResponses.CopyObjectResult(Instant.now().toString(), "\"\"")));
                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    });
            });
    }

    /** POST /{bucket}?delete — DeleteObjects */
    public Mono<ServerResponse> deleteObjects(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
        if (bucketInfo.isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> {
                var keys = DELETE_OBJECT_KEY_PATTERN.matcher(body).results()
                    .map(match -> match.group(1))
                    .toList();
                var deleted = keys.stream()
                    .peek(key -> S3WebSupport.findObject(objectService, bucketInfo.get(), key)
                        .ifPresent(object -> objectService.deleteObject(object.id())))
                    .map(S3XmlResponses.DeletedEntry::new)
                    .toList();
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(new S3XmlResponses.DeleteResult(deleted));
            });
    }

    /** GET /{bucket}/{key} — GetObject */
    public Mono<ServerResponse> getObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
        if (bucketInfo.isEmpty()) {
            return ServerResponse.notFound().build();
        }
        var object = S3WebSupport.findObject(objectService, bucketInfo.get(), key);
        if (object.isEmpty()) {
            return ServerResponse.notFound().build();
        }
        var obj = object.get();
        return Mono.fromFuture(objectService.getContent(obj.id()))
            .flatMap(content -> {
                if (content.isEmpty()) {
                    return ServerResponse.notFound().build();
                }
                if (!(content.get() instanceof DefaultS3ObjectContent objectContent)) {
                    return S3WebSupport.xmlError(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", "Unsupported content implementation");
                }
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("ETag", "\"\"")
                    .body(BodyInserters.fromDataBuffers(objectContent.content()));
            });
    }

    /** HEAD /{bucket}/{key} — HeadObject */
    public Mono<ServerResponse> headObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
        if (bucketInfo.isEmpty()) {
            return ServerResponse.notFound().build();
        }
        var found = S3WebSupport.findObject(objectService, bucketInfo.get(), key).isPresent();
        return found
            ? ServerResponse.ok().build()
            : ServerResponse.notFound().build();
    }

    /** DELETE /{bucket}/{key} — DeleteObject */
    public Mono<ServerResponse> deleteObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
        if (bucketInfo.isEmpty()) {
            return ServerResponse.notFound().build();
        }
        S3WebSupport.findObject(objectService, bucketInfo.get(), key)
            .ifPresent(object -> objectService.deleteObject(object.id()));
        return ServerResponse.noContent().build();
    }
}
