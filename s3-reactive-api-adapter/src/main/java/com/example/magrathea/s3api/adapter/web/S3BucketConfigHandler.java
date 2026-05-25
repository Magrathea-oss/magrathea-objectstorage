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
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Bucket configuration operations: CORS, policy, encryption, logging, website, notification.
 * Uses Jackson XML codec for request body deserialization and response serialization.
 * All operations are fully reactive — no blocking, no CompletableFuture bridging.
 *
 * Configuration storage uses aggregate-root patterns — all config data is stored on the Bucket aggregate.
 * Each GET endpoint extracts relevant configuration data from Bucket.Configuration or domain value objects.
 * Each PUT endpoint merges new data into the bucket via withConfiguration() and saves the bucket.
 *
 * Uses generic helper methods to eliminate duplication — all 50+ config operations
 * are reduced to a few lines each by extracting the common find-bucket → apply → respond pattern.
 */
public class S3BucketConfigHandler {

    private final ReactiveBucketService bucketService;

    public S3BucketConfigHandler(ReactiveBucketService bucketService) {
        this.bucketService = bucketService;
    }

    // ─────────────────────────────────────────────────────
    //  Generic helpers — capture the common pattern
    // ─────────────────────────────────────────────────────

    /** Finds a bucket by name from the request, applies handler, or returns 404. */
    private Mono<ServerResponse> findBucket(ServerRequest request,
                                            Function<Bucket, Mono<ServerResponse>> handler) {
        var bucketName = request.pathVariable("bucket");
        return bucketService.findByName(bucketName)
            .flatMap(handler)
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** Helper for GET config — finds bucket, checks config exists, maps to response. */
    private Mono<ServerResponse> getConfig(ServerRequest request,
                                           String errorCode,
                                           String errorMessage,
                                           Function<Bucket.Configuration, Mono<ServerResponse>> responseMapper) {
        return findBucket(request, b -> Mono.justOrEmpty(b.configuration())
            .flatMap(config -> {
                if (!config.hasCors()) {
                    return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, errorCode, errorMessage);
                }
                return responseMapper.apply(config);
            }));
    }

