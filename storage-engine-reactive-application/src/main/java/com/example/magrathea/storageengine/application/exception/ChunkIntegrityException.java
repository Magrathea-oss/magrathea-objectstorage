package com.example.magrathea.storageengine.application.exception;

/**
 * Thrown when a stored chunk fails checksum verification on the read path.
 * Indicates that the chunk bytes on disk do not match the stored SHA-256 checksum,
 * which signals storage corruption or bit-rot.
 */
public class ChunkIntegrityException extends RuntimeException {

    public ChunkIntegrityException(String message) {
        super(message);
    }

    public ChunkIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
