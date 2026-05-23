package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.service.BucketService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Bucket configuration list-type operations: Analytics, Inventory, Metrics, Intelligent-Tiering.
 * These APIs use ?analytics, ?inventory, ?metrics, ?intelligent-tiering query params
 * with an optional &id=xxx for get/put/delete vs list.
 *
 * NOTE: Analytics/Inventory/Metrics/Intelligent-Tiering service/repository methods are
 * not yet implemented (Phase E). The handler routes are registered but all PUT operations
 * return 501 Not Implemented until the service layer is completed.
 */
public class S3BucketConfigListHandler {

    private final BucketService bucketService;

    public S3BucketConfigListHandler(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    // ── Analytics ──

    /** GET /{bucket}?analytics — GetBucketAnalyticsConfiguration or ListBucketAnalyticsConfigurations */
    public Mono<ServerResponse> getBucketAnalytics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            if (id != null) {
                return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                    "GetBucketAnalyticsConfiguration not yet implemented");
            } else {
                return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                    "ListBucketAnalyticsConfigurations not yet implemented");
            }
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?analytics&id=xxx — PutBucketAnalyticsConfiguration */
    public Mono<ServerResponse> putBucketAnalytics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        if (id == null) {
            return Mono.fromCallable(() ->
                S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingId", "Analytics configuration id is required"))
                .flatMap(Mono::from);
        }
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return Mono.fromCallable(() ->
                S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                    "PutBucketAnalyticsConfiguration not yet implemented"))
                .flatMap(Mono::from);
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** DELETE /{bucket}?analytics&id=xxx — DeleteBucketAnalyticsConfiguration */
    public Mono<ServerResponse> deleteBucketAnalytics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                "DeleteBucketAnalyticsConfiguration not yet implemented");
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Inventory ──

    /** GET /{bucket}?inventory — GetBucketInventoryConfiguration or ListBucketInventoryConfigurations */
    public Mono<ServerResponse> getBucketInventory(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                "Bucket inventory configuration not yet implemented");
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?inventory&id=xxx — PutBucketInventoryConfiguration */
    public Mono<ServerResponse> putBucketInventory(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        if (id == null) {
            return Mono.fromCallable(() ->
                S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingId", "Inventory configuration id is required"))
                .flatMap(Mono::from);
        }
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                "PutBucketInventoryConfiguration not yet implemented");
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** DELETE /{bucket}?inventory&id=xxx — DeleteBucketInventoryConfiguration */
    public Mono<ServerResponse> deleteBucketInventory(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                "DeleteBucketInventoryConfiguration not yet implemented");
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Metrics ──

    /** GET /{bucket}?metrics — GetBucketMetricsConfiguration or ListBucketMetricsConfigurations */
    public Mono<ServerResponse> getBucketMetrics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                "Bucket metrics configuration not yet implemented");
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?metrics&id=xxx — PutBucketMetricsConfiguration */
    public Mono<ServerResponse> putBucketMetrics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        if (id == null) {
            return Mono.fromCallable(() ->
                S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingId", "Metrics configuration id is required"))
                .flatMap(Mono::from);
        }
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                "PutBucketMetricsConfiguration not yet implemented");
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** DELETE /{bucket}?metrics&id=xxx — DeleteBucketMetricsConfiguration */
    public Mono<ServerResponse> deleteBucketMetrics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                "DeleteBucketMetricsConfiguration not yet implemented");
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Intelligent-Tiering ──

    /** GET /{bucket}?intelligent-tiering — GetBucketIntelligentTieringConfiguration or List */
    public Mono<ServerResponse> getBucketIntelligentTiering(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                "Bucket intelligent-tiering configuration not yet implemented");
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?intelligent-tiering&id=xxx — PutBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> putBucketIntelligentTiering(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        if (id == null) {
            return Mono.fromCallable(() ->
                S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingId", "Intelligent-Tiering configuration id is required"))
                .flatMap(Mono::from);
        }
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                "PutBucketIntelligentTieringConfiguration not yet implemented");
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** DELETE /{bucket}?intelligent-tiering&id=xxx — DeleteBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> deleteBucketIntelligentTiering(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var id = request.queryParam("id").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return S3WebSupport.xmlError(HttpStatus.NOT_IMPLEMENTED, "NotImplemented",
                "DeleteBucketIntelligentTieringConfiguration not yet implemented");
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }
}
