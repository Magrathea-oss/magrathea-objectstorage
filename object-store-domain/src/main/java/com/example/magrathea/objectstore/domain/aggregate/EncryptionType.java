package com.example.magrathea.objectstore.domain.aggregate;

/**
 * AWS S3 encryption types for server-side encryption.
 * <p>
 * Pure domain — NO framework dependencies.
 */
public enum EncryptionType {
    NONE,
    SSE_S3,
    SSE_KMS,
    SSE_C
}
