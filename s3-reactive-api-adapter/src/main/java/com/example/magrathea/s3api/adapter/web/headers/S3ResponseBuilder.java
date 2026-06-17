package com.example.magrathea.s3api.adapter.web.headers;

import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumValue;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import com.example.magrathea.objectstore.domain.valueobject.ObjectChecksum;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

/**
 * Builder for S3 HTTP responses.
 * <p>
 * All methods return {@link Mono Mono<ServerResponse>} directly.
 * Headers are applied to the builder BEFORE the body is attached, ensuring
 * the body {@link Flux Flux<DataBuffer>} is never consumed for header
 * computation.
 * All header names use the {@link S3Header} enum.
 * </p>
 */
public final class S3ResponseBuilder {

    private S3ResponseBuilder() {
    }

    // ─────────────────────────────────────────────────────
    //  Internal helpers (builder-level)
    // ─────────────────────────────────────────────────────

    private static void applyEtag(ServerResponse.HeadersBuilder<?> builder, S3Object obj) {
        // Prefer the pre-computed hex MD5 ETag stored on the aggregate;
        // fall back to checksum-based computation for backward compat (e.g. Content-MD5 echo).
        var etag = obj.etag() != null ? obj.etag() : computeEtag(obj);
        builder.header("ETag", etag);
    }

    /**
     * Computes the ETag from the object's checksum.
     * Uses the MD5 checksum value (base64-encoded) and wraps it in quotes.
     * If no MD5 checksum exists, falls back to a quoted empty string.
     */
    private static String computeEtag(S3Object obj) {
        var checksum = obj.checksum();
        if (checksum != null) {
            var checksums = checksum.checksums();
            if (checksums != null && !checksums.isEmpty()) {
                for (var cv : checksums) {
                    if (cv.algorithm() == ChecksumAlgorithm.MD5) {
                        return "\"" + cv.base64Value() + "\"";
                    }
                }
            }
        }
        return "\"\"";
    }

    /**
     * Computes the ETag from a raw byte content (MD5 digest → base64 → quoted).
     */
    public static String computeEtagFromBytes(byte[] content) {
        try {
            var md = java.security.MessageDigest.getInstance("MD5");
            var digest = md.digest(content);
            var base64 = Base64.getEncoder().encodeToString(digest);
            return "\"" + base64 + "\"";
        } catch (java.security.NoSuchAlgorithmException e) {
            return "\"\"";
        }
    }

    /**
     * Computes the ETag from an ObjectChecksum.
     * Uses the MD5 checksum value (base64-encoded) and wraps it in quotes.
     */
    public static String computeEtagFromChecksum(ObjectChecksum checksum) {
        if (checksum == null) return "\"\"";
        var checksums = checksum.checksums();
        if (checksums != null && !checksums.isEmpty()) {
            for (var cv : checksums) {
                if (cv.algorithm() == ChecksumAlgorithm.MD5) {
                    return "\"" + cv.base64Value() + "\"";
                }
            }
        }
        return "\"\"";
    }

    /**
     * Applies all S3 object headers to a builder (ETag, encryption, checksums, user metadata).
     * Used by handlers that need full header set without predefined response methods.
     */
    public static void applyHeaders(ServerResponse.HeadersBuilder<?> builder, S3Object obj) {
        applyEtag(builder, obj);
        var enc = obj.encryption();
        if (enc != null) {
            applyEncryptionHeaders(builder, enc);
        }
        applyChecksumHeaders(builder, obj);
        applyUserMetadataHeaders(builder, obj);
    }

    private static void applyEncryptionHeaders(ServerResponse.HeadersBuilder<?> builder,
                                                EncryptionConfiguration encryption) {
        if (encryption == null) return;
        var algo = encryption.algorithm();
        // SSE header value: "AES256" for SSE-S3, "aws:kms" for SSE-KMS, "AES256" for SSE-C
        var sseValue = switch (algo) {
            case AWS_KMS -> "aws:kms";
            case SSE_C -> "AES256";
            default -> "AES256";
        };
        builder.header(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION.headerName(), sseValue);
        var keyRef = encryption.keyReference();
        if (keyRef != null) {
            builder.header(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION_AWS_KMS_KEY_ID.headerName(), keyRef.keyId());
        }
        var context = encryption.encryptionContext();
        if (context != null && context.context() != null && !context.context().isEmpty()) {
            var ctxValue = context.context().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
            builder.header(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION_CONTEXT.headerName(), ctxValue);
        }
        // SSE-C specific headers
        if (algo == com.example.magrathea.objectstore.domain.valueobject.EncryptionAlgorithm.SSE_C) {
            builder.header(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_ALGORITHM.headerName(), "AES256");
            // Echo back SSE-C key headers if present in the request (stored in encryption context)
            if (context != null && context.context() != null) {
                var customerKeyMd5 = context.context().get("x-amz-server-side-encryption-customer-key-MD5");
                if (customerKeyMd5 != null && !customerKeyMd5.isBlank()) {
                    builder.header(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_MD5.headerName(), customerKeyMd5);
                }
                var customerKeySha256 = context.context().get("x-amz-server-side-encryption-customer-key-sha256");
                if (customerKeySha256 != null && !customerKeySha256.isBlank()) {
                    builder.header(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION_CUSTOMER_KEY_SHA256.headerName(), customerKeySha256);
                }
            }
        }
    }

