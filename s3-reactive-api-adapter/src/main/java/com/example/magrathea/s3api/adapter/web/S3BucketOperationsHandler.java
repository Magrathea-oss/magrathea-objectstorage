package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.Region;
import com.example.magrathea.objectstore.domain.valueobject.StorageClass;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.reactive.application.service.ReactiveObjectService;
import com.example.magrathea.s3api.dto.command.VersioningConfigurationCommand;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
import com.example.magrathea.s3api.dto.query.ListAllMyBucketsResultQuery;
import com.example.magrathea.s3api.dto.query.ListDirectoryBucketsQuery;
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

/**
 * Bucket-context S3 operations.
 * Owns bucket lifecycle, bucket discovery/configuration, and bucket-level object listings.
 * Uses reactive services natively — no blocking, no CompletableFuture bridging.
 */
public class S3BucketOperationsHandler {

    private final ReactiveBucketService bucketService;
    private final ReactiveObjectService objectService;

    public S3BucketOperationsHandler(ReactiveBucketService bucketService,
                                      ReactiveObjectService objectService) {
        this.bucketService = bucketService;
        this.objectService = objectService;
    }

    /** GET / — ListBuckets (XML) */
    public Mono<ServerResponse> listBucketsXml(ServerRequest request) {
        return ListAllMyBucketsResultQuery.from(bucketService.findAllBuckets())
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(result));
    }

    /** GET / — ListBuckets (JSON, test convenience only) */
    public Mono<ServerResponse> listBucketsJson(ServerRequest request) {
        return bucketService.findAllBuckets()
            .collectList()
            .flatMap(buckets -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buckets));
    }

    /** PUT /{bucket} — CreateBucket */
    public Mono<ServerResponse> createBucket(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return bucketService.findByName(bucketName)
            .flatMap(existing -> ServerResponse.status(HttpStatus.CONFLICT)
                .bodyValue(ErrorQuery.from("BucketAlreadyExists", bucketName)))
            .switchIfEmpty(Mono.defer(() -> {
                // Validate bucket name before creating
                if (bucketName.length() < 3 || bucketName.length() > 63) {
                    return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidBucketName",
                        "Bucket name must be 3-63 characters");
                }
                if (!bucketName.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$")) {
                    return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidBucketName",
                        "Bucket name must be lowercase, no underscores");
                }
                var bucketId = Bucket.Id.generate();
                var bucket = Bucket.create(bucketId, bucketName, Region.US_EAST_1, StorageClass.STANDARD);
                return bucketService.createBucket(bucket)
                    .then(ServerResponse.ok()
                        .header("Location", "/" + bucketName)
                        .build());
            }));
    }

    /** HEAD /{bucket} — HeadBucket */
    public Mono<ServerResponse> headBucket(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(found -> ServerResponse.ok().build())
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    /** GET /{bucket}?location — GetBucketLocation */
    public Mono<ServerResponse> getBucketLocation(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return bucketService.findByName(bucketName)
            .flatMap(bucket -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(LocationConstraintQuery.from(bucket.region())))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}?versioning — GetBucketVersioning */
    public Mono<ServerResponse> getBucketVersioning(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return bucketService.findByName(bucketName)
            .flatMap(bucket -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(VersioningConfigurationQuery.from(bucket.versioningEnabled())))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?versioning — PutBucketVersioning */
    public Mono<ServerResponse> putBucketVersioning(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(VersioningConfigurationCommand.class)
                .flatMap(cmd -> {
                    var enabled = "Enabled".equals(cmd.status());
                    var updated = enabled ? b.withVersioningEnabled() : b.withVersioningSuspended();
                    return bucketService.updateBucket(updated)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket} — ListObjects (XML) */
    public Mono<ServerResponse> listObjectsXml(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> S3WebSupport.validateRuntimeRequest(request, b)
                .switchIfEmpty(Mono.defer(() -> ListObjectsQuery.from(bucket, objectService.findByBucket(b.id()))
                    .flatMap(result -> {
                        var builder = ServerResponse.ok().contentType(MediaType.APPLICATION_XML);
                        S3WebSupport.applyRuntimeHeaders(builder, request, b);
                        return builder.bodyValue(result);
                    }))))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}?list-type=2 — ListObjectsV2 */
    public Mono<ServerResponse> listObjectsV2Xml(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> S3WebSupport.validateRuntimeRequest(request, b)
                .switchIfEmpty(Mono.defer(() -> ListObjectsV2Query.from(bucket, objectService.findByBucket(b.id()))
                    .flatMap(result -> {
                        var builder = ServerResponse.ok().contentType(MediaType.APPLICATION_XML);
                        S3WebSupport.applyRuntimeHeaders(builder, request, b);
                        return builder.bodyValue(result);
                    }))))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}?versions — ListObjectVersions */
    public Mono<ServerResponse> listObjectVersions(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> S3WebSupport.validateRuntimeRequest(request, b)
                .switchIfEmpty(Mono.defer(() -> ListVersionsQuery.from(bucket, objectService.findByBucket(b.id()))
                    .flatMap(result -> {
                        var builder = ServerResponse.ok().contentType(MediaType.APPLICATION_XML);
                        S3WebSupport.applyRuntimeHeaders(builder, request, b);
                        return builder.bodyValue(result);
                    }))))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /**
     * GET /{bucket} — Website hosting routing.
     * Checks if the bucket has website configuration and handles the request as a website request.
     * Falls through to listObjectsXml if no website config is found.
     */
    public Mono<ServerResponse> websiteRouting(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return bucketService.findByName(bucketName)
            .flatMap(bucket -> S3WebSupport.handleWebsiteRequest(request, bucket)
                .switchIfEmpty(Mono.defer(() -> listObjectsXml(request))))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /?directory-buckets — ListDirectoryBuckets */
    public Mono<ServerResponse> listDirectoryBuckets(ServerRequest request) {
        return bucketService.findAllBuckets()
            .map(b -> new ListDirectoryBucketsQuery.BucketEntry(
                b.name(), b.id().value(), java.time.Instant.now().toString()))
            .collectList()
            .flatMap(entries -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new ListDirectoryBucketsQuery(entries)));
    }

    /** DELETE /{bucket} — DeleteBucket */
    public Mono<ServerResponse> deleteBucket(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> bucketService.deleteBucket(b)
                .then(ServerResponse.noContent().build()))
            .switchIfEmpty(ServerResponse.notFound().build());
    }
}
