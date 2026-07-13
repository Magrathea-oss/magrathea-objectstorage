package com.example.magrathea.storageengine.cluster.application;

/** A consensus-owned bucket namespace generation. */
public record BucketNamespace(String bucket, long generation) {
    public BucketNamespace {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        if (generation < 1) throw new IllegalArgumentException("generation must be positive");
    }
}
