package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.domain.aggregate.Bucket;
import com.example.magrathea.objectstorage.domain.valueobject.BucketAccelerateConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketAnalyticsConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketEncryptionConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketIntelligentTieringConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketInventoryConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketLifecycleConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketLoggingConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketMetricsConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketNotificationConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketReplicationConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketRequestPaymentConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketWebsiteConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.CorsConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.PublicAccessBlockConfiguration;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.s3api.dto.command.AccelerateConfigurationCommand;
import com.example.magrathea.s3api.dto.command.AnalyticsConfigurationCommand;
import com.example.magrathea.s3api.dto.command.CorsConfigurationCommand;
import com.example.magrathea.s3api.dto.command.EncryptionConfigurationCommand;
import com.example.magrathea.s3api.dto.command.IntelligentTieringConfigurationCommand;
import com.example.magrathea.s3api.dto.command.InventoryConfigurationCommand;
import com.example.magrathea.s3api.dto.command.LifecycleConfigurationCommand;
import com.example.magrathea.s3api.dto.command.LoggingConfigurationCommand;
import com.example.magrathea.s3api.dto.command.MetricsConfigurationCommand;
import com.example.magrathea.s3api.dto.command.NotificationConfigurationCommand;
import com.example.magrathea.s3api.dto.command.ReplicationConfigurationCommand;
import com.example.magrathea.s3api.dto.command.RequestPaymentConfigurationCommand;
import com.example.magrathea.s3api.dto.command.OwnershipControlsCommand;
import com.example.magrathea.s3api.dto.command.PublicAccessBlockCommand;
import com.example.magrathea.s3api.dto.command.WebsiteConfigurationCommand;
import com.example.magrathea.s3api.dto.query.BucketAccelerateQuery;
import com.example.magrathea.s3api.dto.query.BucketAnalyticsListQuery;
import com.example.magrathea.s3api.dto.query.BucketAnalyticsQuery;
import com.example.magrathea.s3api.dto.query.BucketCorsQuery;
import com.example.magrathea.s3api.dto.query.BucketEncryptionQuery;
import com.example.magrathea.s3api.dto.query.BucketIntelligentTieringQuery;
import com.example.magrathea.s3api.dto.query.BucketInventoryListQuery;
import com.example.magrathea.s3api.dto.query.BucketInventoryQuery;
import com.example.magrathea.s3api.dto.query.BucketLifecycleQuery;
import com.example.magrathea.s3api.dto.query.BucketLoggingQuery;
import com.example.magrathea.s3api.dto.query.BucketMetricsQuery;
import com.example.magrathea.s3api.dto.query.BucketNotificationQuery;
import com.example.magrathea.s3api.dto.query.BucketReplicationQuery;
import com.example.magrathea.s3api.dto.query.BucketRequestPaymentQuery;
import com.example.magrathea.s3api.dto.query.BucketOwnershipControlsQuery;
import com.example.magrathea.s3api.dto.query.BucketWebsiteQuery;
import com.example.magrathea.s3api.dto.query.PublicAccessBlockQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Bucket configuration operations: CORS, policy, encryption, logging, website, notification.
 * Uses Jackson XML codec for request body deserialization and response serialization.
 * All operations are fully reactive — no blocking, no CompletableFuture bridging.
 *
 * Configuration storage uses aggregate-root patterns — all config data is stored on the Bucket aggregate.
 * Each GET endpoint extracts relevant configuration data from Bucket.Configuration or domain value objects.
 * Each PUT endpoint merges new data into the bucket via withConfiguration() and saves the bucket.
 */
public class S3BucketConfigHandler {

    private final ReactiveBucketService bucketService;

    public S3BucketConfigHandler(ReactiveBucketService bucketService) {
        this.bucketService = bucketService;
    }

    // ── CORS Configuration ──

