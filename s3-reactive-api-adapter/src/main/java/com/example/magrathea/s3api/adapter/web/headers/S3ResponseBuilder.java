package com.example.magrathea.s3api.adapter.web.headers;

import com.example.magrathea.objectstore.domain.aggregate.S3Object;
import com.example.magrathea.objectstore.domain.valueobject.ChecksumAlgorithm;
import com.example.magrathea.objectstore.domain.valueobject.EncryptionConfiguration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        builder.header("ETag", obj.hasEtag() ? obj.etag() : "\"\"");
    }

    private static void applyEncryptionHeaders(ServerResponse.HeadersBuilder<?> builder,
                                                EncryptionConfiguration encryption) {
        if (encryption == null) return;
        builder.header(S3Header.X_AMZ_SERVER_SIDE_ENCRYPTION.headerName(), encryption.algorithm().name());
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
    }

    private static void applyChecksumHeaders(ServerResponse.HeadersBuilder<?> builder, S3Object obj) {
        var descriptor = obj.contentDescriptor();
        if (descriptor == null) return;
        var checksums = descriptor.checksums();
        if (checksums != null && !checksums.isEmpty()) {
            for (var cv : checksums) {
                if (cv.algorithm() == ChecksumAlgorithm.MD5) {
                    builder.header(S3Header.CONTENT_MD5.headerName(), cv.base64Value());
                } else {
                    builder.header("x-amz-checksum-" + cv.algorithm().apiName(), cv.base64Value());
                }
            }
        }
        var sdkAlgo = descriptor.sdkChecksumAlgorithm();
        if (sdkAlgo != null && !sdkAlgo.isBlank()) {
            builder.header(S3Header.X_AMZ_SDK_CHECKSUM_ALGORITHM.headerName(), sdkAlgo);
        }
    }

    // ─────────────────────────────────────────────────────
    //  Public response builders
    // ─────────────────────────────────────────────────────

    /**
     * 200 OK with all S3 object headers (ETag, encryption, checksums).
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
