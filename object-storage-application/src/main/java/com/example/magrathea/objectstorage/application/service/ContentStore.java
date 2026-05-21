package com.example.magrathea.objectstorage.application.service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Content store interface — bridges application layer to infrastructure content cache.
 * Implemented by S3ObjectRepositoryImpl in object-storage-infrastructure.
 * Used by ObjectService to store/retrieve raw bytes for S3 objects.
 */
public interface ContentStore {

    void storeContent(String objectId, byte[] data);

    CompletableFuture<Optional<byte[]>> getContent(String objectId);
}
