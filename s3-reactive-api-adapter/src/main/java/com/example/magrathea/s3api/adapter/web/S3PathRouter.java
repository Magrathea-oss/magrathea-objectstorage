package com.example.magrathea.s3api.adapter.web;

import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_XML;
import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * S3-compatible route composition.
 *
 * This class only declares HTTP route mappings. Endpoint behavior is delegated
 * to context-specific handlers so bucket and object concerns stay readable.
 */
public class S3PathRouter {

    private static final String OBJECT_PATH = "/{bucket}/{*key}";

    private final S3BucketOperationsHandler bucketOperations;
    private final S3BucketMetadataHandler bucketMetadata;
    private final S3ObjectOperationsHandler objectOperations;
    private final S3ObjectMetadataHandler objectMetadata;
    private final S3BucketConfigHandler bucketConfig;
    private final S3MultipartHandler multipartHandler;
    private final S3SessionHandler sessionHandler;

    public S3PathRouter(S3BucketOperationsHandler bucketOperations,
                         S3BucketMetadataHandler bucketMetadata,
                         S3ObjectOperationsHandler objectOperations,
                         S3ObjectMetadataHandler objectMetadata,
                         S3BucketConfigHandler bucketConfig,
                         S3MultipartHandler multipartHandler,
                         S3SessionHandler sessionHandler) {
        this.bucketOperations = bucketOperations;
        this.bucketMetadata = bucketMetadata;
        this.objectOperations = objectOperations;
        this.objectMetadata = objectMetadata;
        this.bucketConfig = bucketConfig;
        this.multipartHandler = multipartHandler;
        this.sessionHandler = sessionHandler;
    }

