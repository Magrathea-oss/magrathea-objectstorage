package com.example.magrathea.storageengine.domain.valueobject;

public record BucketRef(BucketId bucketId, String bucketName) {
    public BucketRef {
        java.util.Objects.requireNonNull(bucketId, "bucketId must not be null");
        java.util.Objects.requireNonNull(bucketName, "bucketName must not be null");
        if (bucketName.isBlank()) {
            throw new IllegalArgumentException("bucketName must not be blank");
        }
    }

    public static BucketRef of(BucketId bucketId, String bucketName) {
        return new BucketRef(bucketId, bucketName);
    }
}
