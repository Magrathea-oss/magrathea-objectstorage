package com.example.magrathea.s3api.adapter.web;

import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * S3-compatible route composition.
 *
 * This class only declares HTTP route mappings. Endpoint behavior is delegated
 * to context-specific handlers so bucket and object concerns stay readable.
 */
public class S3ProxyRouter {

    private final S3BucketOperationsHandler bucketOperations;
    private final S3BucketMetadataHandler bucketMetadata;
    private final S3ObjectOperationsHandler objectOperations;
    private final S3ObjectMetadataHandler objectMetadata;
    private final S3BucketConfigHandler bucketConfig;
    private final S3MultipartHandler multipartHandler;

    public S3ProxyRouter(S3BucketOperationsHandler bucketOperations,
                         S3BucketMetadataHandler bucketMetadata,
                         S3ObjectOperationsHandler objectOperations,
                         S3ObjectMetadataHandler objectMetadata,
                         S3BucketConfigHandler bucketConfig,
                         S3MultipartHandler multipartHandler) {
        this.bucketOperations = bucketOperations;
        this.bucketMetadata = bucketMetadata;
        this.objectOperations = objectOperations;
        this.objectMetadata = objectMetadata;
        this.bucketConfig = bucketConfig;
        this.multipartHandler = multipartHandler;
    }

    public RouterFunction<ServerResponse> s3Routes() {
        return RouterFunctions
            .route()
            .GET("/", S3WebSupport::acceptXml, bucketOperations::listBucketsXml)
            .GET("/", S3WebSupport::acceptJson, bucketOperations::listBucketsJson)

            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "acl"), bucketMetadata::getBucketAcl)
            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "tagging"), bucketMetadata::getBucketTagging)
            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "location"), bucketOperations::getBucketLocation)
            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "versioning"), bucketOperations::getBucketVersioning)
            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "versions"), bucketOperations::listObjectVersions)
            .GET("/{bucket}", request -> "2".equals(request.queryParam("list-type").orElse("")), bucketOperations::listObjectsV2Xml)
            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "cors"), bucketConfig::getBucketCors)
            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "lifecycle"), bucketConfig::getBucketLifecycle)
            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "policy"), bucketConfig::getBucketPolicy)
            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "encryption"), bucketConfig::getBucketEncryption)
            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "logging"), bucketConfig::getBucketLogging)
            .GET("/{bucket}", request -> S3WebSupport.hasQuery(request, "uploads"), multipartHandler::listMultipartUploads)
            .GET("/{bucket}", S3WebSupport::acceptXml, bucketOperations::listObjectsXml)
            .PUT("/{bucket}", request -> S3WebSupport.hasQuery(request, "acl"), bucketMetadata::putBucketAcl)
            .PUT("/{bucket}", request -> S3WebSupport.hasQuery(request, "tagging"), bucketMetadata::putBucketTagging)
            .PUT("/{bucket}", request -> S3WebSupport.hasQuery(request, "versioning"), bucketOperations::putBucketVersioning)
            .PUT("/{bucket}", request -> S3WebSupport.hasQuery(request, "cors"), bucketConfig::putBucketCors)
            .PUT("/{bucket}", request -> S3WebSupport.hasQuery(request, "lifecycle"), bucketConfig::putBucketLifecycle)
            .PUT("/{bucket}", request -> S3WebSupport.hasQuery(request, "policy"), bucketConfig::putBucketPolicy)
            .PUT("/{bucket}", request -> S3WebSupport.hasQuery(request, "encryption"), bucketConfig::putBucketEncryption)
            .PUT("/{bucket}", request -> S3WebSupport.hasQuery(request, "logging"), bucketConfig::putBucketLogging)
            .PUT("/{bucket}", bucketOperations::createBucket)
            .HEAD("/{bucket}", bucketOperations::headBucket)
            .DELETE("/{bucket}", request -> S3WebSupport.hasQuery(request, "tagging"), bucketMetadata::deleteBucketTagging)
            .DELETE("/{bucket}", request -> S3WebSupport.hasQuery(request, "cors"), bucketConfig::deleteBucketCors)
            .DELETE("/{bucket}", request -> S3WebSupport.hasQuery(request, "lifecycle"), bucketConfig::deleteBucketLifecycle)
            .DELETE("/{bucket}", request -> S3WebSupport.hasQuery(request, "policy"), bucketConfig::deleteBucketPolicy)
            .DELETE("/{bucket}", request -> S3WebSupport.hasQuery(request, "encryption"), bucketConfig::deleteBucketEncryption)
            .DELETE("/{bucket}", request -> S3WebSupport.hasQuery(request, "logging"), bucketConfig::deleteBucketLogging)
            .DELETE("/{bucket}", bucketOperations::deleteBucket)

            .POST("/{bucket}", request -> S3WebSupport.hasQuery(request, "delete"), objectOperations::deleteObjects)
            .POST("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "uploads"), multipartHandler::initiateMultipartUpload)
            .POST("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "uploadId"), multipartHandler::completeMultipartUpload)
            .GET("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "acl"), objectMetadata::getObjectAcl)
            .GET("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "tagging"), objectMetadata::getObjectTagging)
            .GET("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "attributes"), objectMetadata::getObjectAttributes)
            .GET("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "uploadId"), multipartHandler::listParts)
            .PUT("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "acl"), objectMetadata::putObjectAcl)
            .PUT("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "tagging"), objectMetadata::putObjectTagging)
            .PUT("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "uploadId") && request.headers().firstHeader("x-amz-copy-source") != null, multipartHandler::uploadPartCopy)
            .PUT("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "uploadId"), multipartHandler::uploadPart)
            .PUT("/{bucket}/{key}", request -> request.headers().firstHeader("x-amz-copy-source") != null, objectOperations::copyObject)
            .PUT("/{bucket}/{key}", objectOperations::putObject)
            .GET("/{bucket}/{key}", objectOperations::getObject)
            .HEAD("/{bucket}/{key}", objectOperations::headObject)
            .DELETE("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "tagging"), objectMetadata::deleteObjectTagging)
            .DELETE("/{bucket}/{key}", request -> S3WebSupport.hasQuery(request, "uploadId"), multipartHandler::abortMultipartUpload)
            .DELETE("/{bucket}/{key}", objectOperations::deleteObject)
            .build();
    }
}
