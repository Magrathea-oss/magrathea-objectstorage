package com.example.magrathea.objectstorage.domain.aggregate;

/**
 * Domain-level marker for persisted S3 object content.
 * Implementations may carry framework-specific streams outside the domain.
 */
public interface S3ObjectContent {
    S3Object.Id objectId();
}
