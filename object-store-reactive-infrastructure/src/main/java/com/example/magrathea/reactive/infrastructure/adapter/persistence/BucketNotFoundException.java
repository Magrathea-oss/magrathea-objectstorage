package com.example.magrathea.reactive.infrastructure.adapter.persistence;

public class BucketNotFoundException extends RuntimeException {
    private final String bucketName;

    public BucketNotFoundException(String bucketName) {
        super("Bucket not found: " + bucketName);
        this.bucketName = bucketName;
    }

    public String bucketName() { return bucketName; }
}
