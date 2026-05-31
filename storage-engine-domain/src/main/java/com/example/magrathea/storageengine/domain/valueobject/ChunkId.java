package com.example.magrathea.storageengine.domain.valueobject;

import java.util.UUID;

public record ChunkId(UUID value) {
    public ChunkId {
        java.util.Objects.requireNonNull(value, "ChunkId value must not be null");
    }

    public static ChunkId generate() {
        return new ChunkId(UUID.randomUUID());
    }

    public static ChunkId of(UUID value) {
        return new ChunkId(value);
    }
}
