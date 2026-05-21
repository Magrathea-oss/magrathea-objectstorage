package com.example.magrathea.objectstorage.domain.valueobject;

import java.util.Objects;

/**
 * AWS S3 Object Key — the unique identifier of an object within a bucket.
 * Java 17+ record — immutable, transparent.
 */
public record ObjectKey(String value) {

    public ObjectKey {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Object key must not be blank");
        }
        if (value.length() > 1024) {
            throw new IllegalArgumentException("Object key exceeds 1024 characters");
        }
    }

    public static ObjectKey of(String value) { return new ObjectKey(value); }
}
