package com.example.magrathea.storageengine.domain.valueobject;

public record StorageClassId(String value) {
    public StorageClassId {
        java.util.Objects.requireNonNull(value, "StorageClassId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("StorageClassId value must not be blank");
        }
    }

    public static StorageClassId of(String value) {
        return new StorageClassId(value);
    }
}
