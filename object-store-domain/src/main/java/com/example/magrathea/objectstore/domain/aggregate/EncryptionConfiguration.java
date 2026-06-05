package com.example.magrathea.objectstore.domain.aggregate;

import java.util.Objects;
import java.util.Optional;

/**
 * EncryptionConfiguration — value record representing encryption configuration
 * for an S3 object at the aggregate level.
 * <p>
 * Encapsulates:
 * <ul>
 *   <li>{@code type} — the encryption type (NONE, SSE_S3, SSE_KMS, SSE_C)</li>
 *   <li>{@code kmsKeyId} — KMS key ID (for SSE_KMS)</li>
 *   <li>{@code kmsEncryptionContext} — KMS encryption context (for SSE_KMS)</li>
 *   <li>{@code algorithm} — encryption algorithm override (optional)</li>
 * </ul>
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record EncryptionConfiguration(
    EncryptionType type,
    Optional<String> kmsKeyId,
    Optional<String> kmsEncryptionContext,
    Optional<String> algorithm
) {
    public EncryptionConfiguration {
        Objects.requireNonNull(type, "type must not be null");
        kmsKeyId = Objects.requireNonNull(kmsKeyId, "kmsKeyId must not be null");
        kmsEncryptionContext = Objects.requireNonNull(kmsEncryptionContext, "kmsEncryptionContext must not be null");
        algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
    }

    /**
     * Factory method — create with no encryption (NONE).
     */
    public static EncryptionConfiguration none() {
        return new EncryptionConfiguration(EncryptionType.NONE,
            Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Factory method — create with SSE-S3 encryption.
     */
    public static EncryptionConfiguration sseS3() {
        return new EncryptionConfiguration(EncryptionType.SSE_S3,
            Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Factory method — create with SSE-KMS encryption.
     *
     * @param kmsKeyId              the KMS key ID
     * @param kmsEncryptionContext  optional encryption context
     */
    public static EncryptionConfiguration sseKms(String kmsKeyId, String kmsEncryptionContext) {
        Objects.requireNonNull(kmsKeyId, "kmsKeyId must not be null");
        return new EncryptionConfiguration(EncryptionType.SSE_KMS,
            Optional.of(kmsKeyId),
            Optional.ofNullable(kmsEncryptionContext),
            Optional.empty());
    }

    /**
     * Factory method — create with SSE-C encryption.
     *
     * @param algorithm the encryption algorithm
     */
    public static EncryptionConfiguration sseC(String algorithm) {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        return new EncryptionConfiguration(EncryptionType.SSE_C,
            Optional.empty(), Optional.empty(), Optional.of(algorithm));
    }
}
