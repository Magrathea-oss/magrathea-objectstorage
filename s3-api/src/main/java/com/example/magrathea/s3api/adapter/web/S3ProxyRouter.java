package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.dto.CreateBucketCommand;
import com.example.magrathea.objectstorage.application.dto.ObjectResponse;
import com.example.magrathea.objectstorage.application.dto.PutObjectCommand;
import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.objectstorage.application.service.ObjectService;
import com.example.magrathea.s3api.adapter.web.xml.S3XmlResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * S3-compatible RouterFunction — functional WebFlux style.
 * All HTTP endpoints use RouterFunction, never @Controller.
 * XML serialization: Jackson 3 XmlMapper with annotated record objects.
 * Content store delegated to ObjectService — no direct infrastructure dependency.
 */
public class S3ProxyRouter {

    private final BucketService bucketService;
    private final ObjectService objectService;

    public S3ProxyRouter(BucketService bucketService, ObjectService objectService) {
        this.bucketService = bucketService;
        this.objectService = objectService;
    }

    public RouterFunction<ServerResponse> s3Routes() {
        return RouterFunctions
            .route()
            .GET("/", req -> acceptXml(req), this::listBucketsXml)
            .GET("/", req -> acceptJson(req), this::listBucketsJson)
            .PUT("/{bucket}", this::createBucket)
            .HEAD("/{bucket}", this::headBucket)
            .GET("/{bucket}", req -> acceptXml(req), this::listObjectsXml)
            .PUT("/{bucket}/{key}", this::putObject)
            .GET("/{bucket}/{key}", this::getObject)
            .HEAD("/{bucket}/{key}", this::headObject)
            .DELETE("/{bucket}/{key}", this::deleteObject)
            .DELETE("/{bucket}", this::deleteBucket)
            .build();
    }

    private static boolean acceptXml(ServerRequest req) {
        var accept = req.headers().accept();
        if (accept.isEmpty()) {
            return true;
        }
        return accept.stream()
            .anyMatch(m -> m.equals(MediaType.ALL)
                || m.equals(MediaType.APPLICATION_XML)
                || m.includes(MediaType.APPLICATION_XML));
    }

    private static boolean acceptJson(ServerRequest req) {
        return req.headers().accept().stream()
            .anyMatch(m -> m.equals(MediaType.APPLICATION_JSON));
    }

    /** GET / — ListBuckets (XML) */
    private Mono<ServerResponse> listBucketsXml(ServerRequest request) {
        var buckets = bucketService.findAll();
        var result = S3XmlResponses.ListAllMyBucketsResult.from(buckets);
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(result);
    }

    /** GET / — ListBuckets (JSON) */
    private Mono<ServerResponse> listBucketsJson(ServerRequest request) {
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(bucketService.findAll());
    }

    /** PUT /{bucket} — CreateBucket */
    private Mono<ServerResponse> createBucket(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var exists = bucketService.findAll().stream()
            .anyMatch(b -> b.name().equals(bucketName));
        if (exists) {
            return ServerResponse.status(HttpStatus.CONFLICT)
                .bodyValue(S3XmlResponses.Error.from("BucketAlreadyExists", bucketName));
        }
        var cmd = new CreateBucketCommand(bucketName, "us-east-1", "STANDARD");
        bucketService.createBucket(cmd);
        return ServerResponse.ok()
            .header("Location", "/" + bucketName)
            .build();
    }

    /** HEAD /{bucket} — HeadBucket */
    private Mono<ServerResponse> headBucket(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var found = bucketService.findAll().stream()
            .anyMatch(b -> b.name().equals(bucket));
        return found
            ? ServerResponse.ok().build()
            : ServerResponse.notFound().build();
    }

    /** GET /{bucket} — ListObjects (XML) */
    private Mono<ServerResponse> listObjectsXml(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var bucketInfo = bucketService.findAll().stream()
            .filter(b -> b.name().equals(bucket))
            .findFirst();
        if (bucketInfo.isEmpty()) {
            return ServerResponse.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(S3XmlResponses.Error.from("NoSuchBucket", "Bucket not found"));
        }
        var objects = objectService.findByBucket(bucketInfo.get().id());
        var result = S3XmlResponses.ListBucketResult.from(bucket, objects);
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(result);
    }

    /** PUT /{bucket}/{key} — PutObject */
    private Mono<ServerResponse> putObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var contentType = request.headers().contentType()
            .map(MediaType::toString)
            .orElse("application/octet-stream");
        return request.bodyToMono(byte[].class).flatMap(bytes -> {
            var bucketInfo = bucketService.findAll().stream()
                .filter(b -> b.name().equals(bucket))
                .findFirst();
            if (bucketInfo.isEmpty()) {
                return ServerResponse.status(HttpStatus.NOT_FOUND)
                    .bodyValue(S3XmlResponses.Error.from("NoSuchBucket", "Bucket not found"));
            }
            var cmd = new PutObjectCommand(bucketInfo.get().id(), key,
                contentType, null, null, bytes.length, Map.of());
            var result = objectService.putObject(cmd);
            objectService.storeContent(result.id(), bytes);
            return ServerResponse.ok()
                .header("ETag", "\"\"")
                .build();
        });
    }

    /** GET /{bucket}/{key} — GetObject */
    private Mono<ServerResponse> getObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var bucketInfo = bucketService.findAll().stream()
            .filter(b -> b.name().equals(bucket))
            .findFirst();
        if (bucketInfo.isEmpty()) {
            return ServerResponse.notFound().build();
        }
        var objects = objectService.findByBucket(bucketInfo.get().id()).stream()
            .filter(o -> o.key().equals(key))
            .findFirst();
        if (objects.isEmpty()) {
            return ServerResponse.notFound().build();
        }
        var obj = objects.get();
        return Mono.fromFuture(objectService.getContent(obj.id()))
            .flatMap(contentBytes -> {
                var body = contentBytes
                    .orElseThrow(() -> new RuntimeException("Content not found: " + obj.id()));
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("ETag", "\"\"")
                    .bodyValue(body);
            });
    }

    /** HEAD /{bucket}/{key} — HeadObject */
    private Mono<ServerResponse> headObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var bucketInfo = bucketService.findAll().stream()
            .filter(b -> b.name().equals(bucket))
            .findFirst();
        if (bucketInfo.isEmpty()) {
            return ServerResponse.notFound().build();
        }
        var found = objectService.findByBucket(bucketInfo.get().id()).stream()
            .anyMatch(o -> o.key().equals(key));
        return found
            ? ServerResponse.ok().build()
            : ServerResponse.notFound().build();
    }

    /** DELETE /{bucket}/{key} — DeleteObject */
    private Mono<ServerResponse> deleteObject(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = request.pathVariable("key");
        var bucketInfo = bucketService.findAll().stream()
            .filter(b -> b.name().equals(bucket))
            .findFirst();
        if (bucketInfo.isEmpty()) {
            return ServerResponse.notFound().build();
        }
        var objects = objectService.findByBucket(bucketInfo.get().id()).stream()
            .filter(o -> o.key().equals(key))
            .findFirst();
        objects.ifPresent(o -> objectService.deleteObject(o.id()));
        return ServerResponse.noContent().build();
    }

    /** DELETE /{bucket} — DeleteBucket */
    private Mono<ServerResponse> deleteBucket(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var bucketInfo = bucketService.findAll().stream()
            .filter(b -> b.name().equals(bucket))
            .findFirst();
        if (bucketInfo.isEmpty()) {
            return ServerResponse.notFound().build();
        }
        bucketService.deleteBucket(bucketInfo.get().id());
        return ServerResponse.noContent().build();
    }
}
