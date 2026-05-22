package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstorage.application.dto.CorsConfigurationCommand;
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
import com.example.magrathea.s3api.adapter.web.xml.S3XmlResponses;
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
                .bodyValue(S3XmlResponses.CORSConfiguration.from(bucket, config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?cors — PutBucketCors */
    public Mono<ServerResponse> putBucketCors(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var rules = parseCorsXml(body);
                    var cmd = new CorsConfigurationCommand(bucket, rules);
                    bucketService.putCorsConfiguration(cmd);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
                .bodyValue(S3XmlResponses.LifecycleConfiguration.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?lifecycle — PutBucketLifecycleConfiguration */
    public Mono<ServerResponse> putBucketLifecycle(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var config = parseLifecycleXml(bucket, body);
                    bucketService.putLifecycleConfiguration(config);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    bucketService.putPolicy(new BucketPolicy(bucket, body));
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
                .bodyValue(S3XmlResponses.EncryptionConfiguration.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?encryption — PutBucketEncryption */
    public Mono<ServerResponse> putBucketEncryption(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var config = parseEncryptionXml(bucket, body);
                    bucketService.putEncryptionConfiguration(config);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
                .bodyValue(S3XmlResponses.LoggingConfiguration.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?logging — PutBucketLogging */
    public Mono<ServerResponse> putBucketLogging(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var config = parseLoggingXml(bucket, body);
                    bucketService.putLoggingConfiguration(config);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
                .bodyValue(S3XmlResponses.WebsiteConfiguration.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?website — PutBucketWebsite */
    public Mono<ServerResponse> putBucketWebsite(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var config = parseWebsiteXml(bucket, body);
                    bucketService.putWebsiteConfiguration(config);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
                .bodyValue(S3XmlResponses.NotificationConfiguration.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?notification — PutBucketNotification */
    public Mono<ServerResponse> putBucketNotification(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var config = parseNotificationXml(bucket, body);
                    bucketService.putNotificationConfiguration(config);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
                .bodyValue(S3XmlResponses.ReplicationConfiguration.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?replication — PutBucketReplication */
    public Mono<ServerResponse> putBucketReplication(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var config = parseReplicationXml(bucket, body);
                    bucketService.putReplicationConfiguration(config);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
                .bodyValue(S3XmlResponses.RequestPaymentConfiguration.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?requestPayment — PutBucketRequestPayment */
    public Mono<ServerResponse> putBucketRequestPayment(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var payer = extractXmlValue(body, "Payer");
                    var config = new BucketRequestPaymentConfiguration(bucket, payer.isEmpty() ? "BucketOwner" : payer);
                    bucketService.putRequestPaymentConfiguration(config);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
                .bodyValue(S3XmlResponses.OwnershipControls.from(controls));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?ownershipControls — PutBucketOwnershipControls */
    public Mono<ServerResponse> putBucketOwnershipControls(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var ruleId = extractXmlValue(body, "ID");
                    var ownership = extractXmlValue(body, "Ownership");
                    var controls = new BucketOwnershipControls(
                        bucket,
                        ruleId.isEmpty() ? null : ruleId,
                        ownership.isEmpty() ? "BucketOwnerPreferred" : ownership
                    );
                    bucketService.putOwnershipControls(controls);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
                .bodyValue(S3XmlResponses.PublicAccessBlockConfiguration.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?publicAccessBlock — PutPublicAccessBlock */
    public Mono<ServerResponse> putPublicAccessBlock(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var blockPublicAcls = "true".equals(extractXmlValue(body, "BlockPublicAcls"));
                    var ignorePublicAcls = "true".equals(extractXmlValue(body, "IgnorePublicAcls"));
                    var blockPublicPolicy = "true".equals(extractXmlValue(body, "BlockPublicPolicy"));
                    var restrictPublicBuckets = "true".equals(extractXmlValue(body, "RestrictPublicBuckets"));
                    var config = new PublicAccessBlockConfiguration(
                        bucket, blockPublicAcls, ignorePublicAcls,
                        blockPublicPolicy, restrictPublicBuckets
                    );
                    bucketService.putPublicAccessBlockConfiguration(config);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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
                .bodyValue(S3XmlResponses.AccelerateConfiguration.from(config));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
    }

    /** PUT /{bucket}?accelerate — PutBucketAccelerateConfiguration */
    public Mono<ServerResponse> putBucketAccelerate(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        return Mono.fromCallable(() -> {
            if (S3WebSupport.findBucket(bucketService, bucket).isEmpty()) {
                return S3WebSupport.xmlError(HttpStatus.NOT_FOUND, "NoSuchBucket", "Bucket not found");
            }
            return request.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    var status = extractXmlValue(body, "Status");
                    var config = new BucketAccelerateConfiguration(
                        bucket, status.isEmpty() ? "Suspended" : status
                    );
                    bucketService.putAccelerateConfiguration(config);
                    return ServerResponse.ok().build();
                });
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(Mono::from);
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

    private static List<CorsConfigurationCommand.CorsRuleDto> parseCorsXml(String body) {
        // Minimal CORS XML parser — extracts CORSRule elements
        var rules = new ArrayList<CorsConfigurationCommand.CorsRuleDto>();
        var ruleMatcher = java.util.regex.Pattern.compile(
            "<CORSRule>(.*?)</CORSRule>", java.util.regex.Pattern.DOTALL
        ).matcher(body);
        while (ruleMatcher.find()) {
            var ruleBody = ruleMatcher.group(1);
            var origins = extractXmlList(ruleBody, "AllowedOrigin");
            var methods = extractXmlList(ruleBody, "AllowedMethod");
            var headers = extractXmlList(ruleBody, "AllowedHeader");
            var exposeHeaders = extractXmlList(ruleBody, "ExposeHeader");
            var maxAge = extractXmlValue(ruleBody, "MaxAgeSeconds");
            var id = extractXmlValue(ruleBody, "ID");
            rules.add(new CorsConfigurationCommand.CorsRuleDto(
                origins, methods, headers,
                maxAge.isEmpty() ? 0 : Integer.parseInt(maxAge),
                exposeHeaders, id.isEmpty() ? null : id
            ));
        }
        return List.copyOf(rules);
    }

    private static BucketLifecycleConfiguration parseLifecycleXml(String bucketName, String body) {
        var rules = new ArrayList<BucketLifecycleConfiguration.LifecycleRule>();
        var ruleMatcher = java.util.regex.Pattern.compile(
            "<Rule>(.*?)</Rule>", java.util.regex.Pattern.DOTALL
        ).matcher(body);
        while (ruleMatcher.find()) {
            var ruleBody = ruleMatcher.group(1);
            var id = extractXmlValue(ruleBody, "ID");
            var status = extractXmlValue(ruleBody, "Status");
            var prefix = extractXmlValue(ruleBody, "Prefix");
            var expirationDays = extractXmlValue(ruleBody, "Days");
            var expirationDate = extractXmlValue(ruleBody, "Date");
            var noncurrentDays = extractXmlValue(ruleBody, "NoncurrentDays");
            var daysAfterInitiation = extractXmlValue(ruleBody, "DaysAfterInitiation");
            var expiration = (expirationDays.isEmpty() && expirationDate.isEmpty())
                ? null
                : new BucketLifecycleConfiguration.Expiration(
                    expirationDays.isEmpty() ? null : expirationDays,
                    expirationDate.isEmpty() ? null : expirationDate);
            var noncurrentVersionExpiration = noncurrentDays.isEmpty()
                ? null
                : new BucketLifecycleConfiguration.NoncurrentVersionExpiration(noncurrentDays);
            var abortIncomplete = daysAfterInitiation.isEmpty()
                ? null
                : new BucketLifecycleConfiguration.AbortIncompleteMultipartUpload(daysAfterInitiation);
            rules.add(new BucketLifecycleConfiguration.LifecycleRule(
                id.isEmpty() ? null : id,
                status,
                prefix.isEmpty() ? null : prefix,
                expiration,
                noncurrentVersionExpiration,
                abortIncomplete
            ));
        }
        return new BucketLifecycleConfiguration(bucketName, List.copyOf(rules));
    }

    private static BucketEncryptionConfiguration parseEncryptionXml(String bucketName, String body) {
        var ruleId = extractXmlValue(body, "RuleId");
        var algorithm = extractXmlValue(body, "Algorithm");
        var kmsKeyId = extractXmlValue(body, "KMSKeyId");
        return new BucketEncryptionConfiguration(
            bucketName,
            ruleId.isEmpty() ? null : ruleId,
            algorithm.isEmpty() ? null : algorithm,
            kmsKeyId.isEmpty() ? null : kmsKeyId
        );
    }

    private static BucketLoggingConfiguration parseLoggingXml(String bucketName, String body) {
        var targetBucket = extractXmlValue(body, "TargetBucket");
        var targetPrefix = extractXmlValue(body, "TargetPrefix");
        return new BucketLoggingConfiguration(
            bucketName,
            targetBucket.isEmpty() ? null : targetBucket,
            targetPrefix.isEmpty() ? null : targetPrefix
        );
    }

    private static BucketWebsiteConfiguration parseWebsiteXml(String bucketName, String body) {
        var indexDoc = extractXmlValue(body, "IndexDocument");
        var errorDoc = extractXmlValue(body, "ErrorDocument");
        var redirectAll = extractXmlValue(body, "RedirectAllRequestsTo");
        var routingHost = extractXmlValue(body, "HostName");
        var routingProtocol = extractXmlValue(body, "Protocol");
        return new BucketWebsiteConfiguration(
            bucketName,
            indexDoc.isEmpty() ? null : indexDoc,
            errorDoc.isEmpty() ? null : errorDoc,
            redirectAll.isEmpty() ? null : redirectAll,
            routingHost.isEmpty() ? null : routingHost,
            routingProtocol.isEmpty() ? null : routingProtocol
        );
    }

    private static BucketNotificationConfiguration parseNotificationXml(String bucketName, String body) {
        var events = new ArrayList<BucketNotificationConfiguration.NotificationEvent>();
        var eventMatcher = java.util.regex.Pattern.compile(
            "<EventConfiguration>(.*?)</EventConfiguration>", java.util.regex.Pattern.DOTALL
        ).matcher(body);
        while (eventMatcher.find()) {
            var evBody = eventMatcher.group(1);
            var event = extractXmlValue(evBody, "Event");
            var topicArn = extractXmlValue(evBody, "TopicArn");
            var queueArn = extractXmlValue(evBody, "QueueArn");
            var lambdaArn = extractXmlValue(evBody, "LambdaArn");
            var filterRules = extractXmlList(evBody, "FilterRule");
            events.add(new BucketNotificationConfiguration.NotificationEvent(
                event.isEmpty() ? "s3:ObjectCreated:*" : event,
                topicArn.isEmpty() ? null : topicArn,
                queueArn.isEmpty() ? null : queueArn,
                lambdaArn.isEmpty() ? null : lambdaArn,
                filterRules.isEmpty() ? null : List.copyOf(filterRules)
            ));
        }
        return new BucketNotificationConfiguration(bucketName, List.copyOf(events));
    }

    private static BucketReplicationConfiguration parseReplicationXml(String bucketName, String body) {
        var role = extractXmlValue(body, "Role");
        var rules = new ArrayList<BucketReplicationConfiguration.ReplicationRule>();
        var ruleMatcher = java.util.regex.Pattern.compile(
            "<Rule>(.*?)</Rule>", java.util.regex.Pattern.DOTALL
        ).matcher(body);
        while (ruleMatcher.find()) {
            var ruleBody = ruleMatcher.group(1);
            var id = extractXmlValue(ruleBody, "ID");
            var status = extractXmlValue(ruleBody, "Status");
            var prefix = extractXmlValue(ruleBody, "Prefix");
            var destBucket = extractXmlValue(ruleBody, "Bucket");
            var destStorageClass = extractXmlValue(ruleBody, "StorageClass");
            rules.add(new BucketReplicationConfiguration.ReplicationRule(
                id.isEmpty() ? null : id,
                status.isEmpty() ? "Enabled" : status,
                prefix.isEmpty() ? null : prefix,
                destBucket.isEmpty() ? null : "arn:aws:s3:::" + destBucket,
                destStorageClass.isEmpty() ? null : destStorageClass,
                false, false
            ));
        }
        return new BucketReplicationConfiguration(
            bucketName,
            role.isEmpty() ? null : role,
            List.copyOf(rules)
        );
    }

    private static List<String> extractXmlList(String body, String tag) {
        var matcher = java.util.regex.Pattern.compile(
            "<" + tag + ">([^<]+)</" + tag + ">", java.util.regex.Pattern.DOTALL
        ).matcher(body);
        var result = new ArrayList<String>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static String extractXmlValue(String body, String tag) {
        var matcher = java.util.regex.Pattern.compile(
            "<" + tag + ">([^<]+)</" + tag + ">", java.util.regex.Pattern.DOTALL
        ).matcher(body);
        return matcher.find() ? matcher.group(1) : "";
    }
}
