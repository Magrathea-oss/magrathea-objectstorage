package com.example.magrathea.storageengine.application.service;

import com.example.magrathea.storageengine.domain.valueobject.ChunkId;

/**
 * A materialized chunk payload: chunkId + bounded byte array.
 * Package-private byte[] — only used internally by the storage pipeline.
 */
public class ApplicationChunkPayload {

    private final ChunkId chunkId;
    private final byte[] data;

    public ApplicationChunkPayload(ChunkId chunkId, byte[] data) {
        this.chunkId = java.util.Objects.requireNonNull(chunkId, "chunkId must not be null");
        this.data = java.util.Objects.requireNonNull(data, "data must not be null");
    }

    public ChunkId chunkId() {
        return chunkId;
    }

    public byte[] data() {
        return data;
    }

    public int size() {
        return data.length;
    }
}
