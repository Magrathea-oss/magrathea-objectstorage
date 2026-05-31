package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Map;
import java.util.Collections;

public record ObjectMetadataDescriptor(Map<String, String> entries) {
    public ObjectMetadataDescriptor {
        java.util.Objects.requireNonNull(entries, "entries must not be null");
    }

    // Accessor returning an unmodifiable view
    public static ObjectMetadataDescriptor of(Map<String, String> entries) {
        return new ObjectMetadataDescriptor(entries);
    }

    public static ObjectMetadataDescriptor empty() {
        return new ObjectMetadataDescriptor(Collections.emptyMap());
    }

    @Override
    public Map<String, String> entries() {
        return Collections.unmodifiableMap(entries);
    }
}
