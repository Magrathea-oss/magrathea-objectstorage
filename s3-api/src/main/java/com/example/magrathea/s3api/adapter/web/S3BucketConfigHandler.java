package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.service.BucketService;
import com.example.magrathea.objectstorage.domain.valueobject.BucketLifecycleConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketPolicy;
import com.example.magrathea.objectstorage.domain.valueobject.BucketEncryptionConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketLoggingConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketWebsiteConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketNotificationConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketReplicationConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketRequestPaymentConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketOwnershipControls;
import com.example.magrathea.objectstorage.domain.valueobject.PublicAccessBlockConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketAccelerateConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketAnalyticsConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketIntelligentTieringConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketInventoryConfiguration;
import com.example.magrathea.objectstorage.domain.valueobject.BucketMetricsConfiguration;
import com.example.magrathea.s3api.dto.command.CorsConfigurationCommand;
import com.example.magrathea.s3api.dto.command.LifecycleConfigurationCommand;
import com.example.magrathea.s3api.dto.command.EncryptionConfigurationCommand;
import com.example.magrathea.s3api.dto.command.LoggingConfigurationCommand;
import com.example.magrathea.s3api.dto.command.WebsiteConfigurationCommand;
import com.example.magrathea.s3api.dto.command.NotificationConfigurationCommand;
import com.example.magrathea.s3api.dto.command.ReplicationConfigurationCommand;
import com.example.magrathea.s3api.dto.command.RequestPaymentConfigurationCommand;
import com.example.magrathea.s3api.dto.command.OwnershipControlsCommand;
import com.example.magrathea.s3api.dto.command.PublicAccessBlockCommand;
import com.example.magrathea.s3api.dto.command.AccelerateConfigurationCommand;
import com.example.magrathea.s3api.dto.command.AnalyticsConfigurationCommand;
import com.example.magrathea.s3api.dto.command.InventoryConfigurationCommand;
import com.example.magrathea.s3api.dto.command.MetricsConfigurationCommand;
import com.example.magrathea.s3api.dto.command.IntelligentTieringConfigurationCommand;
import com.example.magrathea.s3api.dto.query.ErrorQuery;
import com.example.magrathea.s3api.dto.query.BucketCorsQuery;
import com.example.magrathea.s3api.dto.query.BucketLifecycleQuery;
import com.example.magrathea.s3api.dto.query.BucketEncryptionQuery;
import com.example.magrathea.s3api.dto.query.BucketLoggingQuery;
import com.example.magrathea.s3api.dto.query.BucketWebsiteQuery;
import com.example.magrathea.s3api.dto.query.BucketNotificationQuery;
import com.example.magrathea.s3api.dto.query.BucketReplicationQuery;
import com.example.magrathea.s3api.dto.query.BucketRequestPaymentQuery;
import com.example.magrathea.s3api.dto.query.BucketOwnershipControlsQuery;
import com.example.magrathea.s3api.dto.query.PublicAccessBlockQuery;
import com.example.magrathea.s3api.dto.query.BucketAccelerateQuery;
import com.example.magrathea.s3api.dto.query.BucketAnalyticsQuery;
import com.example.magrathea.s3api.dto.query.BucketInventoryQuery;
import com.example.magrathea.s3api.dto.query.BucketMetricsQuery;
import com.example.magrathea.s3api.dto.query.BucketIntelligentTieringQuery;
import com.example.magrathea.s3api.dto.query.BucketAnalyticsListQuery;
import com.example.magrathea.s3api.dto.query.BucketInventoryListQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.ArrayList;

/**
 * Bucket configuration operations: CORS, policy, encryption, logging, website, notification.
 * Uses Jackson XML codec for request body deserialization and response serialization.
 */
public class S3BucketConfigHandler {

    private final BucketService bucketService;

