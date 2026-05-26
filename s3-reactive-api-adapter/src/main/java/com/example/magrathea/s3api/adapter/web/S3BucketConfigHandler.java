package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.BucketAccelerateConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketAnalyticsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.BucketEncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketIntelligentTieringConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketInventoryConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLifecycleConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLoggingConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetricsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketNotificationConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketReplicationConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketRequestPaymentConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketWebsiteConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.PublicAccessBlockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketOwnershipControls;
import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.s3api.dto.command.AbacConfigurationCommand;
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
import com.example.magrathea.s3api.dto.command.ObjectLockConfigurationCommand;
import com.example.magrathea.s3api.dto.command.InventoryTableConfigurationCommand;
import com.example.magrathea.s3api.dto.command.JournalTableConfigurationCommand;
import com.example.magrathea.s3api.dto.command.MetadataConfigurationCommand;
import com.example.magrathea.s3api.dto.command.MetadataTableConfigurationCommand;
import com.example.magrathea.s3api.dto.query.InventoryTableConfigurationQuery;
import com.example.magrathea.s3api.dto.query.JournalTableConfigurationQuery;
import com.example.magrathea.s3api.dto.query.MetadataConfigurationQuery;
import com.example.magrathea.s3api.dto.query.MetadataTableConfigurationQuery;
import com.example.magrathea.s3api.dto.query.ObjectLockConfigurationQuery;
import com.example.magrathea.s3api.dto.query.AbacConfigurationQuery;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Bucket configuration operations: CORS, policy, encryption, logging, website, notification,
 * accelerate, analytics, inventory, metrics, intelligent-tiering, ownership controls, public access block,
 * request payment, replication, lifecycle.
 *
 * Uses Jackson XML codec for request body deserialization and response serialization.
 * All operations are fully reactive — no blocking, no CompletableFuture bridging.
 *
 * Configuration storage uses the unified BucketConfig value object — each config type
 * is stored as a nullable field on BucketConfig, accessed via getXxxConfiguration() and
 * set via withXxxConfiguration() on BucketConfig, then applied to the Bucket aggregate
 * via withBucketConfig(BucketConfig).
 *
 * Generic helper methods eliminate duplication by extracting the common find-bucket → apply → respond pattern.
 */
public class S3BucketConfigHandler {

    private final ReactiveBucketService bucketService;
    private final Map<String, List<AbacConfigEntry>> abacConfigs = new ConcurrentHashMap<>();

    private final Map<String, ObjectLockConfigEntry> objectLockConfigs = new ConcurrentHashMap<>();

    private final Map<String, MetadataConfigEntry> metadataConfigs = new ConcurrentHashMap<>();
    private final Map<String, MetadataTableConfigEntry> metadataTableConfigs = new ConcurrentHashMap<>();
    private final Map<String, InventoryTableConfigEntry> inventoryTableConfigs = new ConcurrentHashMap<>();
    private final Map<String, JournalTableConfigEntry> journalTableConfigs = new ConcurrentHashMap<>();

    /**
     * In-memory object lock configuration entry — stored per bucket.
     */
    private record ObjectLockConfigEntry(boolean enabled, String mode, int days) {}

    public S3BucketConfigHandler(ReactiveBucketService bucketService) {
        this.bucketService = bucketService;
    }

    public void resetInMemoryConfigurations() {
        abacConfigs.clear();
        objectLockConfigs.clear();
        metadataConfigs.clear();
        metadataTableConfigs.clear();
        inventoryTableConfigs.clear();
        journalTableConfigs.clear();
    }

    /**
     * In-memory ABAC configuration entry — stored per bucket.
     */
    private record AbacConfigEntry(String id, String principal, String resource, String action,
                                    List<AbacCondition> conditions) {
        private record AbacCondition(String tag, String value) {}
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

    /** Helper for PUT config — finds bucket, parses command body, applies handler. */
    private <T> Mono<ServerResponse> putConfig(ServerRequest request,
                                               Class<T> commandClass,
                                               BiFunction<Bucket, T, Mono<ServerResponse>> handler) {
        return findBucket(request, b -> request.bodyToMono(commandClass)
            .flatMap(cmd -> handler.apply(b, cmd)));
    }

    /** Helper for PUT with String body (not a typed command). */
    private Mono<ServerResponse> putConfigString(ServerRequest request,
                                                 BiFunction<Bucket, String, Mono<ServerResponse>> handler) {
        return findBucket(request, b -> request.bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> handler.apply(b, body)));
    }

