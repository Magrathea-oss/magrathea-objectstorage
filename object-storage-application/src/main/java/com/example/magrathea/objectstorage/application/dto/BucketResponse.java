package com.example.magrathea.objectstorage.application.dto;

/**
 * Output response for bucket operations — Java 17+ record.
 */
public record BucketResponse(
    String id,
    String name,
    String region,
    String storageClass,
    boolean versioningEnabled,
    boolean encryptionEnabled
) {}
