package com.example.magrathea.storageengine.domain.valueobject;

import java.util.UUID;

public record ManifestId(UUID value) {
    public ManifestId {
        java.util.Objects.requireNonNull(value, "ManifestId value must not be null");
    }

    public static ManifestId generate() {
        return new ManifestId(UUID.randomUUID());
    }

    public static ManifestId of(UUID value) {
        return new ManifestId(value);
    }
}
