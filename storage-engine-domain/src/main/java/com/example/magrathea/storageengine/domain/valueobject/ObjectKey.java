package com.example.magrathea.storageengine.domain.valueobject;

public record ObjectKey(String bucket, String key) {
    public ObjectKey {
        java.util.Objects.requireNonNull(bucket, "bucket must not be null");
        java.util.Objects.requireNonNull(key, "key must not be null");
        if (bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }

    public static ObjectKey of(String bucket, String key) {
        return new ObjectKey(bucket, key);
    }
}
