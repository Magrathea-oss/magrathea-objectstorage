package com.example.magrathea.reactive.infrastructure.adapter.persistence;

public class BucketAlreadyExistsException extends RuntimeException {
    private final String bucketName;

    public BucketAlreadyExistsException(String bucketName) {
        super("Bucket already exists: " + bucketName);
        this.bucketName = bucketName;
    }

    public String bucketName() { return bucketName; }
}
