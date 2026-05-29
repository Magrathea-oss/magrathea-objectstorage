package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 Object Key — composite natural identifier consisting of bucket name and object key.
 * Java 17+ record — immutable, transparent.
 */
public record ObjectKey(
    String bucket,
    String key
) {

    public ObjectKey {
        Objects.requireNonNull(bucket, "bucket must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (bucket.isBlank()) {
            throw new IllegalArgumentException("Bucket name must not be blank");
        }
        if (key.isBlank()) {
            throw new IllegalArgumentException("Object key must not be blank");
        }
        if (key.length() > 1024) {
            throw new IllegalArgumentException("Object key exceeds 1024 characters");
        }
    }

    public static ObjectKey of(String bucket, String key) {
        return new ObjectKey(bucket, key);
    }

    @Override
    public String toString() {
        return bucket + "/" + key;
    }
}
