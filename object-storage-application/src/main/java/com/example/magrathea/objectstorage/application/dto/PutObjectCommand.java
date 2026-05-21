package com.example.magrathea.objectstorage.application.dto;

import java.util.Map;

/**
 * Input command for putting an object into a bucket — Java 17+ record.
 * Uses AWS S3 native fields: contentType, contentDisposition, contentEncoding, metadata.
 */
public record PutObjectCommand(
    String bucketId,
    String key,
    String contentType,
    String contentDisposition,
    String contentEncoding,
    long size,
    Map<String, String> metadata
) {}
