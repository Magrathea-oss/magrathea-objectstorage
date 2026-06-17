package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Map;

public record ObjectMetadataDescriptor(Map<String, String> entries) {
    public ObjectMetadataDescriptor {
        java.util.Objects.requireNonNull(entries, "entries must not be null");
        entries = Map.copyOf(entries);
    }

    public static ObjectMetadataDescriptor of(Map<String, String> entries) {
        return new ObjectMetadataDescriptor(entries);
    }

    public static ObjectMetadataDescriptor empty() {
        return new ObjectMetadataDescriptor(Map.of());
    }
}
