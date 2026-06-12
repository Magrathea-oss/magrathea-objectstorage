package com.example.magrathea.objectstore.reactive.repository.application;

/**
 * Thrown when a required bucket is not found in the repository.
 */
public class BucketNotFoundException extends RuntimeException {
    private final String bucketName;

    public BucketNotFoundException(String bucketName) {
        super("Bucket not found: " + bucketName);
        this.bucketName = bucketName;
    }

    public String bucketName() { return bucketName; }
}
