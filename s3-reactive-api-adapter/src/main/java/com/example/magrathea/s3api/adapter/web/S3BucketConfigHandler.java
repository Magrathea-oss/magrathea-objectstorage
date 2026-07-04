package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.valueobject.BucketAccelerateConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketAnalyticsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.AbacConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketConfig;
import com.example.magrathea.objectstore.domain.valueobject.BucketEncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetadataTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketIntelligentTieringConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketInventoryConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketInventoryTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketJournalTableConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketObjectLockConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLifecycleConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketLoggingConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketMetricsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketNotificationConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketOwnershipControls;
import com.example.magrathea.objectstore.domain.valueobject.BucketReplicationConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketRequestPaymentConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.BucketWebsiteConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.CorsConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.PublicAccessBlockConfiguration;
import com.example.magrathea.reactive.application.service.ReactiveBucketService;
import com.example.magrathea.s3api.dto.command.AbacConfigurationCommand;
import com.example.magrathea.s3api.dto.command.AccelerateConfigurationCommand;
import com.example.magrathea.s3api.dto.command.AnalyticsConfigurationCommand;
import com.example.magrathea.s3api.dto.command.CorsConfigurationCommand;
import com.example.magrathea.s3api.dto.command.EncryptionConfigurationCommand;
import com.example.magrathea.s3api.dto.command.IntelligentTieringConfigurationCommand;
import com.example.magrathea.s3api.dto.command.InventoryConfigurationCommand;
import com.example.magrathea.s3api.dto.command.InventoryTableConfigurationCommand;
import com.example.magrathea.s3api.dto.command.JournalTableConfigurationCommand;
import com.example.magrathea.s3api.dto.command.LifecycleConfigurationCommand;
import com.example.magrathea.s3api.dto.command.LoggingConfigurationCommand;
import com.example.magrathea.s3api.dto.command.MetadataConfigurationCommand;
import com.example.magrathea.s3api.dto.command.MetadataTableConfigurationCommand;
import com.example.magrathea.s3api.dto.command.MetricsConfigurationCommand;
import com.example.magrathea.s3api.dto.command.NotificationConfigurationCommand;
import com.example.magrathea.s3api.dto.command.ObjectLockConfigurationCommand;
import com.example.magrathea.s3api.dto.command.OwnershipControlsCommand;
import com.example.magrathea.s3api.dto.command.PublicAccessBlockCommand;
import com.example.magrathea.s3api.dto.command.ReplicationConfigurationCommand;
import com.example.magrathea.s3api.dto.command.RequestPaymentConfigurationCommand;
import com.example.magrathea.s3api.dto.command.WebsiteConfigurationCommand;
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
import com.example.magrathea.s3api.dto.query.BucketOwnershipControlsQuery;
import com.example.magrathea.s3api.dto.query.BucketReplicationQuery;
import com.example.magrathea.s3api.dto.query.BucketRequestPaymentQuery;
import com.example.magrathea.s3api.dto.query.BucketWebsiteQuery;
import com.example.magrathea.s3api.dto.query.InventoryTableConfigurationQuery;
import com.example.magrathea.s3api.dto.query.JournalTableConfigurationQuery;
import com.example.magrathea.s3api.dto.query.MetadataConfigurationQuery;
import com.example.magrathea.s3api.dto.query.MetadataTableConfigurationQuery;
import com.example.magrathea.s3api.dto.query.ObjectLockConfigurationQuery;
import com.example.magrathea.s3api.dto.query.PublicAccessBlockQuery;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Bucket configuration operations.
 *
 * The primary S3 bucket configuration endpoints are driven by a registry of
 * small strategies. Each strategy owns extraction, command decoding, aggregate
 * transition, deletion, and query DTO mapping for one configuration type.
 */
public class S3BucketConfigHandler {

    private enum ConfigType {
        CORS,
        LIFECYCLE,
        POLICY,
        ENCRYPTION,
        LOGGING,
        WEBSITE,
        NOTIFICATION,
        REPLICATION,
        REQUEST_PAYMENT,
        OWNERSHIP_CONTROLS,
        PUBLIC_ACCESS_BLOCK,
        ACCELERATE,
        ANALYTICS,
        INVENTORY,
        METRICS,
        INTELLIGENT_TIERING
    }

    private record ConfigStrategy<V>(
        String errorCode,
        String errorMessage,
        MediaType responseType,
        BiFunction<ServerRequest, Bucket, Optional<V>> extractor,
        BiFunction<ServerRequest, Bucket, Mono<V>> reader,
        BiFunction<Bucket, V, Mono<Bucket>> applier,
        Function<Bucket, Bucket> deleter,
        Function<V, Object> responseMapper,
        Predicate<V> present,
        BiFunction<Bucket, V, Mono<ServerResponse>> rejection
    ) {}

    private final ReactiveBucketService bucketService;
    private final Map<ConfigType, ConfigStrategy<?>> registry;

    // EP-2: every bucket configuration family is now persisted on the Bucket aggregate
    // (durable via BucketStore in storage-engine mode). No handler-local config state
    // remains in the web adapter.

    public S3BucketConfigHandler(ReactiveBucketService bucketService) {
        this.bucketService = bucketService;
        this.registry = buildRegistry();
    }

    /**
     * Retained for test-glue compatibility. All bucket configuration families now live
     * on the durable Bucket aggregate, so there is no handler-local state to reset;
     * repository reset/reload handles configuration state.
     */
    public void resetInMemoryConfigurations() {
        // no-op: configuration state is held on the Bucket aggregate
    }