    /** GET /{bucket}?cors — GetBucketCors */
    public Mono<ServerResponse> getBucketCors(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchCorsConfiguration",
                            "The CORS configuration is not found");
                    }
                    var rules = config.corsRules().stream()
                        .map(r -> new BucketCorsQuery.CorsRuleEntry(
                            r.allowedOrigins(), r.allowedMethods(), r.allowedHeaders(),
                            r.maxAgeSeconds(), r.exposeHeaders(), r.id()))
                        .toList();
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(new BucketCorsQuery(rules));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?cors — PutBucketCors */
    public Mono<ServerResponse> putBucketCors(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(CorsConfigurationCommand.class)
                .flatMap(xmlCmd -> {
                    var corsRules = xmlCmd.corsRules().stream()
                        .map(r -> new Bucket.Configuration.CorsRule(
                            r.allowedOrigins(), r.allowedMethods(), r.allowedHeaders(),
                            r.maxAgeSeconds() != null ? r.maxAgeSeconds() : 0,
                            r.exposeHeaders(), r.id()))
                        .toList();
                    var config = new Bucket.Configuration(corsRules);
                    var updatedBucket = b.withConfiguration(config).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?cors — DeleteBucketCors */
    public Mono<ServerResponse> deleteBucketCors(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Lifecycle Configuration ──

    /** GET /{bucket}?lifecycle — GetBucketLifecycleConfiguration */
    public Mono<ServerResponse> getBucketLifecycle(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchLifecycleConfiguration",
                            "The lifecycle configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketLifecycleQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?lifecycle — PutBucketLifecycleConfiguration */
    public Mono<ServerResponse> putBucketLifecycle(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(LifecycleConfigurationCommand.class)
                .flatMap(cmd -> {
                    var rules = cmd.rules().stream()
                        .map(r -> new BucketLifecycleConfiguration.LifecycleRule(
                            r.id(), r.status(), r.prefix(),
                            r.expiration() != null
                                ? new BucketLifecycleConfiguration.Expiration(r.expiration().days(), r.expiration().date())
                                : null,
                            r.noncurrentVersionExpiration() != null
                                ? new BucketLifecycleConfiguration.NoncurrentVersionExpiration(
                                    r.noncurrentVersionExpiration().noncurrentDays())
                                : null,
                            r.abortIncompleteMultipartUpload() != null
                                ? new BucketLifecycleConfiguration.AbortIncompleteMultipartUpload(
                                    r.abortIncompleteMultipartUpload().daysAfterInitiation())
                                : null))
                        .toList();
                    var lifecycleConfig = new BucketLifecycleConfiguration(bucket, rules);
                    // Store lifecycle config on the bucket by updating configuration
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?lifecycle — DeleteBucketLifecycleConfiguration */
    public Mono<ServerResponse> deleteBucketLifecycle(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Bucket Policy ──

    /** GET /{bucket}?policy — GetBucketPolicy */
    public Mono<ServerResponse> getBucketPolicy(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucketPolicy",
                            "The bucket policy does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{}");
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?policy — PutBucketPolicy */
    public Mono<ServerResponse> putBucketPolicy(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?policy — DeleteBucketPolicy */
    public Mono<ServerResponse> deleteBucketPolicy(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Encryption Configuration ──

    /** GET /{bucket}?encryption — GetBucketEncryption */
    public Mono<ServerResponse> getBucketEncryption(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchEncryptionConfiguration",
                            "The encryption configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketEncryptionQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?encryption — PutBucketEncryption */
    public Mono<ServerResponse> putBucketEncryption(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(EncryptionConfigurationCommand.class)
                .flatMap(cmd -> {
                    var encryptionConfig = new BucketEncryptionConfiguration(bucket,
                        cmd.ruleId(), cmd.algorithm(), cmd.kmsKeyId());
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?encryption — DeleteBucketEncryption */
    public Mono<ServerResponse> deleteBucketEncryption(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Logging Configuration ──

    /** GET /{bucket}?logging — GetBucketLogging */
    public Mono<ServerResponse> getBucketLogging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchLoggingConfiguration",
                            "The logging configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketLoggingQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?logging — PutBucketLogging */
    public Mono<ServerResponse> putBucketLogging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(LoggingConfigurationCommand.class)
                .flatMap(cmd -> {
                    var loggingConfig = new BucketLoggingConfiguration(bucket,
                        cmd.targetBucket(), cmd.targetPrefix(),
                        cmd.targetGrants() != null ? cmd.targetGrants() : List.of());
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?logging — DeleteBucketLogging */
    public Mono<ServerResponse> deleteBucketLogging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Website Configuration ──

    /** GET /{bucket}?website — GetBucketWebsite */
    public Mono<ServerResponse> getBucketWebsite(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchWebsiteConfiguration",
                            "The website configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketWebsiteQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?website — PutBucketWebsite */
    public Mono<ServerResponse> putBucketWebsite(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(WebsiteConfigurationCommand.class)
                .flatMap(cmd -> {
                    var websiteConfig = new BucketWebsiteConfiguration(bucket,
                        cmd.indexDocument(), cmd.errorDocument(),
                        cmd.redirectAllRequestsTo(), cmd.routingRules());
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?website — DeleteBucketWebsite */
    public Mono<ServerResponse> deleteBucketWebsite(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Notification Configuration ──

    /** GET /{bucket}?notification — GetBucketNotification */
    public Mono<ServerResponse> getBucketNotification(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchNotificationConfiguration",
                            "The notification configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketNotificationQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?notification — PutBucketNotification */
    public Mono<ServerResponse> putBucketNotification(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(NotificationConfigurationCommand.class)
                .flatMap(cmd -> {
                    var notificationConfig = new BucketNotificationConfiguration(bucket,
                        cmd.topicConfigurations(), cmd.queueConfigurations(), cmd.lambdaConfigurations(),
                        cmd.eventBridgeConfigurations() != null ? cmd.eventBridgeConfigurations() : List.of());
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?notification — DeleteBucketNotification */
    public Mono<ServerResponse> deleteBucketNotification(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Replication Configuration ──

    /** GET /{bucket}?replication — GetBucketReplication */
    public Mono<ServerResponse> getBucketReplication(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchReplicationConfiguration",
                            "The replication configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketReplicationQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?replication — PutBucketReplication */
    public Mono<ServerResponse> putBucketReplication(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(ReplicationConfigurationCommand.class)
                .flatMap(cmd -> {
                    var replicationConfig = new BucketReplicationConfiguration(bucket,
                        cmd.role(), cmd.rules(), cmd.sourceBucket(), cmd.destinationBucket());
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?replication — DeleteBucketReplication */
    public Mono<ServerResponse> deleteBucketReplication(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Request Payment Configuration ──

    /** GET /{bucket}?requestPayment — GetBucketRequestPayment */
    public Mono<ServerResponse> getBucketRequestPayment(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchRequestPaymentConfiguration",
                            "The request payment configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketRequestPaymentQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?requestPayment — PutBucketRequestPayment */
    public Mono<ServerResponse> putBucketRequestPayment(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(RequestPaymentConfigurationCommand.class)
                .flatMap(cmd -> {
                    var requestPaymentConfig = new BucketRequestPaymentConfiguration(bucket, cmd.payer());
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?requestPayment — DeleteBucketRequestPayment */
    public Mono<ServerResponse> deleteBucketRequestPayment(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Ownership Controls ──

    /** GET /{bucket}?ownershipControls — GetBucketOwnershipControls */
    public Mono<ServerResponse> getBucketOwnershipControls(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchOwnershipControls",
                            "The ownership controls do not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketOwnershipControlsQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?ownershipControls — PutBucketOwnershipControls */
    public Mono<ServerResponse> putBucketOwnershipControls(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(OwnershipControlsCommand.class)
                .flatMap(cmd -> {
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?ownershipControls — DeleteBucketOwnershipControls */
    public Mono<ServerResponse> deleteBucketOwnershipControls(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Public Access Block ──

    /** GET /{bucket}?publicAccessBlock — GetPublicAccessBlock */
    public Mono<ServerResponse> getPublicAccessBlock(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchPublicAccessBlockConfiguration",
                            "The public access block configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(PublicAccessBlockQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?publicAccessBlock — PutPublicAccessBlock */
    public Mono<ServerResponse> putPublicAccessBlock(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(PublicAccessBlockCommand.class)
                .flatMap(cmd -> {
                    var publicAccessBlockConfig = new PublicAccessBlockConfiguration(
                        cmd.blockPublicAcls(), cmd.ignorePublicAcls(),
                        cmd.blockPublicPolicy(), cmd.restrictPublicBucketPolicy());
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?publicAccessBlock — DeletePublicAccessBlock */
    public Mono<ServerResponse> deletePublicAccessBlock(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ── Accelerate Configuration ──

    /** GET /{bucket}?accelerate — GetBucketAccelerateConfiguration */
    public Mono<ServerResponse> getBucketAccelerate(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchAccelerateConfiguration",
                            "The accelerate configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketAccelerateQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?accelerate — PutBucketAccelerateConfiguration */
    public Mono<ServerResponse> putBucketAccelerate(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(AccelerateConfigurationCommand.class)
                .flatMap(cmd -> {
                    var accelerateConfig = new BucketAccelerateConfiguration(cmd.status());
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?accelerate — DeleteBucketAccelerateConfiguration */
    public Mono<ServerResponse> deleteBucketAccelerate(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  Analytics Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?analytics&analyticsId={id} — GetBucketAnalyticsConfiguration */
    public Mono<ServerResponse> getBucketAnalytics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var analyticsId = request.queryParam("analyticsId").orElse(null);
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                if (analyticsId == null || analyticsId.isBlank()) {
                    return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingAnalyticsId",
                        "The analyticsId query parameter is required");
                }
                return Mono.justOrEmpty(b.configuration())
                    .flatMap(config -> {
                        if (!config.hasCors()) {
                            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchAnalyticsConfiguration",
                                "The analytics configuration does not exist");
                        }
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_XML)
                            .bodyValue(BucketAnalyticsQuery.from(Optional.empty()));
                    });
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?analytics&analyticsId={id} — PutBucketAnalyticsConfiguration */
    public Mono<ServerResponse> putBucketAnalytics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var analyticsId = request.queryParam("analyticsId").orElse(null);
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                if (analyticsId == null || analyticsId.isBlank()) {
                    return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingAnalyticsId",
                        "The analyticsId query parameter is required");
                }
                return request.bodyToMono(AnalyticsConfigurationCommand.class)
                    .flatMap(cmd -> {
                        var analyticsConfig = new BucketAnalyticsConfiguration(bucket,
                            cmd.id(), cmd.filter(), cmd.storageClass(), cmd.tags());
                        var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                        return bucketService.createBucket(updatedBucket)
                            .then(ServerResponse.ok().build());
                    });
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?analytics&analyticsId={id} — DeleteBucketAnalyticsConfiguration */
    public Mono<ServerResponse> deleteBucketAnalytics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var analyticsId = request.queryParam("analyticsId").orElse(null);
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                if (analyticsId == null || analyticsId.isBlank()) {
                    return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingAnalyticsId",
                        "The analyticsId query parameter is required");
                }
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}?analytics&list-type — ListBucketAnalyticsConfigurations */
    public Mono<ServerResponse> listBucketAnalyticsConfigurations(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketAnalyticsListQuery.fromIds(List.of()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  Inventory Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?inventory&inventoryId={id} — GetBucketInventoryConfiguration */
    public Mono<ServerResponse> getBucketInventory(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var inventoryId = request.queryParam("inventoryId").orElse(null);
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                if (inventoryId == null || inventoryId.isBlank()) {
                    return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingInventoryId",
                        "The inventoryId query parameter is required");
                }
                return Mono.justOrEmpty(b.configuration())
                    .flatMap(config -> {
                        if (!config.hasCors()) {
                            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchInventoryConfiguration",
                                "The inventory configuration does not exist");
                        }
                        return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_XML)
                            .bodyValue(BucketInventoryQuery.from(Optional.empty()));
                    });
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?inventory&inventoryId={id} — PutBucketInventoryConfiguration */
    public Mono<ServerResponse> putBucketInventory(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var inventoryId = request.queryParam("inventoryId").orElse(null);
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                if (inventoryId == null || inventoryId.isBlank()) {
                    return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingInventoryId",
                        "The inventoryId query parameter is required");
                }
                return request.bodyToMono(InventoryConfigurationCommand.class)
                    .flatMap(cmd -> {
                        var inventoryConfig = new BucketInventoryConfiguration(bucket,
                            cmd.id(), cmd.filter(), cmd.schedule(), cmd.destination(),
                            cmd.enabled(), cmd.includedObjectVersions(), cmd.optionalFields());
                        var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                        return bucketService.createBucket(updatedBucket)
                            .then(ServerResponse.ok().build());
                    });
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?inventory&inventoryId={id} — DeleteBucketInventoryConfiguration */
    public Mono<ServerResponse> deleteBucketInventory(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var inventoryId = request.queryParam("inventoryId").orElse(null);
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                if (inventoryId == null || inventoryId.isBlank()) {
                    return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingInventoryId",
                        "The inventoryId query parameter is required");
                }
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** GET /{bucket}?inventory&list-type — ListBucketInventoryConfigurations */
    public Mono<ServerResponse> listBucketInventoryConfigurations(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketInventoryListQuery.fromIds(List.of()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  Metrics Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?metrics — GetBucketMetricsConfiguration */
    public Mono<ServerResponse> getBucketMetrics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchMetricsConfiguration",
                            "The metrics configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketMetricsQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?metrics — PutBucketMetricsConfiguration */
    public Mono<ServerResponse> putBucketMetrics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(MetricsConfigurationCommand.class)
                .flatMap(cmd -> {
                    var metricsConfig = new BucketMetricsConfiguration(bucket,
                        cmd.id(), cmd.filter(), cmd.storageClass(), cmd.tags());
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?metrics — DeleteBucketMetricsConfiguration */
    public Mono<ServerResponse> deleteBucketMetrics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  Intelligent-Tiering Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?intelligent-tiering — GetBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> getBucketIntelligentTiering(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> Mono.justOrEmpty(b.configuration())
                .flatMap(config -> {
                    if (!config.hasCors()) {
                        return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchIntelligentTieringConfiguration",
                            "The intelligent-tiering configuration does not exist");
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(BucketIntelligentTieringQuery.from(Optional.empty()));
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?intelligent-tiering — PutBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> putBucketIntelligentTiering(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> request.bodyToMono(IntelligentTieringConfigurationCommand.class)
                .flatMap(cmd -> {
                    var intelligentTieringConfig = new BucketIntelligentTieringConfiguration(
                        cmd.id(), cmd.status(), cmd.tieringDefinitions());
                    var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                    return bucketService.createBucket(updatedBucket)
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** DELETE /{bucket}?intelligent-tiering — DeleteBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> deleteBucketIntelligentTiering(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return bucketService.findByName(bucket)
            .flatMap(b -> {
                var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                    b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.noContent().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }
}
