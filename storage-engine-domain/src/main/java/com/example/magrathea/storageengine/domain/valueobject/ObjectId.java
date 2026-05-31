package com.example.magrathea.storageengine.domain.valueobject;

public record ObjectId(String value) {
    public ObjectId {
        java.util.Objects.requireNonNull(value, "ObjectId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ObjectId value must not be blank");
        }
    }

    public static ObjectId of(String value) {
        return new ObjectId(value);
    }
}
