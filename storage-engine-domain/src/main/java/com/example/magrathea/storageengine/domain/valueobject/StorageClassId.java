package com.example.magrathea.storageengine.domain.valueobject;

public record StorageClassId(String value) {

    /** Well-known storage class identifier for the STANDARD class. */
    public static final StorageClassId STANDARD = StorageClassId.of("STANDARD");

    /** Well-known storage class identifier for the MINIO_STANDARD class. */
    public static final StorageClassId MINIO_STANDARD = StorageClassId.of("MINIO_STANDARD");
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
