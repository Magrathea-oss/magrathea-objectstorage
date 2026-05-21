package com.example.magrathea.objectstorage.application.dto;

/**
 * Output response for S3 object operations — Java 17+ record.
 */
public record ObjectResponse(
    String id,
    String bucketId,
    String key,
    long size,
    String contentType,
    String etag,
    String storageClass
) {}