    /** Helper for GET config from a dedicated config field — checks field presence, maps to response. */
    private <T> Mono<ServerResponse> getDedicatedConfig(ServerRequest request,
                                                         String errorCode,
                                                         String errorMessage,
                                                         Function<Bucket, Optional<T>> configExtractor,
                                                         Function<T, Mono<ServerResponse>> responseMapper) {
        return findBucket(request, b -> {
            var config = configExtractor.apply(b);
            if (config.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, errorCode, errorMessage);
            }
            return responseMapper.apply(config.get());
        });
    }

    /** Helper for DELETE dedicated config — sets the specific config field to null via BucketConfig. */
    private Mono<ServerResponse> deleteDedicatedConfig(ServerRequest request,
                                                        Function<BucketConfig, BucketConfig> clearFunction) {
        return findBucket(request, b -> {
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = clearFunction.apply(baseConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.noContent().build());
        });
    }

    // ── CORS Configuration ──

    /** GET /{bucket}?cors — GetBucketCors */
    public Mono<ServerResponse> getBucketCors(ServerRequest request) {
        return findBucket(request, b -> {
            var opt = b.bucketConfig() != null
                ? b.bucketConfig().getCorsConfiguration() : Optional.<CorsConfiguration>empty();
            if (opt.isEmpty() || opt.get().corsRules() == null || opt.get().corsRules().isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND,
                    "NoSuchCorsConfiguration", "The CORS configuration is not found");
            }
            var corsConfig = opt.get();
            var rules = corsConfig.corsRules().stream()
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
                .map(r -> new CorsConfiguration.CorsRule(
                    r.allowedOrigins(), r.allowedMethods(), r.allowedHeaders(),
                    r.maxAgeSeconds() != null ? r.maxAgeSeconds() : 0,
                    r.exposeHeaders(), r.id()))
                .toList();
            var corsConfig = new CorsConfiguration(corsRules);
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withCorsConfiguration(corsConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?cors — DeleteBucketCors */
    public Mono<ServerResponse> deleteBucketCors(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), null, bc.bucketPolicy()));
    }

    // ── Lifecycle Configuration ──

    /** GET /{bucket}?lifecycle — GetBucketLifecycleConfiguration */
    public Mono<ServerResponse> getBucketLifecycle(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchLifecycleConfiguration", "The lifecycle configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getLifecycleConfiguration() : Optional.<BucketLifecycleConfiguration>empty(),
            lc -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketLifecycleQuery.from(Optional.of(lc))));
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
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withLifecycleConfiguration(lifecycleConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?lifecycle — DeleteBucketLifecycleConfiguration */
    public Mono<ServerResponse> deleteBucketLifecycle(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                null, bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ── Bucket Policy ──

    /** GET /{bucket}?policy — GetBucketPolicy */
    public Mono<ServerResponse> getBucketPolicy(ServerRequest request) {
        return findBucket(request, b -> {
            var policy = b.bucketConfig() != null
                ? b.bucketConfig().getBucketPolicy() : Optional.empty();
            if (policy.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucketPolicy", "The bucket policy does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(policy.get());
        });
    }

    /** PUT /{bucket}?policy — PutBucketPolicy */
    public Mono<ServerResponse> putBucketPolicy(ServerRequest request) {
        return putConfigString(request, (b, body) -> {
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withBucketPolicy(body);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?policy — DeleteBucketPolicy */
    public Mono<ServerResponse> deleteBucketPolicy(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), null));
    }

    // ── Encryption Configuration ──

    /** GET /{bucket}?encryption — GetBucketEncryption */
    public Mono<ServerResponse> getBucketEncryption(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchEncryptionConfiguration", "The encryption configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getEncryptionConfiguration() : Optional.<BucketEncryptionConfiguration>empty(),
            ec -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketEncryptionQuery.from(Optional.of(ec))));
    }

    /** PUT /{bucket}?encryption — PutBucketEncryption */
    public Mono<ServerResponse> putBucketEncryption(ServerRequest request) {
        return putConfig(request, EncryptionConfigurationCommand.class, (b, cmd) -> {
            var encryptionConfig = new BucketEncryptionConfiguration(b.name(),
                cmd.ruleId(), cmd.algorithm(), cmd.kmsKeyId());
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withEncryptionConfiguration(encryptionConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?encryption — DeleteBucketEncryption */
    public Mono<ServerResponse> deleteBucketEncryption(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), null,
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ── Logging Configuration ──

    /** GET /{bucket}?logging — GetBucketLogging */
    public Mono<ServerResponse> getBucketLogging(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchLoggingConfiguration", "The logging configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getLoggingConfiguration() : Optional.<BucketLoggingConfiguration>empty(),
            lc -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketLoggingQuery.from(Optional.of(lc))));
    }

    /** PUT /{bucket}?logging — PutBucketLogging */
    public Mono<ServerResponse> putBucketLogging(ServerRequest request) {
        return putConfig(request, LoggingConfigurationCommand.class, (b, cmd) -> {
            var loggingConfig = new BucketLoggingConfiguration(b.name(),
                cmd.targetBucket(), cmd.targetPrefix());
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withLoggingConfiguration(loggingConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?logging — DeleteBucketLogging */
    public Mono<ServerResponse> deleteBucketLogging(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                null, bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ── Website Configuration ──

    /** GET /{bucket}?website — GetBucketWebsite */
    public Mono<ServerResponse> getBucketWebsite(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchWebsiteConfiguration", "The website configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getWebsiteConfiguration() : Optional.<BucketWebsiteConfiguration>empty(),
            wc -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketWebsiteQuery.from(Optional.of(wc))));
    }

    /** PUT /{bucket}?website — PutBucketWebsite */
    public Mono<ServerResponse> putBucketWebsite(ServerRequest request) {
        return putConfig(request, WebsiteConfigurationCommand.class, (b, cmd) -> {
            var websiteConfig = new BucketWebsiteConfiguration(b.name(),
                cmd.indexDocument(), cmd.errorDocument(),
                cmd.redirectAllRequestsTo(), cmd.hostName(), cmd.protocol());
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withWebsiteConfiguration(websiteConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?website — DeleteBucketWebsite */
    public Mono<ServerResponse> deleteBucketWebsite(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), null, bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ── Notification Configuration ──

    /** GET /{bucket}?notification — GetBucketNotification */
    public Mono<ServerResponse> getBucketNotification(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchNotificationConfiguration", "The notification configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getNotificationConfiguration() : Optional.<BucketNotificationConfiguration>empty(),
            nc -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketNotificationQuery.from(Optional.of(nc))));
    }

    /** PUT /{bucket}?notification — PutBucketNotification */
    public Mono<ServerResponse> putBucketNotification(ServerRequest request) {
        return putConfig(request, NotificationConfigurationCommand.class, (b, cmd) -> {
            var events = cmd.eventConfigurations().stream()
                .map(ec -> new BucketNotificationConfiguration.NotificationEvent(
                    ec.event(), ec.topicArn(), ec.queueArn(), ec.lambdaArn(),
                    ec.filterRules() != null ? ec.filterRules() : List.of()))
                .toList();
            var notificationConfig = new BucketNotificationConfiguration(b.name(), events);
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withNotificationConfiguration(notificationConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?notification — DeleteBucketNotification */
    public Mono<ServerResponse> deleteBucketNotification(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), null,
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ── Replication Configuration ──

    /** GET /{bucket}?replication — GetBucketReplication */
    public Mono<ServerResponse> getBucketReplication(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchReplicationConfiguration", "The replication configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getReplicationConfiguration() : Optional.<BucketReplicationConfiguration>empty(),
            rc -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketReplicationQuery.from(Optional.of(rc))));
    }

    /** PUT /{bucket}?replication — PutBucketReplication */
    public Mono<ServerResponse> putBucketReplication(ServerRequest request) {
        return putConfig(request, ReplicationConfigurationCommand.class, (b, cmd) -> {
            var replicationRules = cmd.rules().stream()
                .map(r -> new BucketReplicationConfiguration.ReplicationRule(
                    r.id(), r.status(), r.prefix(), r.destinationBucket(),
                    r.destinationStorageClass(), false, false))
                .toList();
            var replicationConfig = new BucketReplicationConfiguration(b.name(),
                cmd.role(), replicationRules);
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withReplicationConfiguration(replicationConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?replication — DeleteBucketReplication */
    public Mono<ServerResponse> deleteBucketReplication(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), null, bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ── Request Payment Configuration ──

    /** GET /{bucket}?requestPayment — GetBucketRequestPayment */
    public Mono<ServerResponse> getBucketRequestPayment(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchRequestPaymentConfiguration", "The request payment configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getRequestPaymentConfiguration() : Optional.<BucketRequestPaymentConfiguration>empty(),
            rpc -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketRequestPaymentQuery.from(Optional.of(rpc))));
    }

    /** PUT /{bucket}?requestPayment — PutBucketRequestPayment */
    public Mono<ServerResponse> putBucketRequestPayment(ServerRequest request) {
        return putConfig(request, RequestPaymentConfigurationCommand.class, (b, cmd) -> {
            var requestPaymentConfig = new BucketRequestPaymentConfiguration(b.name(), cmd.payer());
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withRequestPaymentConfiguration(requestPaymentConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?requestPayment — DeleteBucketRequestPayment */
    public Mono<ServerResponse> deleteBucketRequestPayment(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), null,
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ── Ownership Controls ──

    /** GET /{bucket}?ownershipControls — GetBucketOwnershipControls */
    public Mono<ServerResponse> getBucketOwnershipControls(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchOwnershipControls", "The ownership controls do not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getOwnershipControls() : Optional.<BucketOwnershipControls>empty(),
            oc -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketOwnershipControlsQuery.from(Optional.of(oc))));
    }

    /** PUT /{bucket}?ownershipControls — PutBucketOwnershipControls */
    public Mono<ServerResponse> putBucketOwnershipControls(ServerRequest request) {
        return putConfig(request, OwnershipControlsCommand.class, (b, cmd) -> {
            var ownershipControls = new BucketOwnershipControls(b.name(), cmd.id(), cmd.ownership());
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withOwnershipControls(ownershipControls);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?ownershipControls — DeleteBucketOwnershipControls */
    public Mono<ServerResponse> deleteBucketOwnershipControls(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                null, bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ── Public Access Block ──

    /** GET /{bucket}?publicAccessBlock — GetPublicAccessBlock */
    public Mono<ServerResponse> getPublicAccessBlock(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchPublicAccessBlockConfiguration", "The public access block configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getPublicAccessBlockConfiguration() : Optional.<PublicAccessBlockConfiguration>empty(),
            pab -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(PublicAccessBlockQuery.from(Optional.of(pab))));
    }

    /** PUT /{bucket}?publicAccessBlock — PutPublicAccessBlock */
    public Mono<ServerResponse> putPublicAccessBlock(ServerRequest request) {
        return putConfig(request, PublicAccessBlockCommand.class, (b, cmd) -> {
            var publicAccessBlockConfig = new PublicAccessBlockConfiguration(b.name(),
                "true".equals(cmd.blockPublicAcls()), "true".equals(cmd.ignorePublicAcls()),
                "true".equals(cmd.blockPublicPolicy()), "true".equals(cmd.restrictPublicBuckets()));
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withPublicAccessBlock(publicAccessBlockConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?publicAccessBlock — DeletePublicAccessBlock */
    public Mono<ServerResponse> deletePublicAccessBlock(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), null, bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ── Accelerate Configuration ──

    /** GET /{bucket}?accelerate — GetBucketAccelerateConfiguration */
    public Mono<ServerResponse> getBucketAccelerate(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchAccelerateConfiguration", "The accelerate configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getAccelerateConfiguration() : Optional.<BucketAccelerateConfiguration>empty(),
            ac -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketAccelerateQuery.from(Optional.of(ac))));
    }

    /** PUT /{bucket}?accelerate — PutBucketAccelerateConfiguration */
    public Mono<ServerResponse> putBucketAccelerate(ServerRequest request) {
        return putConfig(request, AccelerateConfigurationCommand.class, (b, cmd) -> {
            var accelerateConfig = new BucketAccelerateConfiguration(b.name(), cmd.status());
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withAccelerateConfiguration(accelerateConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?accelerate — DeleteBucketAccelerateConfiguration */
    public Mono<ServerResponse> deleteBucketAccelerate(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                null, bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ─────────────────────────────────────────────────────
    //  Analytics Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?analytics&analyticsId={id} — GetBucketAnalyticsConfiguration */
    public Mono<ServerResponse> getBucketAnalytics(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchAnalyticsConfiguration", "The analytics configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getAnalyticsConfiguration() : Optional.<BucketAnalyticsConfiguration>empty(),
            ac -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketAnalyticsQuery.from(Optional.of(ac))));
    }

    /** PUT /{bucket}?analytics&analyticsId={id} — PutBucketAnalyticsConfiguration */
    public Mono<ServerResponse> putBucketAnalytics(ServerRequest request) {
        var analyticsId = request.queryParam("analyticsId").orElse(null);
        if (analyticsId == null || analyticsId.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingAnalyticsId",
                "The analyticsId query parameter is required");
        }
        return putConfig(request, AnalyticsConfigurationCommand.class, (b, cmd) -> {
            var analyticsConfig = new BucketAnalyticsConfiguration(b.name(),
                cmd.id(), cmd.filter() != null ? cmd.filter().prefix() : null);
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withAnalyticsConfiguration(analyticsConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?analytics&analyticsId={id} — DeleteBucketAnalyticsConfiguration */
    public Mono<ServerResponse> deleteBucketAnalytics(ServerRequest request) {
        var analyticsId = request.queryParam("analyticsId").orElse(null);
        if (analyticsId == null || analyticsId.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingAnalyticsId",
                "The analyticsId query parameter is required");
        }
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), null, bc.inventoryConfiguration(),
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    /** GET /{bucket}?analytics&list-type — ListBucketAnalyticsConfigurations */
    public Mono<ServerResponse> listBucketAnalyticsConfigurations(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchAnalyticsConfiguration", "No analytics configurations exist",
            b -> b.bucketConfig() != null
                ? b.bucketConfig().getAnalyticsConfiguration().map(ac -> List.of(ac)) : Optional.<List<BucketAnalyticsConfiguration>>empty(),
            list -> {
                var entries = list.stream()
                    .map(ac -> new BucketAnalyticsListQuery.AnalyticsConfigurationEntry(ac.analyticsId()))
                    .toList();
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(new BucketAnalyticsListQuery(entries));
            });
    }

    // ─────────────────────────────────────────────────────
    //  Inventory Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?inventory&inventoryId={id} — GetBucketInventoryConfiguration */
    public Mono<ServerResponse> getBucketInventory(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchInventoryConfiguration", "The inventory configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getInventoryConfiguration() : Optional.<BucketInventoryConfiguration>empty(),
            ic -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketInventoryQuery.from(Optional.of(ic))));
    }

    /** PUT /{bucket}?inventory&inventoryId={id} — PutBucketInventoryConfiguration */
    public Mono<ServerResponse> putBucketInventory(ServerRequest request) {
        var inventoryId = request.queryParam("inventoryId").orElse(null);
        if (inventoryId == null || inventoryId.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingInventoryId",
                "The inventoryId query parameter is required");
        }
        return putConfig(request, InventoryConfigurationCommand.class, (b, cmd) -> {
            var inventoryConfig = new BucketInventoryConfiguration(b.name(),
                cmd.id(), cmd.destination() != null ? cmd.destination().format() : null,
                cmd.schedule() != null ? cmd.schedule().frequency() : null,
                "Enabled".equals(cmd.enabled()));
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withInventoryConfiguration(inventoryConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?inventory&inventoryId={id} — DeleteBucketInventoryConfiguration */
    public Mono<ServerResponse> deleteBucketInventory(ServerRequest request) {
        var inventoryId = request.queryParam("inventoryId").orElse(null);
        if (inventoryId == null || inventoryId.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingInventoryId",
                "The inventoryId query parameter is required");
        }
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), null,
                bc.metricsConfiguration(), bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    /** GET /{bucket}?inventory&list-type — ListBucketInventoryConfigurations */
    public Mono<ServerResponse> listBucketInventoryConfigurations(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchInventoryConfiguration", "No inventory configurations exist",
            b -> b.bucketConfig() != null
                ? b.bucketConfig().getInventoryConfiguration().map(ic -> List.of(ic)) : Optional.<List<BucketInventoryConfiguration>>empty(),
            list -> {
                var entries = list.stream()
                    .map(ic -> new BucketInventoryListQuery.InventoryConfigurationEntry(ic.inventoryId()))
                    .toList();
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(new BucketInventoryListQuery(entries));
            });
    }

    // ─────────────────────────────────────────────────────
    //  Metrics Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?metrics — GetBucketMetricsConfiguration */
    public Mono<ServerResponse> getBucketMetrics(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchMetricsConfiguration", "The metrics configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getMetricsConfiguration() : Optional.<BucketMetricsConfiguration>empty(),
            mc -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketMetricsQuery.from(Optional.of(mc))));
    }

    /** PUT /{bucket}?metrics — PutBucketMetricsConfiguration */
    public Mono<ServerResponse> putBucketMetrics(ServerRequest request) {
        return putConfig(request, MetricsConfigurationCommand.class, (b, cmd) -> {
            var metricsConfig = new BucketMetricsConfiguration(b.name(),
                cmd.id(), cmd.filter() != null ? cmd.filter().prefix() : null);
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withMetricsConfiguration(metricsConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?metrics — DeleteBucketMetricsConfiguration */
    public Mono<ServerResponse> deleteBucketMetrics(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                null, bc.intelligentTieringConfiguration(), bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ─────────────────────────────────────────────────────
    //  Intelligent-Tiering Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?intelligent-tiering — GetBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> getBucketIntelligentTiering(ServerRequest request) {
        return getDedicatedConfig(request, "NoSuchIntelligentTieringConfiguration", "The intelligent-tiering configuration does not exist",
            b -> b.bucketConfig() != null ? b.bucketConfig().getIntelligentTieringConfiguration() : Optional.<BucketIntelligentTieringConfiguration>empty(),
            itc -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketIntelligentTieringQuery.from(Optional.of(itc))));
    }

    /** PUT /{bucket}?intelligent-tiering — PutBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> putBucketIntelligentTiering(ServerRequest request) {
        return putConfig(request, IntelligentTieringConfigurationCommand.class, (b, cmd) -> {
            var intelligentTieringConfig = new BucketIntelligentTieringConfiguration(b.name(),
                cmd.id(), cmd.tieringPolicy() != null ? cmd.tieringPolicy().tieringRule() : null,
                cmd.autoTieringStatus());
            var baseConfig = b.bucketConfig() != null ? b.bucketConfig() : BucketConfig.EMPTY;
            var newConfig = baseConfig.withIntelligentTieringConfiguration(intelligentTieringConfig);
            var updatedBucket = b.withBucketConfig(newConfig).clearEvents();
            return bucketService.updateBucket(updatedBucket)
                .then(ServerResponse.ok().build());
        });
    }

    /** DELETE /{bucket}?intelligent-tiering — DeleteBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> deleteBucketIntelligentTiering(ServerRequest request) {
        return deleteDedicatedConfig(request,
            bc -> new BucketConfig(
                bc.loggingConfiguration(), bc.websiteConfiguration(), bc.notificationConfiguration(),
                bc.accelerateConfiguration(), bc.analyticsConfiguration(), bc.inventoryConfiguration(),
                bc.metricsConfiguration(), null, bc.encryptionConfiguration(),
                bc.lifecycleConfiguration(), bc.replicationConfiguration(), bc.requestPaymentConfiguration(),
                bc.ownershipControls(), bc.publicAccessBlockConfiguration(), bc.corsConfiguration(), bc.bucketPolicy()));
    }

    // ─────────────────────────────────────────────────────
    //  ABAC Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?abac — GetBucketAbac */
    public Mono<ServerResponse> getBucketAbac(ServerRequest request) {
        return findBucket(request, b -> {
            var entries = abacConfigs.get(b.name());
            if (entries == null || entries.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchAbacConfiguration",
                    "The ABAC configuration does not exist");
            }
            var rules = entries.stream()
                .map(e -> new AbacConfigurationQuery.AbacRuleEntry(
                    e.id(), e.principal(), e.resource(), e.action(),
                    e.conditions().stream()
                        .map(c -> new AbacConfigurationQuery.ConditionEntry(c.tag(), c.value()))
                        .toList()))
                .toList();
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new AbacConfigurationQuery(rules));
        });
    }

    // ─────────────────────────────────────────────────────
    //  Object Lock Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?object-lock — GetObjectLockConfiguration */
    public Mono<ServerResponse> getObjectLockConfiguration(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return bucketService.findByName(bucketName)
            .flatMap(b -> {
                var config = objectLockConfigs.get(bucketName);
                if (config == null || !config.enabled()) {
                    return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchObjectLockConfiguration",
                        "The object lock configuration does not exist");
                }
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(ObjectLockConfigurationQuery.from(config.mode(), config.days()));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?object-lock — PutObjectLockConfiguration */
    public Mono<ServerResponse> putObjectLockConfiguration(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return bucketService.findByName(bucketName)
            .flatMap(b -> request.bodyToMono(ObjectLockConfigurationCommand.class)
                .flatMap(cmd -> {
                    if (!cmd.isEnabled()) {
                        objectLockConfigs.remove(bucketName);
                        return ServerResponse.ok().build();
                    }
                    var mode = cmd.rule() != null && cmd.rule().defaultRetention() != null
                        ? cmd.rule().defaultRetention().mode() : "GOVERNANCE";
                    var days = cmd.rule() != null && cmd.rule().defaultRetention() != null
                        ? cmd.rule().defaultRetention().days() != null ? cmd.rule().defaultRetention().days() : 5
                        : 5;
                    objectLockConfigs.put(bucketName, new ObjectLockConfigEntry(true, mode, days));
                    return ServerResponse.ok().build();
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    /** PUT /{bucket}?abac — PutBucketAbac */
    public Mono<ServerResponse> putBucketAbac(ServerRequest request) {
        return findBucket(request, b -> request.bodyToMono(AbacConfigurationCommand.class)
            .flatMap(cmd -> {
                var entries = cmd.rules().stream()
                    .map(r -> new AbacConfigEntry(
                        r.id(), r.principal(), r.resource(), r.action(),
                        r.conditions() != null
                            ? r.conditions().stream()
                                .map(c -> new AbacConfigEntry.AbacCondition(c.tag(), c.value()))
                                .toList()
                            : List.of()))
                    .toList();
                abacConfigs.put(b.name(), entries);
                return ServerResponse.ok().build();
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidRequest",
                "Missing ABAC configuration body")));
    }

    // ─────────────────────────────────────────────────────
    //  Metadata Configuration
    // ─────────────────────────────────────────────────────

    private record MetadataConfigEntry(List<MetadataConfigRule> rules) {
        private record MetadataConfigRule(String id, String status,
                                           String metadataResourceType,
                                           String metadataResourceSubtype) {}
    }

    /** GET /{bucket}?metadata-config — GetBucketMetadataConfiguration */
    public Mono<ServerResponse> getBucketMetadataConfiguration(ServerRequest request) {
        return findBucket(request, b -> {
            var entry = metadataConfigs.get(b.name());
            if (entry == null || entry.rules() == null || entry.rules().isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchMetadataConfiguration",
                    "The metadata configuration does not exist");
            }
            var rules = entry.rules().stream()
                .map(r -> new MetadataConfigurationQuery.MetadataRuleEntry(
                    r.id(), r.status(), r.metadataResourceType(), r.metadataResourceSubtype()))
                .toList();
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new MetadataConfigurationQuery(rules));
        });
    }

    /** PUT /{bucket}?metadata-config — PutBucketMetadataConfiguration */
    public Mono<ServerResponse> putBucketMetadataConfiguration(ServerRequest request) {
        return findBucket(request, b -> request.bodyToMono(MetadataConfigurationCommand.class)
            .flatMap(cmd -> {
                var rules = cmd.rules().stream()
                    .map(r -> new MetadataConfigEntry.MetadataConfigRule(
                        r.id(), r.status(), r.metadataResourceType(), r.metadataResourceSubtype()))
                    .toList();
                metadataConfigs.put(b.name(), new MetadataConfigEntry(rules));
                return ServerResponse.ok().build();
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidRequest",
                "Missing metadata configuration body")));
    }

    /** DELETE /{bucket}?metadata-config — DeleteBucketMetadataConfiguration */
    public Mono<ServerResponse> deleteBucketMetadataConfiguration(ServerRequest request) {
        return findBucket(request, b -> {
            metadataConfigs.remove(b.name());
            return ServerResponse.noContent().build();
        });
    }

    // ─────────────────────────────────────────────────────
    //  Metadata Table Configuration
    // ─────────────────────────────────────────────────────

    private record MetadataTableConfigEntry(List<MetadataTableConfigRule> rules) {
        private record MetadataTableConfigRule(String id, String status,
                                                String metadataTableName,
                                                String metadataTableDatabase) {}
    }

    /** GET /{bucket}?metadata-table-config — GetBucketMetadataTableConfiguration */
    public Mono<ServerResponse> getBucketMetadataTableConfiguration(ServerRequest request) {
        return findBucket(request, b -> {
            var entry = metadataTableConfigs.get(b.name());
            if (entry == null || entry.rules() == null || entry.rules().isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchMetadataTableConfiguration",
                    "The metadata table configuration does not exist");
            }
            var rules = entry.rules().stream()
                .map(r -> new MetadataTableConfigurationQuery.MetadataTableRuleEntry(
                    r.id(), r.status(), r.metadataTableName(), r.metadataTableDatabase()))
                .toList();
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new MetadataTableConfigurationQuery(rules));
        });
    }

    /** PUT /{bucket}?metadata-table-config — PutBucketMetadataTableConfiguration */
    public Mono<ServerResponse> putBucketMetadataTableConfiguration(ServerRequest request) {
        return findBucket(request, b -> request.bodyToMono(MetadataTableConfigurationCommand.class)
            .flatMap(cmd -> {
                var rules = cmd.rules().stream()
                    .map(r -> new MetadataTableConfigEntry.MetadataTableConfigRule(
                        r.id(), r.status(), r.metadataTableName(), r.metadataTableDatabase()))
                    .toList();
                metadataTableConfigs.put(b.name(), new MetadataTableConfigEntry(rules));
                return ServerResponse.ok().build();
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidRequest",
                "Missing metadata table configuration body")));
    }

    /** DELETE /{bucket}?metadata-table-config — DeleteBucketMetadataTableConfiguration */
    public Mono<ServerResponse> deleteBucketMetadataTableConfiguration(ServerRequest request) {
        return findBucket(request, b -> {
            metadataTableConfigs.remove(b.name());
            return ServerResponse.noContent().build();
        });
    }

    // ─────────────────────────────────────────────────────
    //  Inventory Table Configuration
    // ─────────────────────────────────────────────────────

    private record InventoryTableConfigEntry(String id, String destinationFormat,
                                              String scheduleFrequency, boolean enabled) {}

    /** GET /{bucket}?inventory-table-config — GetBucketInventoryTableConfiguration */
    public Mono<ServerResponse> getBucketInventoryTableConfiguration(ServerRequest request) {
        return findBucket(request, b -> {
            var entry = inventoryTableConfigs.get(b.name());
            if (entry == null) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchInventoryTableConfiguration",
                    "The inventory table configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new InventoryTableConfigurationQuery(
                    entry.id(), entry.destinationFormat(), entry.scheduleFrequency(),
                    entry.enabled() ? "true" : "false"));
        });
    }

    /** PUT /{bucket}?inventory-table-config — PutBucketInventoryTableConfiguration */
    public Mono<ServerResponse> putBucketInventoryTableConfiguration(ServerRequest request) {
        return findBucket(request, b -> request.bodyToMono(InventoryTableConfigurationCommand.class)
            .flatMap(cmd -> {
                inventoryTableConfigs.put(b.name(), new InventoryTableConfigEntry(
                    cmd.id(), cmd.destinationFormat(), cmd.scheduleFrequency(),
                    "true".equals(cmd.enabled())));
                return ServerResponse.ok().build();
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidRequest",
                "Missing inventory table configuration body")));
    }

    // ─────────────────────────────────────────────────────
    //  Journal Table Configuration
    // ─────────────────────────────────────────────────────

    private record JournalTableConfigEntry(String id, String destinationFormat,
                                            String scheduleFrequency, boolean enabled) {}

    /** GET /{bucket}?journal-table-config — GetBucketJournalTableConfiguration */
    public Mono<ServerResponse> getBucketJournalTableConfiguration(ServerRequest request) {
        return findBucket(request, b -> {
            var entry = journalTableConfigs.get(b.name());
            if (entry == null) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchJournalTableConfiguration",
                    "The journal table configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new JournalTableConfigurationQuery(
                    entry.id(), entry.destinationFormat(), entry.scheduleFrequency(),
                    entry.enabled() ? "true" : "false"));
        });
    }

    /** PUT /{bucket}?journal-table-config — PutBucketJournalTableConfiguration */
    public Mono<ServerResponse> putBucketJournalTableConfiguration(ServerRequest request) {
        return findBucket(request, b -> request.bodyToMono(JournalTableConfigurationCommand.class)
            .flatMap(cmd -> {
                journalTableConfigs.put(b.name(), new JournalTableConfigEntry(
                    cmd.id(), cmd.destinationFormat(), cmd.scheduleFrequency(),
                    "true".equals(cmd.enabled())));
                return ServerResponse.ok().build();
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidRequest",
                "Missing journal table configuration body")));
    }
}