    public S3BucketConfigHandler(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    /** GET /{bucket}?cors — GetBucketCors */
    public Mono<ServerResponse> getBucketCors(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getCorsConfiguration(bucket);
            if (config.isEmpty() || !config.get().hasCors()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchCorsConfiguration",
                    "The CORS configuration is not found");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketCorsQuery.from(bucket, config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?cors — PutBucketCors */
    public Mono<ServerResponse> putBucketCors(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(CorsConfigurationCommand.class)
            .flatMap(xmlCmd -> {
                var rules = xmlCmd.corsRules().stream()
                    .map(r -> new com.example.magrathea.objectstorage.application.dto.CorsConfigurationCommand.CorsRuleDto(
                        r.allowedOrigins(), r.allowedMethods(), r.allowedHeaders(),
                        r.maxAgeSeconds(), r.exposeHeaders(), r.id()))
                    .toList();
                var appCmd = new com.example.magrathea.objectstorage.application.dto.CorsConfigurationCommand(bucket, rules);
                bucketService.putCorsConfiguration(appCmd);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?cors — DeleteBucketCors */
    public Mono<ServerResponse> deleteBucketCors(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteCorsConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Lifecycle Configuration ──

    /** GET /{bucket}?lifecycle — GetBucketLifecycleConfiguration */
    public Mono<ServerResponse> getBucketLifecycle(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getLifecycleConfiguration(bucket);
            if (config.isEmpty() || !config.get().hasRules()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchLifecycleConfiguration",
                    "The lifecycle configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketLifecycleQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?lifecycle — PutBucketLifecycleConfiguration */
    public Mono<ServerResponse> putBucketLifecycle(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(LifecycleConfigurationCommand.class)
            .flatMap(cmd -> {
                var rules = new ArrayList<BucketLifecycleConfiguration.LifecycleRule>();
                for (var rule : cmd.rules()) {
                    var expiration = rule.expiration() != null
                        ? new BucketLifecycleConfiguration.Expiration(rule.expiration().days(), rule.expiration().date())
                        : null;
                    var noncurrent = rule.noncurrentVersionExpiration() != null
                        ? new BucketLifecycleConfiguration.NoncurrentVersionExpiration(
                            rule.noncurrentVersionExpiration().noncurrentDays())
                        : null;
                    var abort = rule.abortIncompleteMultipartUpload() != null
                        ? new BucketLifecycleConfiguration.AbortIncompleteMultipartUpload(
                            rule.abortIncompleteMultipartUpload().daysAfterInitiation())
                        : null;
                    rules.add(new BucketLifecycleConfiguration.LifecycleRule(
                        rule.id(), rule.status(), rule.prefix(), expiration, noncurrent, abort
                    ));
                }
                bucketService.putLifecycleConfiguration(
                    new BucketLifecycleConfiguration(bucket, List.copyOf(rules))
                );
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?lifecycle — DeleteBucketLifecycleConfiguration */
    public Mono<ServerResponse> deleteBucketLifecycle(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteLifecycleConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Bucket Policy ──

    /** GET /{bucket}?policy — GetBucketPolicy */
    public Mono<ServerResponse> getBucketPolicy(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var policy = bucketService.getPolicy(bucket);
            if (policy.isEmpty() || !policy.get().hasPolicy()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucketPolicy",
                    "The bucket policy does not exist");
            }
            // AWS returns policy as raw JSON with Content-Type: application/json
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(policy.get().policyJson());
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?policy — PutBucketPolicy */
    public Mono<ServerResponse> putBucketPolicy(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> {
                bucketService.putPolicy(new BucketPolicy(bucket, body));
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?policy — DeleteBucketPolicy */
    public Mono<ServerResponse> deleteBucketPolicy(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deletePolicy(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Encryption Configuration ──

    /** GET /{bucket}?encryption — GetBucketEncryption */
    public Mono<ServerResponse> getBucketEncryption(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getEncryptionConfiguration(bucket);
            if (config.isEmpty() || !config.get().hasEncryption()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchEncryptionConfiguration",
                    "The encryption configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketEncryptionQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?encryption — PutBucketEncryption */
    public Mono<ServerResponse> putBucketEncryption(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(EncryptionConfigurationCommand.class)
            .flatMap(cmd -> {
                var config = new BucketEncryptionConfiguration(
                    bucket, cmd.ruleId(), cmd.algorithm(), cmd.kmsKeyId()
                );
                bucketService.putEncryptionConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?encryption — DeleteBucketEncryption */
    public Mono<ServerResponse> deleteBucketEncryption(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteEncryptionConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Logging Configuration ──

    /** GET /{bucket}?logging — GetBucketLogging */
    public Mono<ServerResponse> getBucketLogging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getLoggingConfiguration(bucket);
            if (config.isEmpty() || !config.get().hasLogging()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchLoggingConfiguration",
                    "The logging configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketLoggingQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?logging — PutBucketLogging */
    public Mono<ServerResponse> putBucketLogging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(LoggingConfigurationCommand.class)
            .flatMap(cmd -> {
                var config = new BucketLoggingConfiguration(
                    bucket, cmd.targetBucket(), cmd.targetPrefix()
                );
                bucketService.putLoggingConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?logging — DeleteBucketLogging */
    public Mono<ServerResponse> deleteBucketLogging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteLoggingConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Website Configuration ──

    /** GET /{bucket}?website — GetBucketWebsite */
    public Mono<ServerResponse> getBucketWebsite(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getWebsiteConfiguration(bucket);
            if (config.isEmpty() || !config.get().hasWebsite()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchWebsiteConfiguration",
                    "The website configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketWebsiteQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?website — PutBucketWebsite */
    public Mono<ServerResponse> putBucketWebsite(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(WebsiteConfigurationCommand.class)
            .flatMap(cmd -> {
                var config = new BucketWebsiteConfiguration(
                    bucket, cmd.indexDocument(), cmd.errorDocument(),
                    cmd.redirectAllRequestsTo(), cmd.hostName(), cmd.protocol()
                );
                bucketService.putWebsiteConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?website — DeleteBucketWebsite */
    public Mono<ServerResponse> deleteBucketWebsite(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteWebsiteConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Notification Configuration ──

    /** GET /{bucket}?notification — GetBucketNotification */
    public Mono<ServerResponse> getBucketNotification(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getNotificationConfiguration(bucket);
            if (config.isEmpty() || !config.get().hasNotification()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchNotificationConfiguration",
                    "The notification configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketNotificationQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?notification — PutBucketNotification */
    public Mono<ServerResponse> putBucketNotification(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(NotificationConfigurationCommand.class)
            .flatMap(cmd -> {
                var events = cmd.eventConfigurations().stream()
                    .map(e -> new BucketNotificationConfiguration.NotificationEvent(
                        e.event(), e.topicArn(), e.queueArn(), e.lambdaArn(), e.filterRules()
                    ))
                    .toList();
                var config = new BucketNotificationConfiguration(bucket, events);
                bucketService.putNotificationConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?notification — DeleteBucketNotification */
    public Mono<ServerResponse> deleteBucketNotification(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteNotificationConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Replication Configuration ──

    /** GET /{bucket}?replication — GetBucketReplication */
    public Mono<ServerResponse> getBucketReplication(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getReplicationConfiguration(bucket);
            if (config.isEmpty() || !config.get().hasReplication()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchReplicationConfiguration",
                    "The replication configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketReplicationQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?replication — PutBucketReplication */
    public Mono<ServerResponse> putBucketReplication(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(ReplicationConfigurationCommand.class)
            .flatMap(cmd -> {
                var rules = cmd.rules().stream()
                    .map(r -> new BucketReplicationConfiguration.ReplicationRule(
                        r.id(), r.status(), r.prefix(),
                        r.destinationBucket() != null ? "arn:aws:s3:::" + r.destinationBucket() : null,
                        r.destinationStorageClass(), false, false
                    ))
                    .toList();
                var config = new BucketReplicationConfiguration(bucket, cmd.role(), rules);
                bucketService.putReplicationConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?replication — DeleteBucketReplication */
    public Mono<ServerResponse> deleteBucketReplication(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteReplicationConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Request Payment Configuration ──

    /** GET /{bucket}?requestPayment — GetBucketRequestPayment */
    public Mono<ServerResponse> getBucketRequestPayment(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getRequestPaymentConfiguration(bucket);
            if (config.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchRequestPaymentConfiguration",
                    "The request payment configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketRequestPaymentQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?requestPayment — PutBucketRequestPayment */
    public Mono<ServerResponse> putBucketRequestPayment(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(RequestPaymentConfigurationCommand.class)
            .flatMap(cmd -> {
                var config = new BucketRequestPaymentConfiguration(
                    bucket, cmd.payer() != null ? cmd.payer() : "BucketOwner"
                );
                bucketService.putRequestPaymentConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?requestPayment — DeleteBucketRequestPayment */
    public Mono<ServerResponse> deleteBucketRequestPayment(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteRequestPaymentConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Ownership Controls ──

    /** GET /{bucket}?ownershipControls — GetBucketOwnershipControls */
    public Mono<ServerResponse> getBucketOwnershipControls(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var controls = bucketService.getOwnershipControls(bucket);
            if (controls.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchOwnershipControls",
                    "The ownership controls do not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketOwnershipControlsQuery.from(controls));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?ownershipControls — PutBucketOwnershipControls */
    public Mono<ServerResponse> putBucketOwnershipControls(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(OwnershipControlsCommand.class)
            .flatMap(cmd -> {
                var controls = new BucketOwnershipControls(
                    bucket, cmd.id(), cmd.ownership() != null ? cmd.ownership() : "BucketOwnerPreferred"
                );
                bucketService.putOwnershipControls(controls);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?ownershipControls — DeleteBucketOwnershipControls */
    public Mono<ServerResponse> deleteBucketOwnershipControls(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteOwnershipControls(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Public Access Block ──

    /** GET /{bucket}?publicAccessBlock — GetPublicAccessBlock */
    public Mono<ServerResponse> getPublicAccessBlock(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getPublicAccessBlockConfiguration(bucket);
            if (config.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchPublicAccessBlockConfiguration",
                    "The public access block configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(PublicAccessBlockQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?publicAccessBlock — PutPublicAccessBlock */
    public Mono<ServerResponse> putPublicAccessBlock(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(PublicAccessBlockCommand.class)
            .flatMap(cmd -> {
                var config = new PublicAccessBlockConfiguration(
                    bucket,
                    "true".equals(cmd.blockPublicAcls()),
                    "true".equals(cmd.ignorePublicAcls()),
                    "true".equals(cmd.blockPublicPolicy()),
                    "true".equals(cmd.restrictPublicBuckets())
                );
                bucketService.putPublicAccessBlockConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?publicAccessBlock — DeletePublicAccessBlock */
    public Mono<ServerResponse> deletePublicAccessBlock(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deletePublicAccessBlockConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ── Accelerate Configuration ──

    /** GET /{bucket}?accelerate — GetBucketAccelerateConfiguration */
    public Mono<ServerResponse> getBucketAccelerate(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getAccelerateConfiguration(bucket);
            if (config.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchAccelerateConfiguration",
                    "The accelerate configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketAccelerateQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?accelerate — PutBucketAccelerateConfiguration */
    public Mono<ServerResponse> putBucketAccelerate(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(AccelerateConfigurationCommand.class)
            .flatMap(cmd -> {
                var config = new BucketAccelerateConfiguration(
                    bucket, cmd.status() != null ? cmd.status() : "Suspended"
                );
                bucketService.putAccelerateConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?accelerate — DeleteBucketAccelerateConfiguration */
    public Mono<ServerResponse> deleteBucketAccelerate(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteAccelerateConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ─────────────────────────────────────────────────────
    //  Analytics Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?analytics&analyticsId={id} — GetBucketAnalyticsConfiguration */
    public Mono<ServerResponse> getBucketAnalytics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var analyticsId = request.queryParam("analyticsId").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            if (analyticsId == null || analyticsId.isBlank()) {
                return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingAnalyticsId",
                    "The analyticsId query parameter is required");
            }
            var config = bucketService.getAnalyticsConfiguration(bucket, analyticsId);
            if (config.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchAnalyticsConfiguration",
                    "The analytics configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketAnalyticsQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?analytics&analyticsId={id} — PutBucketAnalyticsConfiguration */
    public Mono<ServerResponse> putBucketAnalytics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var analyticsId = request.queryParam("analyticsId").orElse(null);
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        if (analyticsId == null || analyticsId.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingAnalyticsId",
                "The analyticsId query parameter is required");
        }
        return request.bodyToMono(AnalyticsConfigurationCommand.class)
            .flatMap(cmd -> {
                var filterRule = cmd.filter() != null ? cmd.filter().prefix() : null;
                var config = new BucketAnalyticsConfiguration(bucket, analyticsId, filterRule);
                bucketService.putAnalyticsConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?analytics&analyticsId={id} — DeleteBucketAnalyticsConfiguration */
    public Mono<ServerResponse> deleteBucketAnalytics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var analyticsId = request.queryParam("analyticsId").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            if (analyticsId == null || analyticsId.isBlank()) {
                return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingAnalyticsId",
                    "The analyticsId query parameter is required");
            }
            bucketService.deleteAnalyticsConfiguration(bucket, analyticsId);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** GET /{bucket}?analytics&list-type — ListBucketAnalyticsConfigurations */
    public Mono<ServerResponse> listBucketAnalyticsConfigurations(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var configs = bucketService.listAnalyticsConfigurations(bucket);
            // Build list response
            var analyticsIdList = configs.stream()
                .map(c -> c.analyticsId())
                .toList();
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketAnalyticsListQuery.fromIds(analyticsIdList));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ─────────────────────────────────────────────────────
    //  Inventory Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?inventory&inventoryId={id} — GetBucketInventoryConfiguration */
    public Mono<ServerResponse> getBucketInventory(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var inventoryId = request.queryParam("inventoryId").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            if (inventoryId == null || inventoryId.isBlank()) {
                return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingInventoryId",
                    "The inventoryId query parameter is required");
            }
            var config = bucketService.getInventoryConfiguration(bucket, inventoryId);
            if (config.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchInventoryConfiguration",
                    "The inventory configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketInventoryQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?inventory&inventoryId={id} — PutBucketInventoryConfiguration */
    public Mono<ServerResponse> putBucketInventory(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var inventoryId = request.queryParam("inventoryId").orElse(null);
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        if (inventoryId == null || inventoryId.isBlank()) {
            return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingInventoryId",
                "The inventoryId query parameter is required");
        }
        return request.bodyToMono(InventoryConfigurationCommand.class)
            .flatMap(cmd -> {
                var format = cmd.destination() != null ? cmd.destination().format() : null;
                var frequency = cmd.schedule() != null ? cmd.schedule().frequency() : null;
                var enabled = "true".equals(cmd.enabled());
                var config = new BucketInventoryConfiguration(bucket, inventoryId, format, frequency, enabled);
                bucketService.putInventoryConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?inventory&inventoryId={id} — DeleteBucketInventoryConfiguration */
    public Mono<ServerResponse> deleteBucketInventory(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var inventoryId = request.queryParam("inventoryId").orElse(null);
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            if (inventoryId == null || inventoryId.isBlank()) {
                return S3WebSupport.xmlError(HttpStatus.BAD_REQUEST, "MissingInventoryId",
                    "The inventoryId query parameter is required");
            }
            bucketService.deleteInventoryConfiguration(bucket, inventoryId);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** GET /{bucket}?inventory&list-type — ListBucketInventoryConfigurations */
    public Mono<ServerResponse> listBucketInventoryConfigurations(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var configs = bucketService.listInventoryConfigurations(bucket);
            var inventoryIdList = configs.stream()
                .map(c -> c.inventoryId())
                .toList();
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketInventoryListQuery.fromIds(inventoryIdList));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ─────────────────────────────────────────────────────
    //  Metrics Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?metrics — GetBucketMetricsConfiguration */
    public Mono<ServerResponse> getBucketMetrics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getMetricsConfiguration(bucket);
            if (config.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchMetricsConfiguration",
                    "The metrics configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketMetricsQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?metrics — PutBucketMetricsConfiguration */
    public Mono<ServerResponse> putBucketMetrics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(MetricsConfigurationCommand.class)
            .flatMap(cmd -> {
                var filterRule = cmd.filter() != null ? cmd.filter().prefix() : null;
                var config = new BucketMetricsConfiguration(bucket, cmd.id(), filterRule);
                bucketService.putMetricsConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?metrics — DeleteBucketMetricsConfiguration */
    public Mono<ServerResponse> deleteBucketMetrics(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteMetricsConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    // ─────────────────────────────────────────────────────
    //  Intelligent-Tiering Configuration
    // ─────────────────────────────────────────────────────

    /** GET /{bucket}?intelligent-tiering — GetBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> getBucketIntelligentTiering(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            var config = bucketService.getIntelligentTieringConfiguration(bucket);
            if (config.isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchIntelligentTieringConfiguration",
                    "The intelligent-tiering configuration does not exist");
            }
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_XML)
                .bodyValue(BucketIntelligentTieringQuery.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?intelligent-tiering — PutBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> putBucketIntelligentTiering(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
            return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
        }
        return request.bodyToMono(IntelligentTieringConfigurationCommand.class)
            .flatMap(cmd -> {
                var tieringPolicy = cmd.tieringPolicy() != null ? cmd.tieringPolicy().tieringRule() : null;
                var config = new BucketIntelligentTieringConfiguration(
                    bucket, cmd.id(), tieringPolicy, cmd.autoTieringStatus()
                );
                bucketService.putIntelligentTieringConfiguration(config);
                return ServerResponse.ok().build();
            });
    }

    /** DELETE /{bucket}?intelligent-tiering — DeleteBucketIntelligentTieringConfiguration */
    public Mono<ServerResponse> deleteBucketIntelligentTiering(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            bucketService.deleteIntelligentTieringConfiguration(bucket);
            return ServerResponse.noContent().build();
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }
}
