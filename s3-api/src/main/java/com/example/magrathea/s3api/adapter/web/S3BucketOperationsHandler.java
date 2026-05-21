package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.dto.CreateBucketCommand;
import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.objectstorage.application.service.ObjectService;
import com.example.magrathea.s3api.adapter.web.xml.S3XmlResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Bucket-context S3 operations.
 * Owns bucket lifecycle, bucket discovery/configuration, and bucket-level object listings.
 */
public class S3BucketOperationsHandler {

    private final BucketService bucketService;
    private final ObjectService objectService;

    public S3BucketOperationsHandler(BucketService bucketService, ObjectService objectService) {
        this.bucketService = bucketService;
        this.objectService = objectService;
    }

    /** GET / — ListBuckets (XML) */
    public Mono<ServerResponse> listBucketsXml(ServerRequest request) {
        var buckets = bucketService.findAll();
        var result = S3XmlResponses.ListAllMyBucketsResult.from(buckets);
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(result);
    }

    /** GET / — ListBuckets (JSON, test convenience only) */
    public Mono<ServerResponse> listBucketsJson(ServerRequest request) {
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(bucketService.findAll());
    }

    /** PUT /{bucket} — CreateBucket */
    public Mono<ServerResponse> createBucket(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        var exists = S3WebSupport.findBucket(bucketService, bucketName).isPresent();
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
    public Mono<ServerResponse> headBucket(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var found = S3WebSupport.findBucket(bucketService, bucket).isPresent();
        return found
            ? ServerResponse.ok().build()
            : ServerResponse.notFound().build();
    }

    /** GET /{bucket}?location — GetBucketLocation */
    public Mono<ServerResponse> getBucketLocation(ServerRequest request) {
        return S3WebSupport.findBucket(bucketService, request.pathVariable("bucket"))
            .map(bucket -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new S3XmlResponses.LocationConstraint(bucket.region())))
            .orElseGet(() -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}?versioning — GetBucketVersioning */
    public Mono<ServerResponse> getBucketVersioning(ServerRequest request) {
        return S3WebSupport.findBucket(bucketService, request.pathVariable("bucket"))
            .map(bucket -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(S3XmlResponses.VersioningConfiguration.from(bucket.versioningEnabled())))
            .orElseGet(() -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?versioning — PutBucketVersioning */
    public Mono<ServerResponse> putBucketVersioning(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> {
                var enabled = body.contains("<Status>Enabled</Status>") || body.contains("Status=Enabled");
                bucketService.putBucketVersioning(bucket, enabled);
                return ServerResponse.ok().build();
            });
    }

    /** GET /{bucket} — ListObjects (XML) */
    public Mono<ServerResponse> listObjectsXml(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
        if (bucketInfo.isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        var objects = objectService.findByBucket(bucketInfo.get().id());
        var result = S3XmlResponses.ListBucketResult.from(bucket, objects);
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(result);
    }

    /** GET /{bucket}?list-type=2 — ListObjectsV2 */
    public Mono<ServerResponse> listObjectsV2Xml(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
        if (bucketInfo.isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        var objects = objectService.findByBucket(bucketInfo.get().id());
        var result = S3XmlResponses.ListBucketV2Result.from(bucket, objects);
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(result);
    }

    /** GET /{bucket}?versions — ListObjectVersions */
    public Mono<ServerResponse> listObjectVersions(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
        if (bucketInfo.isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        var objects = objectService.findByBucket(bucketInfo.get().id());
        var result = S3XmlResponses.ListVersionsResult.from(bucket, objects);
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_XML)
            .bodyValue(result);
    }

    /** DELETE /{bucket} — DeleteBucket */
    public Mono<ServerResponse> deleteBucket(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
        if (bucketInfo.isEmpty()) {
            return ServerResponse.notFound().build();
        }
        bucketService.deleteBucket(bucketInfo.get().id());
        return ServerResponse.noContent().build();
    }
}
