package com.example.magrathea.storageengine.application.exception;

import java.nio.file.Path;

/** Typed filesystem-capacity failure safe to map to S3 InsufficientStorage. */
public final class StorageCapacityException extends RuntimeException {
    private final String backend;
    private final Path storageRoot;
    private final long requestedBytes;
    private final long availableBytes;

    public StorageCapacityException(
            String backend,
            Path storageRoot,
            long requestedBytes,
            long availableBytes) {
        super("Storage capacity exhausted: backend=" + backend
                + " storageRoot=" + storageRoot
                + " requestedBytes=" + requestedBytes
                + " availableBytes=" + availableBytes);
        this.backend = backend;
        this.storageRoot = storageRoot;
        this.requestedBytes = requestedBytes;
        this.availableBytes = availableBytes;
    }

    public String backend() { return backend; }
    public Path storageRoot() { return storageRoot; }
    public long requestedBytes() { return requestedBytes; }
    public long availableBytes() { return availableBytes; }
}