    public RouterFunction<ServerResponse> s3Routes() {
        return RouterFunctions
            .route()
            .GET("/", accept(APPLICATION_XML), bucketOperations::listBucketsXml)
            .GET("/", accept(APPLICATION_JSON), bucketOperations::listBucketsJson)
            .GET("/", queryParam("directory-buckets",v -> true), bucketOperations::listDirectoryBuckets)

            .GET("/{bucket}", queryParam("acl",v -> true), bucketMetadata::getBucketAcl)
            .GET("/{bucket}", queryParam("tagging", v -> true), bucketMetadata::getBucketTagging)
            .GET("/{bucket}", queryParam("location", v -> true), bucketOperations::getBucketLocation)
            .GET("/{bucket}", queryParam("versioning", v -> true), bucketOperations::getBucketVersioning)
            .GET("/{bucket}", queryParam("versions", v -> true), bucketOperations::listObjectVersions)
            .GET("/{bucket}", queryParam("list-type","2"), bucketOperations::listObjectsV2Xml)
            .GET("/{bucket}", queryParam("cors", v -> true), bucketConfig::getBucketCors)
            .GET("/{bucket}", queryParam("lifecycle", v -> true), bucketConfig::getBucketLifecycle)
            .GET("/{bucket}", queryParam("policy", v -> true), bucketConfig::getBucketPolicy)
            .GET("/{bucket}", queryParam("encryption", v -> true), bucketConfig::getBucketEncryption)
            .GET("/{bucket}", queryParam("logging", v -> true), bucketConfig::getBucketLogging)
            .GET("/{bucket}", queryParam("website", v -> true), bucketConfig::getBucketWebsite)
            .GET("/{bucket}", queryParam("notification", v -> true), bucketConfig::getBucketNotification)
            .GET("/{bucket}", queryParam("replication", v -> true), bucketConfig::getBucketReplication)
            .GET("/{bucket}", queryParam("requestPayment", v -> true), bucketConfig::getBucketRequestPayment)
            .GET("/{bucket}", queryParam("ownershipControls", v -> true), bucketConfig::getBucketOwnershipControls)
            .GET("/{bucket}", queryParam("publicAccessBlock", v -> true), bucketConfig::getPublicAccessBlock)
            .GET("/{bucket}", queryParam("accelerate", v -> true), bucketConfig::getBucketAccelerate)
            .GET("/{bucket}", queryParam("analytics", v -> true).and(queryParam("list-type", v -> true).negate()), bucketConfig::getBucketAnalytics)
            .GET("/{bucket}", queryParam("analytics", v -> true).and(queryParam("list-type", v -> true)), bucketConfig::listBucketAnalyticsConfigurations)
            .GET("/{bucket}", queryParam("inventory", v -> true).and(queryParam("list-type", v -> true).negate()), bucketConfig::getBucketInventory)
            .GET("/{bucket}", queryParam("inventory", v -> true).and(queryParam("list-type", v -> true)), bucketConfig::listBucketInventoryConfigurations)
            .GET("/{bucket}", queryParam("metrics", v -> true), bucketConfig::getBucketMetrics)
            .GET("/{bucket}", queryParam("intelligent-tiering", v -> true), bucketConfig::getBucketIntelligentTiering)
            .GET("/{bucket}", queryParam("uploads", v -> true), multipartHandler::listMultipartUploads)
            .GET("/{bucket}", queryParam("object-lock", v -> true), bucketConfig::getObjectLockConfiguration)
            .PUT("/{bucket}", queryParam("object-lock", v -> true), bucketConfig::putObjectLockConfiguration)
            .GET("/{bucket}", queryParam("abac", v -> true), bucketConfig::getBucketAbac)
            .PUT("/{bucket}", queryParam("abac", v -> true), bucketConfig::putBucketAbac)
            .GET("/{bucket}", queryParam("metadata-config", v -> true), bucketConfig::getBucketMetadataConfiguration)
            .PUT("/{bucket}", queryParam("metadata-config", v -> true), bucketConfig::putBucketMetadataConfiguration)
            .DELETE("/{bucket}", queryParam("metadata-config", v -> true), bucketConfig::deleteBucketMetadataConfiguration)
            .GET("/{bucket}", queryParam("metadata-table-config", v -> true), bucketConfig::getBucketMetadataTableConfiguration)
            .PUT("/{bucket}", queryParam("metadata-table-config", v -> true), bucketConfig::putBucketMetadataTableConfiguration)
            .DELETE("/{bucket}", queryParam("metadata-table-config", v -> true), bucketConfig::deleteBucketMetadataTableConfiguration)
            .GET("/{bucket}", queryParam("inventory-table-config", v -> true), bucketConfig::getBucketInventoryTableConfiguration)
            .PUT("/{bucket}", queryParam("inventory-table-config", v -> true), bucketConfig::putBucketInventoryTableConfiguration)
            .GET("/{bucket}", queryParam("journal-table-config", v -> true), bucketConfig::getBucketJournalTableConfiguration)
            .PUT("/{bucket}", queryParam("journal-table-config", v -> true), bucketConfig::putBucketJournalTableConfiguration)
            .GET("/{bucket}", accept(APPLICATION_XML), bucketOperations::listObjectsXml)
            .PUT("/{bucket}", queryParam("acl", v -> true), bucketMetadata::putBucketAcl)
            .PUT("/{bucket}", queryParam("tagging", v -> true), bucketMetadata::putBucketTagging)
            .PUT("/{bucket}", queryParam("versioning", v -> true), bucketOperations::putBucketVersioning)
            .PUT("/{bucket}", queryParam("cors", v -> true), bucketConfig::putBucketCors)
            .PUT("/{bucket}", queryParam("lifecycle", v -> true), bucketConfig::putBucketLifecycle)
            .PUT("/{bucket}", queryParam("policy", v -> true), bucketConfig::putBucketPolicy)
            .PUT("/{bucket}", queryParam("encryption", v -> true), bucketConfig::putBucketEncryption)
            .PUT("/{bucket}", queryParam("logging", v -> true), bucketConfig::putBucketLogging)
            .PUT("/{bucket}", queryParam("website", v -> true), bucketConfig::putBucketWebsite)
            .PUT("/{bucket}", queryParam("notification", v -> true), bucketConfig::putBucketNotification)
            .PUT("/{bucket}", queryParam("replication", v -> true), bucketConfig::putBucketReplication)
            .PUT("/{bucket}", queryParam("requestPayment", v -> true), bucketConfig::putBucketRequestPayment)
            .PUT("/{bucket}", queryParam("ownershipControls", v -> true), bucketConfig::putBucketOwnershipControls)
            .PUT("/{bucket}", queryParam("publicAccessBlock", v -> true), bucketConfig::putPublicAccessBlock)
            .PUT("/{bucket}", queryParam("accelerate", v -> true), bucketConfig::putBucketAccelerate)
            .PUT("/{bucket}", queryParam("analytics", v -> true), bucketConfig::putBucketAnalytics)
            .PUT("/{bucket}", queryParam("inventory", v -> true), bucketConfig::putBucketInventory)
            .PUT("/{bucket}", queryParam("metrics", v -> true), bucketConfig::putBucketMetrics)
            .PUT("/{bucket}", queryParam("intelligent-tiering", v -> true), bucketConfig::putBucketIntelligentTiering)
            .PUT("/{bucket}", bucketOperations::createBucket)
            .HEAD("/{bucket}", bucketOperations::headBucket)
            .DELETE("/{bucket}", queryParam("tagging", v -> true), bucketMetadata::deleteBucketTagging)
            .DELETE("/{bucket}", queryParam("cors", v -> true), bucketConfig::deleteBucketCors)
            .DELETE("/{bucket}", queryParam("lifecycle", v -> true), bucketConfig::deleteBucketLifecycle)
            .DELETE("/{bucket}", queryParam("policy", v -> true), bucketConfig::deleteBucketPolicy)
            .DELETE("/{bucket}", queryParam("encryption", v -> true), bucketConfig::deleteBucketEncryption)
            .DELETE("/{bucket}", queryParam("logging", v -> true), bucketConfig::deleteBucketLogging)
            .DELETE("/{bucket}", queryParam("website", v -> true), bucketConfig::deleteBucketWebsite)
            .DELETE("/{bucket}", queryParam("notification", v -> true), bucketConfig::deleteBucketNotification)
            .DELETE("/{bucket}", queryParam("replication", v -> true), bucketConfig::deleteBucketReplication)
            .DELETE("/{bucket}", queryParam("requestPayment", v -> true), bucketConfig::deleteBucketRequestPayment)
            .DELETE("/{bucket}", queryParam("ownershipControls", v -> true), bucketConfig::deleteBucketOwnershipControls)
            .DELETE("/{bucket}", queryParam("publicAccessBlock", v -> true), bucketConfig::deletePublicAccessBlock)
            .DELETE("/{bucket}", queryParam("accelerate", v -> true), bucketConfig::deleteBucketAccelerate)
            .DELETE("/{bucket}", queryParam("analytics", v -> true), bucketConfig::deleteBucketAnalytics)
            .DELETE("/{bucket}", queryParam("inventory", v -> true), bucketConfig::deleteBucketInventory)
            .DELETE("/{bucket}", queryParam("metrics", v -> true), bucketConfig::deleteBucketMetrics)
            .DELETE("/{bucket}", queryParam("intelligent-tiering", v -> true), bucketConfig::deleteBucketIntelligentTiering)
            .DELETE("/{bucket}", bucketOperations::deleteBucket)

            .POST("/{bucket}", queryParam("delete", v -> true), objectOperations::deleteObjects)
            .POST("/{bucket}", queryParam("session", v -> true), sessionHandler::createSession)
            .POST(OBJECT_PATH, queryParam("uploads", v -> true), multipartHandler::initiateMultipartUpload)
            .POST(OBJECT_PATH, queryParam("uploadId", v -> true), multipartHandler::completeMultipartUpload)
            .GET(OBJECT_PATH, queryParam("acl", v -> true), objectMetadata::getObjectAcl)
            .GET(OBJECT_PATH, queryParam("tagging", v -> true), objectMetadata::getObjectTagging)
            .GET(OBJECT_PATH, queryParam("attributes", v -> true), objectMetadata::getObjectAttributes)
            .GET(OBJECT_PATH, queryParam("legal-hold", v -> true), objectMetadata::getObjectLegalHold)
            .GET(OBJECT_PATH, queryParam("retention", v -> true), objectMetadata::getObjectRetention)
            .GET(OBJECT_PATH, queryParam("uploadId", v -> true), multipartHandler::listParts)
            .PUT(OBJECT_PATH, queryParam("acl", v -> true), objectMetadata::putObjectAcl)
            .PUT(OBJECT_PATH, queryParam("tagging", v -> true), objectMetadata::putObjectTagging)
            .PUT(OBJECT_PATH, queryParam("legal-hold", v -> true), objectMetadata::putObjectLegalHold)
            .PUT(OBJECT_PATH, queryParam("retention", v -> true), objectMetadata::putObjectRetention)
            .PUT(OBJECT_PATH, queryParam("uploadId", v -> true).and(headers(h -> h.firstHeader("x-amz-copy-source") != null)), multipartHandler::uploadPartCopy)
            .PUT(OBJECT_PATH, queryParam("uploadId", v -> true), multipartHandler::uploadPart)
            .PUT(OBJECT_PATH, request -> request.headers().firstHeader("x-amz-copy-source") != null, objectOperations::copyObject)
            .PUT(OBJECT_PATH, queryParam("rename", v -> true), objectOperations::renameObject)
            .PUT(OBJECT_PATH, queryParam("encryption", v -> true), objectMetadata::updateObjectEncryption)
            .PUT(OBJECT_PATH, request -> "WriteGetObjectResponse".equals(request.queryParam("x-id").orElse("")), objectOperations::writeGetObjectResponse)
            .PUT(OBJECT_PATH, objectOperations::putObject)
            .GET(OBJECT_PATH, queryParam("torrent", v -> true), objectOperations::getObjectTorrent)
            .GET(OBJECT_PATH, objectOperations::getObject)
            .HEAD(OBJECT_PATH, objectOperations::headObject)
            .DELETE(OBJECT_PATH, queryParam("tagging", v -> true), objectMetadata::deleteObjectTagging)
            .DELETE(OBJECT_PATH, queryParam("uploadId", v -> true), multipartHandler::abortMultipartUpload)
            .DELETE(OBJECT_PATH, objectOperations::deleteObject)
            .POST(OBJECT_PATH, queryParam("restore", v -> true), objectOperations::restoreObject)
            .POST(OBJECT_PATH, queryParam("select", v -> true), objectOperations::selectObjectContent)
            .build();
    }
}