    private static void applyChecksumHeaders(ServerResponse.HeadersBuilder<?> builder, S3Object obj) {
        var checksum = obj.checksum();
        if (checksum == null) return;
        var checksums = checksum.checksums();
        if (checksums != null && !checksums.isEmpty()) {
            for (var cv : checksums) {
                if (cv.algorithm() == ChecksumAlgorithm.MD5) {
                    builder.header(S3Header.CONTENT_MD5.headerName(), cv.base64Value());
                } else {
                    builder.header("x-amz-checksum-" + cv.algorithm().apiName(), cv.base64Value());
                }
            }
        }
        var sdkAlgo = checksum.sdkAlgorithm();
        if (sdkAlgo != null && !sdkAlgo.isBlank()) {
            builder.header(S3Header.X_AMZ_SDK_CHECKSUM_ALGORITHM.headerName(), sdkAlgo);
        }
    }

    private static void applyUserMetadataHeaders(ServerResponse.HeadersBuilder<?> builder, S3Object obj) {
        var userMetadata = obj.userMetadata();
        if (userMetadata != null && !userMetadata.isEmpty()) {
            for (var entry : userMetadata.entrySet()) {
                var key = entry.getKey();
                var value = entry.getValue();
                if (key != null && value != null) {
                    builder.header(key, value);
                }
            }
        }
    }

    private static void applyContentType(ServerResponse.HeadersBuilder<?> builder, S3Object obj, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            builder.header("Content-Type", contentType);
        } else {
            builder.header("Content-Type", "application/octet-stream");
        }
    }

    // ─────────────────────────────────────────────────────
    //  Public response builders
    // ─────────────────────────────────────────────────────

    /**
     * 200 OK with all S3 object headers (ETag, encryption, checksums, user metadata).
     * No body.
     */
    public static Mono<ServerResponse> ok(S3Object obj) {
        var builder = ServerResponse.ok();
        applyEtag(builder, obj);
        var enc = obj.encryption();
        if (enc != null) {
            applyEncryptionHeaders(builder, enc);
        }
        applyChecksumHeaders(builder, obj);
        applyUserMetadataHeaders(builder, obj);
        return builder.build();
    }

    /**
     * 200 OK with all S3 object headers and a streaming body.
     * Headers are applied to the builder BEFORE the body flux is attached —
     * the body flux is NEVER consumed for header computation.
     */
    public static Mono<ServerResponse> okWithBody(S3Object obj, Flux<DataBuffer> body) {
        var builder = ServerResponse.ok();
        applyEtag(builder, obj);
        var enc = obj.encryption();
        if (enc != null) {
            applyEncryptionHeaders(builder, enc);
        }
        applyChecksumHeaders(builder, obj);
        applyUserMetadataHeaders(builder, obj);
        return builder.body(BodyInserters.fromDataBuffers(body));
    }

    /**
     * 200 OK with all S3 object headers, Content-Type, and a streaming body.
     */
    public static Mono<ServerResponse> okWithBodyAndContentType(S3Object obj, Flux<DataBuffer> body, String contentType) {
        var builder = ServerResponse.ok();
        applyEtag(builder, obj);
        var enc = obj.encryption();
        if (enc != null) {
            applyEncryptionHeaders(builder, enc);
        }
        applyChecksumHeaders(builder, obj);
        applyUserMetadataHeaders(builder, obj);
        applyContentType(builder, obj, contentType);
        return builder.body(BodyInserters.fromDataBuffers(body));
    }

    /**
     * 200 OK with XML content-type, ETag header, and an XML body value.
     */
    public static Mono<ServerResponse> okXml(String etag, Object bodyValue) {
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_XML)
            .header("ETag", etag)
            .bodyValue(bodyValue);
    }

    /**
     * 200 OK with plain ETag header and no body.
     */
    public static Mono<ServerResponse> okWithEtag(String etag) {
        return ServerResponse.ok().header("ETag", etag).build();
    }
}
