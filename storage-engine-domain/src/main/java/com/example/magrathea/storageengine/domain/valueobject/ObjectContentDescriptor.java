package com.example.magrathea.storageengine.domain.valueobject;

public record ObjectContentDescriptor(String mimeType, long objectSize) {
    public ObjectContentDescriptor {
        java.util.Objects.requireNonNull(mimeType, "mimeType must not be null");
        if (mimeType.isBlank()) {
            throw new IllegalArgumentException("mimeType must not be blank");
        }
        if (objectSize < 0) {
            throw new IllegalArgumentException("objectSize must be >= 0: " + objectSize);
        }
    }

    public static ObjectContentDescriptor of(String mimeType, long objectSize) {
        return new ObjectContentDescriptor(mimeType, objectSize);
    }
}
