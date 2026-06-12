package com.example.magrathea.s3api.adapter.web.headers;

import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionContext;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionKeyReference;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.UserMetadata;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Static extractor methods that parse a {@link ServerRequest} into domain/primitives.
 * <p>
 * Each method focuses on one extraction concern — headers, path variables, query params —
 * and returns typed domain objects or primitives that handlers can delegate to the
 * application service layer.
 * </p>
 * <p>
 * NO handler should duplicate these extraction patterns. All handlers must use these
 * methods to ensure consistent extraction and eliminate code duplication.
 * </p>
 * <p>
 * All header references use the {@link S3Header} enum as the single source of truth.
 * </p>
 */
public final class S3RequestExtractor {

    private S3RequestExtractor() {
    }

    // ─────────────────────────────────────────────────────
    //  Object Key (bucket + key)
    // ─────────────────────────────────────────────────────

    /**
     * Extracts the S3 object key from the request path variables.
     * <p>
     * Returns an {@link ObjectKey} containing both the bucket name and the object key.
     * The bucket is taken from the {@code {bucket}} path variable, the key from the
     * {@code {key}} path variable.
     * </p>
     *
     * @param request the HTTP request with {@code {bucket}} and {@code {key}} path variables
     * @return an {@code ObjectKey} combining bucket and key
     * @throws IllegalArgumentException if bucket or key is missing or blank
     */
    public static ObjectKey extractObjectKey(ServerRequest request) {
        var bucket = request.pathVariable("bucket");
        var key = extractObjectKeyValue(request);
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Bucket name must not be empty");
        }
        return ObjectKey.of(bucket, key);
    }

    /**
     * Extracts the raw object key value from the {@code {key}} path variable.
     * <p>
     * Catch-all object routes use {@code {*key}} so Spring may include the
     * separator slash in the captured value. S3 object keys are stored without
     * that route separator, while keys containing slashes are preserved.
     * </p>
     *
     * @param request the HTTP request with a {@code {key}} path variable
     * @return normalized object key value without the route separator slash
     * @throws IllegalArgumentException if key is missing or blank
     */
    public static String extractObjectKeyValue(ServerRequest request) {
        var key = request.pathVariable("key");
        if (key != null && key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Object key must not be empty");
        }
        return key;
    }

    // ─────────────────────────────────────────────────────
    //  Storage Class
    // ─────────────────────────────────────────────────────

    /**
     * Extracts the storage class from the {@code x-amz-storage-class} header.
     *
     * @param request the HTTP request
     * @return the storage class value, or {@code null} if not present
     */
    public static String extractStorageClass(ServerRequest request) {
        return request.headers().firstHeader(S3Header.X_AMZ_STORAGE_CLASS.headerName());
    }

    // ─────────────────────────────────────────────────────
    //  Checksums
    // ─────────────────────────────────────────────────────

    /**
     * Extracts checksum headers ({@code Content-MD5} and {@code x-amz-checksum-*})
     * into an {@link ObjectChecksum}.
     *
     * @param request the HTTP request
     * @return an {@code ObjectChecksum}, may be empty
     */
    public static ObjectChecksum extractChecksum(ServerRequest request) {
        Set<ChecksumValue> checksums = new HashSet<>();

        var contentMd5 = request.headers().firstHeader(S3Header.CONTENT_MD5.headerName());
        if (contentMd5 != null && !contentMd5.isBlank()) {
            checksums.add(new ChecksumValue(ChecksumAlgorithm.MD5, contentMd5));
        }

        for (var algorithm : ChecksumAlgorithm.values()) {
            if (algorithm == ChecksumAlgorithm.MD5) continue;
            var headerName = "x-amz-checksum-" + algorithm.apiName();
            var hash = request.headers().firstHeader(headerName);
            if (hash != null && !hash.isBlank()) {
                checksums.add(new ChecksumValue(algorithm, hash));
            }
        }

        var sdkAlgorithm = request.headers().firstHeader(S3Header.X_AMZ_SDK_CHECKSUM_ALGORITHM.headerName());
        return ObjectChecksum.of(checksums, sdkAlgorithm);
    }

    // ─────────────────────────────────────────────────────
    //  Encryption
    // ─────────────────────────────────────────────────────

    /**
     * Extracts SSE encryption headers into an {@link EncryptionConfiguration}.
     * <p>
     * Supports SSE-S3 (AES256), SSE-KMS (aws:kms), and SSE-C.
     * Returns {@code null} if no SSE headers are present.
     * </p>
     *
     * @param request the HTTP request
     * @return an {@code EncryptionConfiguration} or {@code null}
     */
    public static EncryptionConfiguration extractEncryption(ServerRequest request) {
        var sseEncryption = request.headers().firstHeader(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION.headerName());
        var sseKmsKeyId = request.headers().firstHeader(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID.headerName());
        var ssekmsKeyId = request.headers().firstHeader(S3Header.X_AMZ_SSE_KMS_KEY_ID.headerName());
        var sseCustomerAlgorithm = request.headers().firstHeader(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_ALGORITHM.headerName());
        var sseCustomerKeyMd5 = request.headers().firstHeader(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5.headerName());
        var sseContext = request.headers().firstHeader(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION_CONTEXT.headerName());
        var sseCustomerKeySha256 = request.headers().firstHeader(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_SHA256.headerName());

        if (sseEncryption == null && sseKmsKeyId == null && ssekmsKeyId == null
            && sseCustomerAlgorithm == null && sseCustomerKeyMd5 == null
            && sseContext == null && sseCustomerKeySha256 == null) {
            return null;
        }

        var algorithm = EncryptionAlgorithm.AES256;
        if (sseEncryption != null) {
            if ("aws:kms".equals(sseEncryption) || "AWS_KMS".equals(sseEncryption)) {
                algorithm = EncryptionAlgorithm.AWS_KMS;
            } else if ("AES256".equals(sseEncryption)) {
                algorithm = EncryptionAlgorithm.AES256;
            }
        } else if (sseCustomerAlgorithm != null) {
            algorithm = EncryptionAlgorithm.SSE_C;
        }
        var keyRef = sseKmsKeyId != null
            ? EncryptionKeyReference.of(sseKmsKeyId)
            : (ssekmsKeyId != null ? EncryptionKeyReference.of(ssekmsKeyId) : null);
        var context = sseContext != null
            ? EncryptionContext.of(Map.of("context", sseContext))
            : null;

        return EncryptionConfiguration.of(algorithm, keyRef, context);
    }

    // ─────────────────────────────────────────────────────
    //  Content Type
    // ─────────────────────────────────────────────────────

    /**
     * Extracts the Content-Type header as a string.
     *
     * @param request the HTTP request
     * @return the content type string, or {@code null} if not present
     */
    public static String extractContentType(ServerRequest request) {
        return request.headers().contentType()
            .map(MediaType::toString)
            .orElse(null);
    }

    // ─────────────────────────────────────────────────────
    //  Content Length
    // ─────────────────────────────────────────────────────

    /**
     * Extracts the Content-Length header as a long.
     * Reads the header directly to preserve negative values (e.g., for validation).
     *
     * @param request the HTTP request
     * @return the content length header value, or {@code 0} if not present
     */
    public static long extractContentLength(ServerRequest request) {
        var headerValue = request.headers().firstHeader(S3Header.CONTENT_LENGTH.headerName());
        if (headerValue == null || headerValue.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(headerValue);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ─────────────────────────────────────────────────────
    //  User Metadata
    // ─────────────────────────────────────────────────────

    /**
     * Extracts user metadata headers ({@code x-amz-meta-*}) into a {@link UserMetadata}.
     *
     * @param request the HTTP request
     * @return a {@code UserMetadata}, may be empty
     */
    public static UserMetadata extractUserMetadata(ServerRequest request) {
        java.util.Map<String, String> entries = new java.util.HashMap<>();
        request.headers().asHttpHeaders().forEach((name, values) -> {
            if (S3Header.isUserMetadata(name) && !values.isEmpty()) {
                entries.put(name, values.get(0));
            }
        });
        return UserMetadata.of(entries);
    }
}