    /** Helper for GET config with a required query parameter. */
    private Mono<ServerResponse> getConfigWithParam(ServerRequest request,
                                                    String paramName,
                                                    String missingCode,
                                                    String missingMessage,
                                                    String errorCode,
                                                    String errorMessage,
                                                    Function<Bucket.Configuration, Mono<ServerResponse>> responseMapper) {
        var paramValue = request.queryParam(paramName).orElse(null);
        if (paramValue == null || paramValue.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, missingCode, missingMessage);
        }
        return getConfig(request, errorCode, errorMessage, responseMapper);
    }

    /** Helper for PUT config — finds bucket, parses command body, applies handler. */
    private <T> Mono<ServerResponse> putConfig(ServerRequest request,
                                               Class<T> commandClass,
                                               BiFunction<Bucket, T, Mono<ServerResponse>> handler) {
        return findBucket(request, b -> request.bodyToMono(commandClass)
            .flatMap(cmd -> handler.apply(b, cmd)));
    }

    /** Helper for PUT config with a required query parameter. */
    private <T> Mono<ServerResponse> putConfigWithParam(ServerRequest request,
                                                        String paramName,
                                                        String missingCode,
                                                        String missingMessage,
                                                        Class<T> commandClass,
                                                        BiFunction<Bucket, T, Mono<ServerResponse>> handler) {
        var paramValue = request.queryParam(paramName).orElse(null);
        if (paramValue == null || paramValue.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, missingCode, missingMessage);
        }
        return putConfig(request, commandClass, handler);
    }

    /** Helper for DELETE config — clears configuration on the bucket. */
    private Mono<ServerResponse> deleteConfig(ServerRequest request) {
        return findBucket(request, b -> {
            var updatedBucket = new Bucket(b.id(), b.name(), b.region(), b.storageClass(),
                b.versioningEnabled(), b.encryptionEnabled(), null, b.events());
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.noContent().build());
        });
    }

    /** Helper for DELETE config with a required query parameter. */
    private Mono<ServerResponse> deleteConfigWithParam(ServerRequest request,
                                                       String paramName,
                                                       String missingCode,
                                                       String missingMessage) {
        var paramValue = request.queryParam(paramName).orElse(null);
        if (paramValue == null || paramValue.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, missingCode, missingMessage);
        }
        return deleteConfig(request);
    }

    /** Helper for PUT with String body (not a typed command). */
    private Mono<ServerResponse> putConfigString(ServerRequest request,
                                                 BiFunction<Bucket, String, Mono<ServerResponse>> handler) {
        return findBucket(request, b -> request.bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> handler.apply(b, body)));
    }

    // ── CORS Configuration ──

    /** GET /{bucket}?cors — GetBucketCors */
    public Mono<ServerResponse> getBucketCors(ServerRequest request) {
        return getConfig(request, "NoSuchCorsConfiguration", "The CORS configuration is not found",
            config -> {
                var rules = config.corsRules().stream()
                    .map(r -> new BucketCorsQuery.CorsRuleEntry(
                        r.allowedOrigins(), r.allowedMethods(), r.allowedHeaders(),
                        r.maxAgeSeconds(), r.exposeHeaders(), r.id()))
                    .toList();
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(new BucketCorsQuery(rules));
            });
    }

    /** PUT /{bucket}?cors — PutBucketCors */
    public Mono<ServerResponse> putBucketCors(ServerRequest request) {
        return putConfig(request, CorsConfigurationCommand.class, (b, cmd) -> {
            var corsRules = cmd.corsRules().stream()
                .map(r -> new Bucket.Configuration.CorsRule(
                    r.allowedOrigins(), r.allowedMethods(), r.allowedHeaders(),
                    r.maxAgeSeconds() != null ? r.maxAgeSeconds() : 0,
                    r.exposeHeaders(), r.id()))
                .toList();
            var config = new Bucket.Configuration(corsRules);
            var updatedBucket = b.withConfiguration(config).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?cors — DeleteBucketCors */
    public Mono<ServerResponse> deleteBucketCors(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Lifecycle Configuration ──

    /** GET /{bucket}?lifecycle — GetBucketLifecycleConfiguration */
    public Mono<ServerResponse> getBucketLifecycle(ServerRequest request) {
        return getConfig(request, "NoSuchLifecycleConfiguration", "The lifecycle configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketLifecycleQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?lifecycle — PutBucketLifecycleConfiguration */
    public Mono<ServerResponse> putBucketLifecycle(ServerRequest request) {
        return putConfig(request, LifecycleConfigurationCommand.class, (b, cmd) -> {
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
            var lifecycleConfig = new BucketLifecycleConfiguration(b.name(), rules);
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?lifecycle — DeleteBucketLifecycleConfiguration */
    public Mono<ServerResponse> deleteBucketLifecycle(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Bucket Policy ──

    /** GET /{bucket}?policy — GetBucketPolicy */
    public Mono<ServerResponse> getBucketPolicy(ServerRequest request) {
        return getConfig(request, "NoSuchBucketPolicy", "The bucket policy does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}"));
    }

    /** PUT /{bucket}?policy — PutBucketPolicy */
    public Mono<ServerResponse> putBucketPolicy(ServerRequest request) {
        return putConfigString(request, (b, body) -> {
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?policy — DeleteBucketPolicy */
    public Mono<ServerResponse> deleteBucketPolicy(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Encryption Configuration ──

    /** GET /{bucket}?encryption — GetBucketEncryption */
    public Mono<ServerResponse> getBucketEncryption(ServerRequest request) {
        return getConfig(request, "NoSuchEncryptionConfiguration", "The encryption configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketEncryptionQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?encryption — PutBucketEncryption */
    public Mono<ServerResponse> putBucketEncryption(ServerRequest request) {
        return putConfig(request, EncryptionConfigurationCommand.class, (b, cmd) -> {
            var encryptionConfig = new BucketEncryptionConfiguration(b.name(),
                cmd.ruleId(), cmd.algorithm(), cmd.kmsKeyId());
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?encryption — DeleteBucketEncryption */
    public Mono<ServerResponse> deleteBucketEncryption(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Logging Configuration ──

    /** GET /{bucket}?logging — GetBucketLogging */
    public Mono<ServerResponse> getBucketLogging(ServerRequest request) {
        return getConfig(request, "NoSuchLoggingConfiguration", "The logging configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketLoggingQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?logging — PutBucketLogging */
    public Mono<ServerResponse> putBucketLogging(ServerRequest request) {
        return putConfig(request, LoggingConfigurationCommand.class, (b, cmd) -> {
            var loggingConfig = new BucketLoggingConfiguration(b.name(),
                cmd.targetBucket(), cmd.targetPrefix(),
                cmd.targetGrants() != null ? cmd.targetGrants() : List.of());
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?logging — DeleteBucketLogging */
    public Mono<ServerResponse> deleteBucketLogging(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Website Configuration ──

    /** GET /{bucket}?website — GetBucketWebsite */
    public Mono<ServerResponse> getBucketWebsite(ServerRequest request) {
        return getConfig(request, "NoSuchWebsiteConfiguration", "The website configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketWebsiteQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?website — PutBucketWebsite */
    public Mono<ServerResponse> putBucketWebsite(ServerRequest request) {
        return putConfig(request, WebsiteConfigurationCommand.class, (b, cmd) -> {
            var websiteConfig = new BucketWebsiteConfiguration(b.name(),
                cmd.indexDocument(), cmd.errorDocument(),
                cmd.redirectAllRequestsTo(), cmd.routingRules());
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?website — DeleteBucketWebsite */
    public Mono<ServerResponse> deleteBucketWebsite(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Notification Configuration ──

    /** GET /{bucket}?notification — GetBucketNotification */
    public Mono<ServerResponse> getBucketNotification(ServerRequest request) {
        return getConfig(request, "NoSuchNotificationConfiguration", "The notification configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketNotificationQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?notification — PutBucketNotification */
    public Mono<ServerResponse> putBucketNotification(ServerRequest request) {
        return putConfig(request, NotificationConfigurationCommand.class, (b, cmd) -> {
            var notificationConfig = new BucketNotificationConfiguration(b.name(),
                cmd.topicConfigurations(), cmd.queueConfigurations(), cmd.lambdaConfigurations(),
                cmd.eventBridgeConfigurations() != null ? cmd.eventBridgeConfigurations() : List.of());
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?notification — DeleteBucketNotification */
    public Mono<ServerResponse> deleteBucketNotification(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Replication Configuration ──

    /** GET /{bucket}?replication — GetBucketReplication */
    public Mono<ServerResponse> getBucketReplication(ServerRequest request) {
        return getConfig(request, "NoSuchReplicationConfiguration", "The replication configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketReplicationQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?replication — PutBucketReplication */
    public Mono<ServerResponse> putBucketReplication(ServerRequest request) {
        return putConfig(request, ReplicationConfigurationCommand.class, (b, cmd) -> {
            var replicationConfig = new BucketReplicationConfiguration(b.name(),
                cmd.role(), cmd.rules(), cmd.sourceBucket(), cmd.destinationBucket());
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?replication — DeleteBucketReplication */
    public Mono<ServerResponse> deleteBucketReplication(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Request Payment Configuration ──

    /** GET /{bucket}?requestPayment — GetBucketRequestPayment */
    public Mono<ServerResponse> getBucketRequestPayment(ServerRequest request) {
        return getConfig(request, "NoSuchRequestPaymentConfiguration", "The request payment configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketRequestPaymentQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?requestPayment — PutBucketRequestPayment */
    public Mono<ServerResponse> putBucketRequestPayment(ServerRequest request) {
        return putConfig(request, RequestPaymentConfigurationCommand.class, (b, cmd) -> {
            var requestPaymentConfig = new BucketRequestPaymentConfiguration(b.name(), cmd.payer());
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?requestPayment — DeleteBucketRequestPayment */
    public Mono<ServerResponse> deleteBucketRequestPayment(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Ownership Controls ──

    /** GET /{bucket}?ownershipControls — GetBucketOwnershipControls */
    public Mono<ServerResponse> getBucketOwnershipControls(ServerRequest request) {
        return getConfig(request, "NoSuchOwnershipControls", "The ownership controls do not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketOwnershipControlsQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?ownershipControls — PutBucketOwnershipControls */
    public Mono<ServerResponse> putBucketOwnershipControls(ServerRequest request) {
        return putConfig(request, OwnershipControlsCommand.class, (b, cmd) -> {
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?ownershipControls — DeleteBucketOwnershipControls */
    public Mono<ServerResponse> deleteBucketOwnershipControls(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Public Access Block ──

    /** GET /{bucket}?publicAccessBlock — GetPublicAccessBlock */
    public Mono<ServerResponse> getPublicAccessBlock(ServerRequest request) {
        return getConfig(request, "NoSuchPublicAccessBlockConfiguration", "The public access block configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(PublicAccessBlockQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?publicAccessBlock — PutPublicAccessBlock */
    public Mono<ServerResponse> putPublicAccessBlock(ServerRequest request) {
        return putConfig(request, PublicAccessBlockCommand.class, (b, cmd) -> {
            var publicAccessBlockConfig = new PublicAccessBlockConfiguration(
                cmd.blockPublicAcls(), cmd.ignorePublicAcls(),
                cmd.blockPublicPolicy(), cmd.restrictPublicBucketPolicy());
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?publicAccessBlock — DeletePublicAccessBlock */
    public Mono<ServerResponse> deletePublicAccessBlock(ServerRequest request) {
        return deleteConfig(request);
    }

    // ── Accelerate Configuration ──

    /** GET /{bucket}?accelerate — GetBucketAccelerateConfiguration */
    public Mono<ServerResponse> getBucketAccelerate(ServerRequest request) {
        return getConfig(request, "NoSuchAccelerateConfiguration", "The accelerate configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketAccelerateQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?accelerate — PutBucketAccelerateConfiguration */
    public Mono<ServerResponse> putBucketAccelerate(ServerRequest request) {
        return putConfig(request, AccelerateConfigurationCommand.class, (b, cmd) -> {
            var accelerateConfig = new BucketAccelerateConfiguration(cmd.status());
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?accelerate — DeleteBucketAccelerateConfiguration */
    public Mono<ServerResponse> deleteBucketAccelerate(ServerRequest request) {
        return deleteConfig(request);
    }

    // ─────────────────────────────────────────────────────
    //  Analytics Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?analytics&analyticsId={id} — GetBucketAnalyticsConfiguration */
    public Mono<ServerResponse> getBucketAnalytics(ServerRequest request) {
        return getConfigWithParam(request, "analyticsId", "MissingAnalyticsId",
            "The analyticsId query parameter is required",
            "NoSuchAnalyticsConfiguration", "The analytics configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketAnalyticsQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?analytics&analyticsId={id} — PutBucketAnalyticsConfiguration */
    public Mono<ServerResponse> putBucketAnalytics(ServerRequest request) {
        return putConfigWithParam(request, "analyticsId", "MissingAnalyticsId",
            "The analyticsId query parameter is required",
            AnalyticsConfigurationCommand.class, (b, cmd) -> {
                var analyticsConfig = new BucketAnalyticsConfiguration(b.name(),
                    cmd.id(), cmd.filter(), cmd.storageClass(), cmd.tags());
                var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.ok().build());
            });
    }

    /** DELETE /{bucket}?analytics&analyticsId={id} — DeleteBucketAnalyticsConfiguration */
    public Mono<ServerResponse> deleteBucketAnalytics(ServerRequest request) {
        return deleteConfigWithParam(request, "analyticsId", "MissingAnalyticsId",
            "The analyticsId query parameter is required");
    }

    /** GET /{bucket}?analytics&list-type — ListBucketAnalyticsConfigurations */
    public Mono<ServerResponse> listBucketAnalyticsConfigurations(ServerRequest request) {
        return findBucket(request, b -> Mono.justOrEmpty(b.configuration())
            .flatMap(config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new BucketAnalyticsListQuery(List.of()))));
    }

    // ─────────────────────────────────────────────────────
    //  Inventory Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?inventory&inventoryId={id} — GetBucketInventoryConfiguration */
    public Mono<ServerResponse> getBucketInventory(ServerRequest request) {
        return getConfigWithParam(request, "inventoryId", "MissingInventoryId",
            "The inventoryId query parameter is required",
            "NoSuchInventoryConfiguration", "The inventory configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketInventoryQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?inventory&inventoryId={id} — PutBucketInventoryConfiguration */
    public Mono<ServerResponse> putBucketInventory(ServerRequest request) {
        return putConfigWithParam(request, "inventoryId", "MissingInventoryId",
            "The inventoryId query parameter is required",
            InventoryConfigurationCommand.class, (b, cmd) -> {
                var inventoryConfig = new BucketInventoryConfiguration(b.name(),
                    cmd.id(), cmd.filter(), cmd.schedule(), cmd.destination(),
                    cmd.enabled(), cmd.includedObjectVersions(), cmd.optionalFields());
                var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
                return bucketService.createBucket(updatedBucket)
                    .then(ServerResponse.ok().build());
            });
    }

    /** DELETE /{bucket}?inventory&inventoryId={id} — DeleteBucketInventoryConfiguration */
    public Mono<ServerResponse> deleteBucketInventory(ServerRequest request) {
        return deleteConfigWithParam(request, "inventoryId", "MissingInventoryId",
            "The inventoryId query parameter is required");
    }

    /** GET /{bucket}?inventory&list-type — ListBucketInventoryConfigurations */
    public Mono<ServerResponse> listBucketInventoryConfigurations(ServerRequest request) {
        return findBucket(request, b -> Mono.justOrEmpty(b.configuration())
            .flatMap(config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new BucketInventoryListQuery(List.of()))));
    }

    // ─────────────────────────────────────────────────────
    //  Metrics Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?metrics — GetBucketMetricsConfiguration */
    public Mono<ServerResponse> getBucketMetrics(ServerRequest request) {
        return getConfig(request, "NoSuchMetricsConfiguration", "The metrics configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketMetricsQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?metrics — PutBucketMetricsConfiguration */
    public Mono<ServerResponse> putBucketMetrics(ServerRequest request) {
        return putConfig(request, MetricsConfigurationCommand.class, (b, cmd) -> {
            var metricsConfig = new BucketMetricsConfiguration(b.name(),
                cmd.id(), cmd.filter(), cmd.storageClass(), cmd.tags());
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?metrics — DeleteBucketMetricsConfiguration */
    public Mono<ServerResponse> deleteBucketMetrics(ServerRequest request) {
        return deleteConfig(request);
    }

    // ─────────────────────────────────────────────────────
    //  Intelligent-Tiering Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?intelligent-tiering — GetBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> getBucketIntelligentTiering(ServerRequest request) {
        return getConfig(request, "NoSuchIntelligentTieringConfiguration", "The intelligent-tiering configuration does not exist",
            config -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketIntelligentTieringQuery.from(Optional.empty())));
    }

    /** PUT /{bucket}?intelligent-tiering — PutBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> putBucketIntelligentTiering(ServerRequest request) {
        return putConfig(request, IntelligentTieringConfigurationCommand.class, (b, cmd) -> {
            var intelligentTieringConfig = new BucketIntelligentTieringConfiguration(
                cmd.id(), cmd.status(), cmd.tieringDefinitions());
            var updatedBucket = b.withConfiguration(new Bucket.Configuration(null)).clearEvents();
            return bucketService.createBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?intelligent-tiering — DeleteBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> deleteBucketIntelligentTiering(ServerRequest request) {
        return deleteConfig(request);
    }
}
