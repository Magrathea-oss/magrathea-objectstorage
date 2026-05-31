package com.example.magrathea.storageengine.domain.valueobject;

public record VersionId(String value) {
    public VersionId {
        java.util.Objects.requireNonNull(value, "VersionId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("VersionId value must not be blank");
        }
    }

    public static VersionId of(String value) {
        return new VersionId(value);
    }
}
