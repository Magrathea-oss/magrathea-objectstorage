package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.dto.CreateBucketCommand;
import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.objectstorage.application.service.ObjectService;
import com.example.magrathea.s3api.dto.command.VersioningConfigurationCommand;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
import com.example.magrathea.s3api.dto.query.ListAllMyBucketsResultQuery;
import com.example.magrathea.s3api.dto.query.LocationConstraintQuery;
import com.example.magrathea.s3api.dto.query.VersioningConfigurationQuery;
import com.example.magrathea.s3api.dto.query.ListObjectsQuery;
import com.example.magrathea.s3api.dto.query.ListObjectsV2Query;
import com.example.magrathea.s3api.dto.query.ListVersionsQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
        return Mono.fromCallable(() -> bucketService.findAll())
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(buckets -> {
                var result = ListAllMyBucketsResultQuery.from(buckets);
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(result);
            });
    }

    /** GET / — ListBuckets (JSON, test convenience only) */
    public Mono<ServerResponse> listBucketsJson(ServerRequest request) {
        return Mono.fromCallable(() -> bucketService.findAll())
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(buckets -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buckets));
    }

    /** PUT /{bucket} — CreateBucket */
    public Mono<ServerResponse> createBucket(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            var exists = S3WebSupport.findBucket(bucketService, bucketName).isPresent();
            if (exists) {
                return ServerResponse.status(HttpStatus.CONFLICT)
                    .bodyValue(ErrorQuery.from("BucketAlreadyExists", bucketName));
            }
            // Validate bucket name before creating
            if (bucketName.length() < 3 || bucketName.length() > 63) {
                return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidBucketName", "Bucket name must be 3-63 characters");
            }
            if (!bucketName.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$")) {
                return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidBucketName", "Bucket name must be lowercase, no underscores");
            }
            var cmd = new CreateBucketCommand(bucketName, "us-east-1", "STANDARD");
            bucketService.createBucket(cmd);
            return ServerResponse.ok()
                .header("Location", "/" + bucketName)
                .build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** HEAD /{bucket} — HeadBucket */
    public Mono<ServerResponse> headBucket(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            var found = S3WebSupport.findBucket(bucketService, bucket).isPresent();
            return found
                ? ServerResponse.ok().build()
                : ServerResponse.notFound().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** GET /{bucket}?location — GetBucketLocation */
    public Mono<ServerResponse> getBucketLocation(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            var bucket = S3WebSupport.findBucket(bucketService, bucketName);
            if (bucket.isPresent()) {
                var b = bucket.get();
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(LocationConstraintQuery.from(b.region()));
            } else {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** GET /{bucket}?versioning — GetBucketVersioning */
    public Mono<ServerResponse> getBucketVersioning(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            var bucket = S3WebSupport.findBucket(bucketService, bucketName);
            if (bucket.isPresent()) {
                var b = bucket.get();
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(VersioningConfigurationQuery.from(b.versioningEnabled()));
            } else {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?versioning — PutBucketVersioning */
    public Mono<ServerResponse> putBucketVersioning(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(VersioningConfigurationCommand.class)
            .flatMap(cmd -> {
                var enabled = "Enabled".equals(cmd.status());
                bucketService.putBucketVersioning(bucket, enabled);
                return ServerResponse.ok().build();
            });
    }

    /** GET /{bucket} — ListObjects (XML) */
    public Mono<ServerResponse> listObjectsXml(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
            if (bucketInfo.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var objects = objectService.findByBucket(bucketInfo.get().id());
            var result = ListObjectsQuery.from(bucket, objects);
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(result);
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** GET /{bucket}?list-type=2 — ListObjectsV2 */
    public Mono<ServerResponse> listObjectsV2Xml(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
            if (bucketInfo.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var objects = objectService.findByBucket(bucketInfo.get().id());
            var result = ListObjectsV2Query.from(bucket, objects);
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(result);
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** GET /{bucket}?versions — ListObjectVersions */
    public Mono<ServerResponse> listObjectVersions(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
            if (bucketInfo.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var objects = objectService.findByBucket(bucketInfo.get().id());
            var result = ListVersionsQuery.from(bucket, objects);
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(result);
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** DELETE /{bucket} — DeleteBucket */
    public Mono<ServerResponse> deleteBucket(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            var bucketInfo = S3WebSupport.findBucket(bucketService, bucket);
            if (bucketInfo.isEmpty()) {
                return ServerResponse.notFound().build();
            }
            bucketService.deleteBucket(bucketInfo.get().id());
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }
}
