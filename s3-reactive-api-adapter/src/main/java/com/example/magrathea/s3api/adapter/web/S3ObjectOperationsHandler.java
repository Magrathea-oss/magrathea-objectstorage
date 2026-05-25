package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.domain.model.Bucket;
import com.example.magrathea.objectstorage.domain.model.S3Object;
import com.example.magrathea.objectstorage.domain.valueobject.ContentDescriptor;
import com.example.magrathea.objectstorage.domain.valueobject.ObjectKey;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.reactive.application.service.ReactiveObjectService;
import com.example.magrathea.s3api.dto.command.DeleteObjectsCommand;
import com.example.magrathea.s3api.dto.query.CopyObjectResultQuery;
import com.example.magrathea.s3api.dto.query.DeleteResultQuery;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
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
import java.util.UUID;

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
                var objectId = S3Object.ObjectId.generate();
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
                        var descriptor = ContentDescriptor.of(bytes.length, "", UUID.randomUUID().toString());
                        var withContent = s3Object.withContent(descriptor);
                        return objectService.saveObject(withContent)
                            .then(ServerResponse.ok()
                                .header("ETag", "\"\"")
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
                var bucketKey = S3Object.ObjectId.BucketKey.of(sourceBucket.id(), ObjectKey.of(sourceKey));
                return objectService.findByBucketAndKey(bucketKey)
                    .flatMap(sourceObject -> {
                        var contentDescriptor = sourceObject.content();
                        var srcStorageClass = sourceObject.storageClass();
                        var targetObjectId = S3Object.ObjectId.generate();
                        var targetObjectKey = ObjectKey.of(targetKey);
                        var s3Object = S3Object.create(targetObjectId, targetBucketInfo.id(), targetObjectKey,
                            sourceObject.contentType(), null, null, contentDescriptor.size(), Map.of());
                        var withContent = s3Object.withContent(contentDescriptor);
                        return objectService.saveObject(withContent)
                            .then(ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_XML)
                                .header("ETag", "\"\"")
                                .bodyValue(CopyObjectResultQuery.from(Instant.now().toString(), "\"\"")));
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
                        .flatMap(obj -> {
                            var bucketKey = S3Object.ObjectId.BucketKey.of(b.id(), ObjectKey.of(obj.key()));
                            return objectService.findByBucketAndKey(bucketKey)
                                .flatMap(object -> objectService.deleteObject(object));
                        })
                        .then();
                    return Mono.zip(xmlMono, deleteAll)
                        .flatMap(tuple -> {
                            DataBuffer buf = DATA_BUFFER_FACTORY.wrap(
                                tuple.getT1().xmlContent().getBytes(StandardCharsets.UTF_8));
                            return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_XML)
                                .body(BodyInserters.fromDataBuffers(Flux.just(buf)));
                        });
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}/{key} — GetObject */
    public Mono<ServerResponse> getObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var bucketKey = S3Object.ObjectId.BucketKey.of(b.id(), ObjectKey.of(key));
                return objectService.findByBucketAndKey(bucketKey)
                    .flatMap(obj -> {
                        var contentFlux = objectService.getContent(obj.id());
                        // Convert Flux<Byte> to Flux<DataBuffer> for response body
                        var dataBufferFlux = contentFlux.buffer(8192)
                            .map(bytes -> {
                                var arr = new byte[bytes.size()];
                                for (int i = 0; i < bytes.size(); i++) {
                                    arr[i] = bytes.get(i);
                                }
                                return DATA_BUFFER_FACTORY.wrap(arr);
                            });
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .header("ETag", "\"\"")
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
                var bucketKey = S3Object.ObjectId.BucketKey.of(b.id(), ObjectKey.of(key));
                return objectService.findByBucketAndKey(bucketKey)
                    .flatMap(obj -> ServerResponse.ok().build())
                    .switchIfEmpty(Mono.defer(() -> ServerResponse.notFound().build()));
            })
            .switchIfEmpty(Mono.defer(() -> ServerResponse.notFound().build()));
    }

    /** DELETE /{bucket}/{key} — DeleteObject */
    public Mono<ServerResponse> deleteObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var bucketKey = S3Object.ObjectId.BucketKey.of(b.id(), ObjectKey.of(key));
                return objectService.findByBucketAndKey(bucketKey)
                    .flatMap(object -> objectService.deleteObject(object)
                        .then(ServerResponse.noContent().build()))
                    .switchIfEmpty(Mono.defer(() -> ServerResponse.noContent().build()));
            })
            .switchIfEmpty(Mono.defer(() -> ServerResponse.noContent().build()));
    }
}
