package com.example.magrathea.objectstore.reactive.repository.application;

/**
 * Thrown when a storage-engine chunk or manifest fails checksum verification while
 * reading object content. Signals that the stored bytes are corrupted and the object
 * cannot be served safely.
 *
 * <p>This exception is the object-store-layer translation of storage-engine integrity
 * exceptions ({@code ChunkIntegrityException}, {@code ManifestIntegrityException}).
 * The S3 HTTP adapter maps it to HTTP 500 with an {@code XAmzChecksumMismatch} error code.
 */
public class StorageObjectIntegrityException extends RuntimeException {

    public StorageObjectIntegrityException(String message) {
        super(message);
    }

    public StorageObjectIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
