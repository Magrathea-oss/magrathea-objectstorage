package com.example.magrathea.reactive.infrastructure.adapter.persistence;

/**
 * @deprecated Use {@link com.example.magrathea.objectstore.reactive.repository.application.BucketNotFoundException} instead.
 * This class will be removed when object-store-reactive-application no longer depends on object-store-reactive-infrastructure.
 */
@Deprecated
public class BucketNotFoundException extends RuntimeException {
    private final String bucketName;

    public BucketNotFoundException(String bucketName) {
        super("Bucket not found: " + bucketName);
        this.bucketName = bucketName;
    }

    public String bucketName() { return bucketName; }
}
