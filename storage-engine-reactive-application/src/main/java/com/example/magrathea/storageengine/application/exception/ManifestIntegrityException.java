package com.example.magrathea.storageengine.application.exception;

/**
 * Thrown when a stored manifest fails checksum verification on the read path.
 * Indicates that the manifest content on disk does not match the stored SHA-256 checksum,
 * which signals storage corruption or a partial/incomplete write.
 */
public class ManifestIntegrityException extends RuntimeException {

    public ManifestIntegrityException(String message) {
        super(message);
    }

    public ManifestIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
