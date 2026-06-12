package com.example.magrathea.reactive.infrastructure.adapter.persistence;

/**
 * @deprecated Use {@link com.example.magrathea.objectstore.reactive.repository.application.BucketAlreadyExistsException} instead.
 * This class will be removed when object-store-reactive-application no longer depends on object-store-reactive-infrastructure.
 */
@Deprecated
public class BucketAlreadyExistsException extends RuntimeException {
    private final String bucketName;

    public BucketAlreadyExistsException(String bucketName) {
        super("Bucket already exists: " + bucketName);
        this.bucketName = bucketName;
    }

    public String bucketName() { return bucketName; }
}
