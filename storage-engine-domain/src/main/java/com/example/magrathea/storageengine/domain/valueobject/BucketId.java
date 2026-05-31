package com.example.magrathea.storageengine.domain.valueobject;

public record BucketId(String value) {
    public BucketId {
        java.util.Objects.requireNonNull(value, "BucketId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("BucketId value must not be blank");
        }
    }

    public static BucketId of(String value) {
        return new BucketId(value);
    }
}
