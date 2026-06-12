package com.example.magrathea.objectstore.reactive.repository.application;

/**
 * Thrown when an attempt is made to create a bucket that already exists.
 */
public class BucketAlreadyExistsException extends RuntimeException {
    private final String bucketName;

    public BucketAlreadyExistsException(String bucketName) {
        super("Bucket already exists: " + bucketName);
        this.bucketName = bucketName;
    }

    public String bucketName() { return bucketName; }
}