    // ─────────────────────────────────────────────────────
    //  Registry and generic dispatch
    // ─────────────────────────────────────────────────────

    private Map<ConfigType, ConfigStrategy<?>> buildRegistry() {
        return Map.ofEntries(
            Map.entry(ConfigType.CORS, xmlStrategy(
                "NoSuchCorsConfiguration", "The CORS configuration is not found",
                CorsConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getCorsConfiguration),
                this::toCorsConfiguration,
                Bucket::withCorsConfiguration,
                Bucket::withCorsConfigurationDeleted,
                this::toCorsQuery,
                cors -> cors.corsRules() != null && !cors.corsRules().isEmpty()
            )),
            Map.entry(ConfigType.LIFECYCLE, xmlStrategy(
                "NoSuchLifecycleConfiguration", "The lifecycle configuration does not exist",
                LifecycleConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getLifecycleConfiguration),
                this::toLifecycleConfiguration,
                Bucket::withLifecycleConfiguration,
                Bucket::withLifecycleConfigurationDeleted,
                lc -> BucketLifecycleQuery.from(Optional.of(lc)),
                lifecycle -> lifecycle.rules() != null && !lifecycle.rules().isEmpty()
            )),
            Map.entry(ConfigType.POLICY, policyStrategy()),
            Map.entry(ConfigType.ENCRYPTION, xmlStrategy(
                "NoSuchEncryptionConfiguration", "The encryption configuration does not exist",
                EncryptionConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getEncryptionConfiguration),
                (bucket, cmd) -> new BucketEncryptionConfiguration(bucket.name(), cmd.ruleId(), cmd.algorithm(), cmd.kmsKeyId()),
                Bucket::withEncryptionConfiguration,
                Bucket::withEncryptionConfigurationDeleted,
                ec -> BucketEncryptionQuery.from(Optional.of(ec))
            )),
            Map.entry(ConfigType.LOGGING, xmlStrategy(
                "NoSuchLoggingConfiguration", "The logging configuration does not exist",
                LoggingConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getLoggingConfiguration),
                (bucket, cmd) -> new BucketLoggingConfiguration(bucket.name(), cmd.targetBucket(), cmd.targetPrefix()),
                Bucket::withLoggingConfiguration,
                Bucket::withLoggingConfigurationDeleted,
                lc -> BucketLoggingQuery.from(Optional.of(lc))
            )),
            Map.entry(ConfigType.WEBSITE, xmlStrategy(
                "NoSuchWebsiteConfiguration", "The website configuration does not exist",
                WebsiteConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getWebsiteConfiguration),
                (bucket, cmd) -> new BucketWebsiteConfiguration(bucket.name(), cmd.indexDocument(), cmd.errorDocument(),
                    cmd.redirectAllRequestsTo(), cmd.hostName(), cmd.protocol()),
                Bucket::withWebsiteConfiguration,
                Bucket::withWebsiteConfigurationDeleted,
                wc -> BucketWebsiteQuery.from(Optional.of(wc))
            )),
            Map.entry(ConfigType.NOTIFICATION, xmlStrategy(
                "NoSuchNotificationConfiguration", "The notification configuration does not exist",
                NotificationConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getNotificationConfiguration),
                this::toNotificationConfiguration,
                Bucket::withNotificationConfiguration,
                Bucket::withNotificationConfigurationDeleted,
                nc -> BucketNotificationQuery.from(Optional.of(nc)),
                notification -> notification.events() != null && !notification.events().isEmpty()
            )),
            Map.entry(ConfigType.REPLICATION, xmlStrategy(
                "NoSuchReplicationConfiguration", "The replication configuration does not exist",
                ReplicationConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getReplicationConfiguration),
                this::toReplicationConfiguration,
                Bucket::withReplicationConfiguration,
                Bucket::withReplicationConfigurationDeleted,
                rc -> BucketReplicationQuery.from(Optional.of(rc))
            )),
            Map.entry(ConfigType.REQUEST_PAYMENT, xmlStrategy(
                "NoSuchRequestPaymentConfiguration", "The request payment configuration does not exist",
                RequestPaymentConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getRequestPaymentConfiguration),
                (bucket, cmd) -> new BucketRequestPaymentConfiguration(bucket.name(), cmd.payer()),
                Bucket::withRequestPaymentConfiguration,
                Bucket::withRequestPaymentConfigurationDeleted,
                rpc -> BucketRequestPaymentQuery.from(Optional.of(rpc))
            )),
            Map.entry(ConfigType.OWNERSHIP_CONTROLS, ownershipControlsStrategy()),
            Map.entry(ConfigType.PUBLIC_ACCESS_BLOCK, xmlStrategy(
                "NoSuchPublicAccessBlockConfiguration", "The public access block configuration does not exist",
                PublicAccessBlockCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getPublicAccessBlockConfiguration),
                (bucket, cmd) -> new PublicAccessBlockConfiguration(bucket.name(), bool(cmd.blockPublicAcls()),
                    bool(cmd.ignorePublicAcls()), bool(cmd.blockPublicPolicy()), bool(cmd.restrictPublicBuckets())),
                Bucket::withPublicAccessBlockConfiguration,
                Bucket::withPublicAccessBlockConfigurationDeleted,
                pab -> PublicAccessBlockQuery.from(Optional.of(pab))
            )),
            Map.entry(ConfigType.ACCELERATE, xmlStrategy(
                "NoSuchAccelerateConfiguration", "The accelerate configuration does not exist",
                AccelerateConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getAccelerateConfiguration),
                (bucket, cmd) -> new BucketAccelerateConfiguration(bucket.name(), cmd.status()),
                Bucket::withAccelerateConfiguration,
                Bucket::withAccelerateConfigurationDeleted,
                ac -> BucketAccelerateQuery.from(Optional.of(ac))
            )),
            Map.entry(ConfigType.ANALYTICS, analyticsStrategy()),
            Map.entry(ConfigType.INVENTORY, inventoryStrategy()),
            Map.entry(ConfigType.METRICS, xmlStrategy(
                "NoSuchMetricsConfiguration", "The metrics configuration does not exist",
                MetricsConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getMetricsConfiguration),
                (bucket, cmd) -> new BucketMetricsConfiguration(bucket.name(), cmd.id(),
                    cmd.filter() != null ? cmd.filter().prefix() : null),
                Bucket::withMetricsConfiguration,
                Bucket::withMetricsConfigurationDeleted,
                mc -> BucketMetricsQuery.from(Optional.of(mc))
            )),
            Map.entry(ConfigType.INTELLIGENT_TIERING, xmlStrategy(
                "NoSuchIntelligentTieringConfiguration", "The intelligent-tiering configuration does not exist",
                IntelligentTieringConfigurationCommand.class,
                (request, bucket) -> bucketConfig(bucket, BucketConfig::getIntelligentTieringConfiguration),
                (bucket, cmd) -> new BucketIntelligentTieringConfiguration(bucket.name(), cmd.id(),
                    cmd.tieringPolicy() != null ? cmd.tieringPolicy().tieringRule() : null,
                    cmd.autoTieringStatus()),
                Bucket::withIntelligentTieringConfiguration,
                Bucket::withIntelligentTieringConfigurationDeleted,
                itc -> BucketIntelligentTieringQuery.from(Optional.of(itc))
            ))
        );
    }

    private <C, V> ConfigStrategy<V> xmlStrategy(String errorCode,
                                                  String errorMessage,
                                                  Class<C> commandClass,
                                                  BiFunction<ServerRequest, Bucket, Optional<V>> extractor,
                                                  BiFunction<Bucket, C, V> mapper,
                                                  BiFunction<Bucket, V, Bucket> applier,
                                                  Function<Bucket, Bucket> deleter,
                                                  Function<V, Object> responseMapper) {
        return xmlStrategy(errorCode, errorMessage, commandClass, extractor, mapper, applier, deleter,
            responseMapper, value -> true);
    }

    private <C, V> ConfigStrategy<V> xmlStrategy(String errorCode,
                                                  String errorMessage,
                                                  Class<C> commandClass,
                                                  BiFunction<ServerRequest, Bucket, Optional<V>> extractor,
                                                  BiFunction<Bucket, C, V> mapper,
                                                  BiFunction<Bucket, V, Bucket> applier,
                                                  Function<Bucket, Bucket> deleter,
                                                  Function<V, Object> responseMapper,
                                                  Predicate<V> present) {
        return new ConfigStrategy<>(
            errorCode,
            errorMessage,
            MediaType.APPLICATION_XML,
            extractor,
            (request, bucket) -> request.bodyToMono(commandClass).map(cmd -> mapper.apply(bucket, cmd)),
            (bucket, value) -> Mono.just(applier.apply(bucket, value)),
            deleter,
            responseMapper,
            present,
            this::noConfigRejection
        );
    }

    private ConfigStrategy<String> policyStrategy() {
        return new ConfigStrategy<>(
            "NoSuchBucketPolicy",
            "The bucket policy does not exist",
            MediaType.APPLICATION_JSON,
            (request, bucket) -> bucketConfig(bucket, BucketConfig::getBucketPolicy),
            this::readPolicyBody,
            (bucket, policy) -> Mono.just(bucket.withBucketPolicy(policy)),
            Bucket::withBucketPolicyDeleted,
            policy -> policy,
            policy -> policy != null && !policy.isBlank(),
            this::rejectBlockedPublicPolicy
        );
    }

    private ConfigStrategy<BucketOwnershipControls> ownershipControlsStrategy() {
        return new ConfigStrategy<>(
            "NoSuchOwnershipControls",
            "The ownership controls do not exist",
            MediaType.APPLICATION_XML,
            (request, bucket) -> bucketConfig(bucket, BucketConfig::getOwnershipControls),
            (request, bucket) -> request.bodyToMono(OwnershipControlsCommand.class)
                .map(cmd -> new BucketOwnershipControls(bucket.name(), cmd.id(), cmd.ownership())),
            (bucket, value) -> Mono.just(bucket.withOwnershipControls(value)),
            Bucket::withOwnershipControlsDeleted,
            oc -> BucketOwnershipControlsQuery.from(Optional.of(oc)),
            value -> true,
            this::rejectOwnershipReversion
        );
    }

    private ConfigStrategy<BucketAnalyticsConfiguration> analyticsStrategy() {
        return new ConfigStrategy<>(
            "NoSuchAnalyticsConfiguration",
            "The analytics configuration does not exist",
            MediaType.APPLICATION_XML,
            this::extractAnalytics,
            (request, bucket) -> request.bodyToMono(AnalyticsConfigurationCommand.class)
                .map(cmd -> new BucketAnalyticsConfiguration(bucket.name(),
                    firstNonBlank(cmd.id(), request.queryParam("analyticsId").orElse(null)),
                    cmd.filter() != null ? cmd.filter().prefix() : null)),
            (bucket, value) -> Mono.just(bucket.withAnalyticsConfiguration(value)),
            Bucket::withAnalyticsConfigurationDeleted,
            ac -> BucketAnalyticsQuery.from(Optional.of(ac)),
            value -> value.analyticsId() != null && !value.analyticsId().isBlank(),
            this::noConfigRejection
        );
    }

    private ConfigStrategy<BucketInventoryConfiguration> inventoryStrategy() {
        return new ConfigStrategy<>(
            "NoSuchInventoryConfiguration",
            "The inventory configuration does not exist",
            MediaType.APPLICATION_XML,
            this::extractInventory,
            (request, bucket) -> request.bodyToMono(InventoryConfigurationCommand.class)
                .map(cmd -> new BucketInventoryConfiguration(bucket.name(),
                    firstNonBlank(cmd.id(), request.queryParam("inventoryId").orElse(null)),
                    cmd.destination() != null ? cmd.destination().format() : null,
                    cmd.schedule() != null ? cmd.schedule().frequency() : null,
                    "Enabled".equals(cmd.enabled()) || "true".equalsIgnoreCase(cmd.enabled()))),
            (bucket, value) -> Mono.just(bucket.withInventoryConfiguration(value)),
            Bucket::withInventoryConfigurationDeleted,
            ic -> BucketInventoryQuery.from(Optional.of(ic)),
            value -> value.inventoryId() != null && !value.inventoryId().isBlank(),
            this::noConfigRejection
        );
    }

    private Mono<ServerResponse> findBucket(ServerRequest request,
                                            Function<Bucket, Mono<ServerResponse>> handler) {
        var bucketName = request.pathVariable("bucket");
        return bucketService.findByName(bucketName)
            .flatMap(handler)
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    @SuppressWarnings("unchecked")
    private <V> ConfigStrategy<V> strategy(ConfigType type) {
        return (ConfigStrategy<V>) registry.get(type);
    }

    private <V> Mono<ServerResponse> getConfig(ServerRequest request, ConfigType type) {
        var selected = this.<V>strategy(type);
        return findBucket(request, bucket -> getConfig(request, bucket, selected));
    }

    private <V> Mono<ServerResponse> getConfig(ServerRequest request, Bucket bucket, ConfigStrategy<V> selected) {
        return selected.extractor().apply(request, bucket)
            .filter(selected.present())
            .map(value -> ServerResponse.ok()
                .contentType(selected.responseType())
                .bodyValue(selected.responseMapper().apply(value)))
            .orElseGet(() -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND,
                selected.errorCode(), selected.errorMessage()));
    }

    private <V> Mono<ServerResponse> putConfig(ServerRequest request, ConfigType type) {
        var selected = this.<V>strategy(type);
        return findBucket(request, bucket -> selected.reader().apply(request, bucket)
            .flatMap(value -> selected.rejection().apply(bucket, value)
                .switchIfEmpty(Mono.defer(() -> selected.applier().apply(bucket, value)
                    .flatMap(bucketService::updateBucket)
                    .then(ServerResponse.ok().build()))))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST,
                "InvalidRequest", "Missing configuration body")));
    }

    private <V> Mono<ServerResponse> deleteConfig(ServerRequest request, ConfigType type) {
        var selected = this.<V>strategy(type);
        return findBucket(request, bucket -> bucketService.updateBucket(selected.deleter().apply(bucket))
            .then(ServerResponse.noContent().build()));
    }

    private <V> Optional<V> bucketConfig(Bucket bucket, Function<BucketConfig, Optional<V>> extractor) {
        return bucket.bucketConfig() != null ? extractor.apply(bucket.bucketConfig()) : Optional.empty();
    }

    private <V> Mono<ServerResponse> noConfigRejection(Bucket bucket, V value) {
        return Mono.empty();
    }

    // ─────────────────────────────────────────────────────
    //  Command mapping
    // ─────────────────────────────────────────────────────

    private CorsConfiguration toCorsConfiguration(Bucket bucket, CorsConfigurationCommand command) {
        var corsRules = list(command.corsRules()).stream()
            .map(rule -> new CorsConfiguration.CorsRule(
                list(rule.allowedOrigins()),
                list(rule.allowedMethods()),
                list(rule.allowedHeaders()),
                rule.maxAgeSeconds() != null ? rule.maxAgeSeconds() : 0,
                list(rule.exposeHeaders()),
                rule.id()))
            .toList();
        return new CorsConfiguration(corsRules);
    }

    private BucketCorsQuery toCorsQuery(CorsConfiguration config) {
        var rules = config.corsRules().stream()
            .map(rule -> new BucketCorsQuery.CorsRuleEntry(
                rule.allowedOrigins(), rule.allowedMethods(), rule.allowedHeaders(),
                rule.maxAgeSeconds(), rule.exposeHeaders(), rule.id()))
            .toList();
        return new BucketCorsQuery(rules);
    }

    private BucketLifecycleConfiguration toLifecycleConfiguration(Bucket bucket, LifecycleConfigurationCommand command) {
        var rules = list(command.rules()).stream()
            .map(rule -> new BucketLifecycleConfiguration.LifecycleRule(
                rule.id(), rule.status(), rule.prefix(),
                rule.expiration() != null
                    ? new BucketLifecycleConfiguration.Expiration(rule.expiration().days(), rule.expiration().date())
                    : null,
                rule.noncurrentVersionExpiration() != null
                    ? new BucketLifecycleConfiguration.NoncurrentVersionExpiration(
                        rule.noncurrentVersionExpiration().noncurrentDays())
                    : null,
                rule.abortIncompleteMultipartUpload() != null
                    ? new BucketLifecycleConfiguration.AbortIncompleteMultipartUpload(
                        rule.abortIncompleteMultipartUpload().daysAfterInitiation())
                    : null))
            .toList();
        return new BucketLifecycleConfiguration(bucket.name(), rules);
    }

    private BucketNotificationConfiguration toNotificationConfiguration(Bucket bucket,
                                                                          NotificationConfigurationCommand command) {
        var events = list(command.eventConfigurations()).stream()
            .map(eventConfig -> new BucketNotificationConfiguration.NotificationEvent(
                eventConfig.event(), eventConfig.topicArn(), eventConfig.queueArn(), eventConfig.lambdaArn(),
                list(eventConfig.filterRules())))
            .toList();
        return new BucketNotificationConfiguration(bucket.name(), events);
    }

    private BucketReplicationConfiguration toReplicationConfiguration(Bucket bucket,
                                                                       ReplicationConfigurationCommand command) {
        var replicationRules = list(command.rules()).stream()
            .map(rule -> new BucketReplicationConfiguration.ReplicationRule(
                rule.id(), rule.status(), rule.prefix(), rule.destinationBucket(),
                rule.destinationStorageClass(), false, false))
            .toList();
        return new BucketReplicationConfiguration(bucket.name(), command.role(), replicationRules);
    }

    private Optional<BucketAnalyticsConfiguration> extractAnalytics(ServerRequest request, Bucket bucket) {
        var configured = bucketConfig(bucket, BucketConfig::getAnalyticsConfiguration);
        var requestedId = request.queryParam("analyticsId").orElse(null);
        if (requestedId == null || requestedId.isBlank()) {
            return configured;
        }
        return configured.filter(value -> requestedId.equals(value.analyticsId()));
    }

    private Optional<BucketInventoryConfiguration> extractInventory(ServerRequest request, Bucket bucket) {
        var configured = bucketConfig(bucket, BucketConfig::getInventoryConfiguration);
        var requestedId = request.queryParam("inventoryId").orElse(null);
        if (requestedId == null || requestedId.isBlank()) {
            return configured;
        }
        return configured.filter(value -> requestedId.equals(value.inventoryId()));
    }

    private Mono<String> readPolicyBody(ServerRequest request, Bucket bucket) {
        return request.bodyToFlux(DataBuffer.class)
            .map(buffer -> {
                try {
                    return buffer.toString(StandardCharsets.UTF_8);
                } finally {
                    DataBufferUtils.release(buffer);
                }
            })
            .reduce(new StringBuilder(), StringBuilder::append)
            .map(StringBuilder::toString)
            .defaultIfEmpty("");
    }

    private Mono<ServerResponse> rejectBlockedPublicPolicy(Bucket bucket, String policy) {
        var publicAccessBlock = bucketConfig(bucket, BucketConfig::getPublicAccessBlockConfiguration).orElse(null);
        if (publicAccessBlock != null && publicAccessBlock.blockPublicPolicy() && isPublicPolicy(policy)) {
            return S3WebSupport.xmlError(HttpStatus.FORBIDDEN, "AccessDenied",
                "Public bucket policies are blocked by PublicAccessBlock");
        }
        return Mono.empty();
    }

    private Mono<ServerResponse> rejectOwnershipReversion(Bucket bucket, BucketOwnershipControls next) {
        var current = bucketConfig(bucket, BucketConfig::getOwnershipControls).orElse(null);
        if (current != null
            && "BucketOwnerEnforced".equals(current.ownership())
            && !"BucketOwnerEnforced".equals(next.ownership())) {
            return S3WebSupport.xmlError(HttpStatus.CONFLICT, "OwnershipControlsNotReversible",
                "BucketOwnerEnforced ownership controls cannot be reverted");
        }
        return Mono.empty();
    }

    private boolean isPublicPolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            return false;
        }
        var compact = policy.replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "");
        return compact.contains("\"Effect\":\"Allow\"")
            && (compact.contains("\"Principal\":\"*\"")
                || compact.contains("\"Principal\":{\"AWS\":\"*\"}"));
    }

    private static boolean bool(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static <T> List<T> list(List<T> values) {
        return values != null ? values : List.of();
    }

    // ─────────────────────────────────────────────────────
    //  S3 singleton and multi-instance bucket configurations
    // ─────────────────────────────────────────────────────

    public Mono<ServerResponse> getBucketCors(ServerRequest request) {
        return getConfig(request, ConfigType.CORS);
    }

    public Mono<ServerResponse> putBucketCors(ServerRequest request) {
        return putConfig(request, ConfigType.CORS);
    }

    public Mono<ServerResponse> deleteBucketCors(ServerRequest request) {
        return deleteConfig(request, ConfigType.CORS);
    }

    public Mono<ServerResponse> getBucketLifecycle(ServerRequest request) {
        return getConfig(request, ConfigType.LIFECYCLE);
    }

    public Mono<ServerResponse> putBucketLifecycle(ServerRequest request) {
        return putConfig(request, ConfigType.LIFECYCLE);
    }

    public Mono<ServerResponse> deleteBucketLifecycle(ServerRequest request) {
        return deleteConfig(request, ConfigType.LIFECYCLE);
    }

    public Mono<ServerResponse> getBucketPolicy(ServerRequest request) {
        return getConfig(request, ConfigType.POLICY);
    }

    public Mono<ServerResponse> putBucketPolicy(ServerRequest request) {
        return putConfig(request, ConfigType.POLICY);
    }

    public Mono<ServerResponse> deleteBucketPolicy(ServerRequest request) {
        return deleteConfig(request, ConfigType.POLICY);
    }

    public Mono<ServerResponse> getBucketEncryption(ServerRequest request) {
        return getConfig(request, ConfigType.ENCRYPTION);
    }

    public Mono<ServerResponse> putBucketEncryption(ServerRequest request) {
        return putConfig(request, ConfigType.ENCRYPTION);
    }

    public Mono<ServerResponse> deleteBucketEncryption(ServerRequest request) {
        return deleteConfig(request, ConfigType.ENCRYPTION);
    }

    public Mono<ServerResponse> getBucketLogging(ServerRequest request) {
        return getConfig(request, ConfigType.LOGGING);
    }

    public Mono<ServerResponse> putBucketLogging(ServerRequest request) {
        return putConfig(request, ConfigType.LOGGING);
    }

    public Mono<ServerResponse> deleteBucketLogging(ServerRequest request) {
        return deleteConfig(request, ConfigType.LOGGING);
    }

    public Mono<ServerResponse> getBucketWebsite(ServerRequest request) {
        return getConfig(request, ConfigType.WEBSITE);
    }

    public Mono<ServerResponse> putBucketWebsite(ServerRequest request) {
        return putConfig(request, ConfigType.WEBSITE);
    }

    public Mono<ServerResponse> deleteBucketWebsite(ServerRequest request) {
        return deleteConfig(request, ConfigType.WEBSITE);
    }

    public Mono<ServerResponse> getBucketNotification(ServerRequest request) {
        return getConfig(request, ConfigType.NOTIFICATION);
    }

    public Mono<ServerResponse> putBucketNotification(ServerRequest request) {
        return putConfig(request, ConfigType.NOTIFICATION);
    }

    public Mono<ServerResponse> deleteBucketNotification(ServerRequest request) {
        return deleteConfig(request, ConfigType.NOTIFICATION);
    }

    public Mono<ServerResponse> getBucketReplication(ServerRequest request) {
        return getConfig(request, ConfigType.REPLICATION);
    }

    public Mono<ServerResponse> putBucketReplication(ServerRequest request) {
        return putConfig(request, ConfigType.REPLICATION);
    }

    public Mono<ServerResponse> deleteBucketReplication(ServerRequest request) {
        return deleteConfig(request, ConfigType.REPLICATION);
    }

    public Mono<ServerResponse> getBucketRequestPayment(ServerRequest request) {
        return getConfig(request, ConfigType.REQUEST_PAYMENT);
    }

    public Mono<ServerResponse> putBucketRequestPayment(ServerRequest request) {
        return putConfig(request, ConfigType.REQUEST_PAYMENT);
    }

    public Mono<ServerResponse> deleteBucketRequestPayment(ServerRequest request) {
        return deleteConfig(request, ConfigType.REQUEST_PAYMENT);
    }

    public Mono<ServerResponse> getBucketOwnershipControls(ServerRequest request) {
        return getConfig(request, ConfigType.OWNERSHIP_CONTROLS);
    }

    public Mono<ServerResponse> putBucketOwnershipControls(ServerRequest request) {
        return putConfig(request, ConfigType.OWNERSHIP_CONTROLS);
    }

    public Mono<ServerResponse> deleteBucketOwnershipControls(ServerRequest request) {
        return deleteConfig(request, ConfigType.OWNERSHIP_CONTROLS);
    }

    public Mono<ServerResponse> getPublicAccessBlock(ServerRequest request) {
        return getConfig(request, ConfigType.PUBLIC_ACCESS_BLOCK);
    }

    public Mono<ServerResponse> putPublicAccessBlock(ServerRequest request) {
        return putConfig(request, ConfigType.PUBLIC_ACCESS_BLOCK);
    }

    public Mono<ServerResponse> deletePublicAccessBlock(ServerRequest request) {
        return deleteConfig(request, ConfigType.PUBLIC_ACCESS_BLOCK);
    }

    public Mono<ServerResponse> getBucketAccelerate(ServerRequest request) {
        return getConfig(request, ConfigType.ACCELERATE);
    }

    public Mono<ServerResponse> putBucketAccelerate(ServerRequest request) {
        return putConfig(request, ConfigType.ACCELERATE);
    }

    public Mono<ServerResponse> deleteBucketAccelerate(ServerRequest request) {
        return deleteConfig(request, ConfigType.ACCELERATE);
    }

    public Mono<ServerResponse> getBucketAnalytics(ServerRequest request) {
        return getConfig(request, ConfigType.ANALYTICS);
    }

    public Mono<ServerResponse> putBucketAnalytics(ServerRequest request) {
        var analyticsId = request.queryParam("analyticsId").orElse(null);
        if (analyticsId == null || analyticsId.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingAnalyticsId",
                "The analyticsId query parameter is required");
        }
        return putConfig(request, ConfigType.ANALYTICS);
    }

    public Mono<ServerResponse> deleteBucketAnalytics(ServerRequest request) {
        var analyticsId = request.queryParam("analyticsId").orElse(null);
        if (analyticsId == null || analyticsId.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingAnalyticsId",
                "The analyticsId query parameter is required");
        }
        return deleteConfig(request, ConfigType.ANALYTICS);
    }

    public Mono<ServerResponse> listBucketAnalyticsConfigurations(ServerRequest request) {
        return findBucket(request, bucket -> bucketConfig(bucket, BucketConfig::getAnalyticsConfiguration)
            .filter(ac -> ac.analyticsId() != null && !ac.analyticsId().isBlank())
            .map(ac -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new BucketAnalyticsListQuery(
                    List.of(new BucketAnalyticsListQuery.AnalyticsConfigurationEntry(ac.analyticsId())))))
            .orElseGet(() -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND,
                "NoSuchAnalyticsConfiguration", "No analytics configurations exist")));
    }

    public Mono<ServerResponse> getBucketInventory(ServerRequest request) {
        return getConfig(request, ConfigType.INVENTORY);
    }

    public Mono<ServerResponse> putBucketInventory(ServerRequest request) {
        var inventoryId = request.queryParam("inventoryId").orElse(null);
        if (inventoryId == null || inventoryId.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingInventoryId",
                "The inventoryId query parameter is required");
        }
        return putConfig(request, ConfigType.INVENTORY);
    }

    public Mono<ServerResponse> deleteBucketInventory(ServerRequest request) {
        var inventoryId = request.queryParam("inventoryId").orElse(null);
        if (inventoryId == null || inventoryId.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingInventoryId",
                "The inventoryId query parameter is required");
        }
        return deleteConfig(request, ConfigType.INVENTORY);
    }

    public Mono<ServerResponse> listBucketInventoryConfigurations(ServerRequest request) {
        return findBucket(request, bucket -> bucketConfig(bucket, BucketConfig::getInventoryConfiguration)
            .filter(ic -> ic.inventoryId() != null && !ic.inventoryId().isBlank())
            .map(ic -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new BucketInventoryListQuery(
                    List.of(new BucketInventoryListQuery.InventoryConfigurationEntry(ic.inventoryId())))))
            .orElseGet(() -> S3WebSupport.xmlError(HttpStatus.NOT_FOUND,
                "NoSuchInventoryConfiguration", "No inventory configurations exist")));
    }

    public Mono<ServerResponse> getBucketMetrics(ServerRequest request) {
        return getConfig(request, ConfigType.METRICS);
    }

    public Mono<ServerResponse> putBucketMetrics(ServerRequest request) {
        return putConfig(request, ConfigType.METRICS);
    }

    public Mono<ServerResponse> deleteBucketMetrics(ServerRequest request) {
        return deleteConfig(request, ConfigType.METRICS);
    }

    public Mono<ServerResponse> getBucketIntelligentTiering(ServerRequest request) {
        return getConfig(request, ConfigType.INTELLIGENT_TIERING);
    }

    public Mono<ServerResponse> putBucketIntelligentTiering(ServerRequest request) {
        return putConfig(request, ConfigType.INTELLIGENT_TIERING);
    }

    public Mono<ServerResponse> deleteBucketIntelligentTiering(ServerRequest request) {
        return deleteConfig(request, ConfigType.INTELLIGENT_TIERING);
    }

    // ─────────────────────────────────────────────────────
    //  ABAC Configuration
    // ─────────────────────────────────────────────────────

    public Mono<ServerResponse> getBucketAbac(ServerRequest request) {
        return findBucket(request, bucket -> {
            var config = bucketConfig(bucket, BucketConfig::getAbacConfiguration).orElse(null);
            if (config == null || config.rules().isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchAbacConfiguration",
                    "The ABAC configuration does not exist");
            }
            var rules = config.rules().stream()
                .map(rule -> new AbacConfigurationQuery.AbacRuleEntry(
                    rule.id(), rule.principal(), rule.resource(), rule.action(),
                    rule.conditions().stream()
                        .map(condition -> new AbacConfigurationQuery.ConditionEntry(condition.tag(), condition.value()))
                        .toList()))
                .toList();
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new AbacConfigurationQuery(rules));
        });
    }

    public Mono<ServerResponse> putBucketAbac(ServerRequest request) {
        return findBucket(request, bucket -> request.bodyToMono(AbacConfigurationCommand.class)
            .flatMap(command -> {
                var rules = command.rules().stream()
                    .map(rule -> AbacConfiguration.AbacRule.of(
                        rule.id(), rule.principal(), rule.resource(), rule.action(),
                        rule.conditions() != null
                            ? rule.conditions().stream()
                                .map(condition -> AbacConfiguration.Condition.of(condition.tag(), condition.value()))
                                .toList()
                            : List.of()))
                    .toList();
                return bucketService.updateBucket(bucket.withAbacConfiguration(AbacConfiguration.of(rules)))
                    .then(ServerResponse.ok().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidRequest",
                "Missing ABAC configuration body")));
    }

    // ─────────────────────────────────────────────────────
    //  Object Lock Configuration
    // ─────────────────────────────────────────────────────

    public Mono<ServerResponse> getObjectLockConfiguration(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return bucketService.findByName(bucketName)
            .flatMap(bucket -> {
                var config = bucketConfig(bucket, BucketConfig::getObjectLockConfiguration).orElse(null);
                if (config == null || !config.enabled()) {
                    return ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_XML)
                        .bodyValue(ObjectLockConfigurationQuery.fromEnabled(false));
                }
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .bodyValue(ObjectLockConfigurationQuery.from(config.mode(), config.days()));
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    public Mono<ServerResponse> putObjectLockConfiguration(ServerRequest request) {
        var bucketName = request.pathVariable("bucket");
        return bucketService.findByName(bucketName)
            .flatMap(bucket -> request.bodyToMono(ObjectLockConfigurationCommand.class)
                .flatMap(command -> {
                    BucketObjectLockConfiguration lock;
                    if (!command.isEnabled()) {
                        lock = BucketObjectLockConfiguration.disabled();
                    } else {
                        var mode = command.rule() != null && command.rule().defaultRetention() != null
                            ? command.rule().defaultRetention().mode() : "GOVERNANCE";
                        var days = command.rule() != null && command.rule().defaultRetention() != null
                            ? command.rule().defaultRetention().days() != null ? command.rule().defaultRetention().days() : 5
                            : 5;
                        lock = BucketObjectLockConfiguration.of(mode, days);
                    }
                    return bucketService.updateBucket(
                            bucket.withBucketConfig(bucket.bucketConfig().withObjectLockConfiguration(lock)))
                        .then(ServerResponse.ok().build());
                }))
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found"));
    }

    // ─────────────────────────────────────────────────────
    //  Metadata Configuration
    // ─────────────────────────────────────────────────────

    public Mono<ServerResponse> getBucketMetadataConfiguration(ServerRequest request) {
        return findBucket(request, bucket -> {
            var config = bucketConfig(bucket, BucketConfig::getMetadataConfiguration).orElse(null);
            if (config == null || config.rules().isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchMetadataConfiguration",
                    "The metadata configuration does not exist");
            }
            var rules = config.rules().stream()
                .map(rule -> new MetadataConfigurationQuery.MetadataRuleEntry(
                    rule.id(), rule.status(), rule.metadataResourceType(), rule.metadataResourceSubtype()))
                .toList();
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new MetadataConfigurationQuery(rules));
        });
    }

    public Mono<ServerResponse> putBucketMetadataConfiguration(ServerRequest request) {
        return findBucket(request, bucket -> request.bodyToMono(MetadataConfigurationCommand.class)
            .flatMap(command -> {
                var rules = command.rules().stream()
                    .map(rule -> BucketMetadataConfiguration.MetadataRule.of(
                        rule.id(), rule.status(), rule.metadataResourceType(), rule.metadataResourceSubtype()))
                    .toList();
                return bucketService.updateBucket(
                        bucket.withMetadataConfiguration(BucketMetadataConfiguration.of(rules)))
                    .then(ServerResponse.ok().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidRequest",
                "Missing metadata configuration body")));
    }

    public Mono<ServerResponse> deleteBucketMetadataConfiguration(ServerRequest request) {
        return findBucket(request, bucket ->
            bucketService.updateBucket(bucket.withMetadataConfiguration(BucketMetadataConfiguration.empty()))
                .then(ServerResponse.noContent().build()));
    }

    // ─────────────────────────────────────────────────────
    //  Metadata Table Configuration
    // ─────────────────────────────────────────────────────

    public Mono<ServerResponse> getBucketMetadataTableConfiguration(ServerRequest request) {
        return findBucket(request, bucket -> {
            var config = bucketConfig(bucket, BucketConfig::getMetadataTableConfiguration).orElse(null);
            if (config == null || config.rules().isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchMetadataTableConfiguration",
                    "The metadata table configuration does not exist");
            }
            var rules = config.rules().stream()
                .map(rule -> new MetadataTableConfigurationQuery.MetadataTableRuleEntry(
                    rule.id(), rule.status(), rule.metadataTableName(), rule.metadataTableDatabase()))
                .toList();
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(new MetadataTableConfigurationQuery(rules));
        });
    }

    public Mono<ServerResponse> putBucketMetadataTableConfiguration(ServerRequest request) {
        return findBucket(request, bucket -> request.bodyToMono(MetadataTableConfigurationCommand.class)
            .flatMap(command -> {
                var rules = command.rules().stream()
                    .map(rule -> BucketMetadataTableConfiguration.MetadataTableRule.of(
                        rule.id(), rule.status(), rule.metadataTableName(), rule.metadataTableDatabase()))
                    .toList();
                return bucketService.updateBucket(
                        bucket.withMetadataTableConfiguration(BucketMetadataTableConfiguration.of(rules)))
                    .then(ServerResponse.ok().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidRequest",
                "Missing metadata table configuration body")));
    }

    public Mono<ServerResponse> deleteBucketMetadataTableConfiguration(ServerRequest request) {
        return findBucket(request, bucket ->
            bucketService.updateBucket(bucket.withMetadataTableConfiguration(BucketMetadataTableConfiguration.empty()))
                .then(ServerResponse.noContent().build()));
    }

    // ─────────────────────────────────────────────────────
    //  Inventory Table Configuration
    // ─────────────────────────────────────────────────────

    public Mono<ServerResponse> getBucketInventoryTableConfiguration(ServerRequest request) {
        return findBucket(request, bucket -> {
            var entry = bucketConfig(bucket, BucketConfig::getInventoryTableConfiguration).orElse(null);
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

    public Mono<ServerResponse> putBucketInventoryTableConfiguration(ServerRequest request) {
        return findBucket(request, bucket -> request.bodyToMono(InventoryTableConfigurationCommand.class)
            .flatMap(command -> {
                var config = BucketInventoryTableConfiguration.of(
                    command.id(), command.destinationFormat(), command.scheduleFrequency(),
                    "true".equals(command.enabled()));
                return bucketService.updateBucket(
                        bucket.withBucketConfig(bucket.bucketConfig().withInventoryTableConfiguration(config)))
                    .then(ServerResponse.ok().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidRequest",
                "Missing inventory table configuration body")));
    }

    // ─────────────────────────────────────────────────────
    //  Journal Table Configuration
    // ─────────────────────────────────────────────────────

    public Mono<ServerResponse> getBucketJournalTableConfiguration(ServerRequest request) {
        return findBucket(request, bucket -> {
            var entry = bucketConfig(bucket, BucketConfig::getJournalTableConfiguration).orElse(null);
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

    public Mono<ServerResponse> putBucketJournalTableConfiguration(ServerRequest request) {
        return findBucket(request, bucket -> request.bodyToMono(JournalTableConfigurationCommand.class)
            .flatMap(command -> {
                var config = BucketJournalTableConfiguration.of(
                    command.id(), command.destinationFormat(), command.scheduleFrequency(),
                    "true".equals(command.enabled()));
                return bucketService.updateBucket(
                        bucket.withBucketConfig(bucket.bucketConfig().withJournalTableConfiguration(config)))
                    .then(ServerResponse.ok().build());
            })
            .switchIfEmpty(S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "InvalidRequest",
                "Missing journal table configuration body")));
    }
}
