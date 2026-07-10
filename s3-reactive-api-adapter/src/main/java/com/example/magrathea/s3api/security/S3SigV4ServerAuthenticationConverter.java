package com.example.magrathea.s3api.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class S3SigV4ServerAuthenticationConverter implements ServerAuthenticationConverter {

    public static final String CACHED_BODY_ATTRIBUTE = S3SigV4ServerAuthenticationConverter.class.getName() + ".cachedBody";

    private final S3SigV4Verifier verifier;
    private final S3SecurityProperties properties;
    private final S3SecurityAuditSink auditSink;

    public S3SigV4ServerAuthenticationConverter(S3SigV4Verifier verifier) {
        this(verifier, null, null);
    }

    public S3SigV4ServerAuthenticationConverter(S3SigV4Verifier verifier,
                                                S3SecurityProperties properties,
                                                S3SecurityAuditSink auditSink) {
        this.verifier = verifier;
        this.properties = properties;
        this.auditSink = auditSink;
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        S3SigV4Verifier.VerificationResult result = verifier.verify(exchange.getRequest());
        if (!result.allowed()) {
            recordPublicAccessBlockDeny(exchange);
            return Mono.error(new S3AuthenticationException(result.errorCode(), result.message()));
        }

        String declaredPayloadHash = exchange.getRequest().getHeaders().getFirst("X-Amz-Content-SHA256");
        if (requiresPayloadHashVerification(declaredPayloadHash)) {
            return bufferBody(exchange)
                .flatMap(body -> {
                    String actualPayloadHash = sha256Hex(body);
                    if (!MessageDigest.isEqual(
                        actualPayloadHash.getBytes(StandardCharsets.UTF_8),
                        declaredPayloadHash.getBytes(StandardCharsets.UTF_8)
                    )) {
                        return Mono.error(new S3AuthenticationException(
                            "XAmzContentSHA256Mismatch",
                            "The provided X-Amz-Content-SHA256 header does not match the request body"
                        ));
                    }
                    exchange.getAttributes().put(CACHED_BODY_ATTRIBUTE, body);
                    return Mono.just(S3AuthenticationToken.authenticated(result.principal()));
                });
        }

        return Mono.just(S3AuthenticationToken.authenticated(result.principal()));
    }

    private void recordPublicAccessBlockDeny(ServerWebExchange exchange) {
        if (properties == null || auditSink == null) {
            return;
        }
        RequestResource resource = RequestResource.from(exchange);
        S3SecurityProperties.BucketRule rule = properties.bucketRule(resource.bucket());
        if (rule == null || !rule.isBlockPublicAcls() || !rule.getPublicReadKeys().contains(resource.key())) {
            return;
        }
        auditSink.record(new S3SecurityAuditSink.AuditEvent(
            Instant.now(),
            "anonymous",
            resource.action(),
            resource.bucket(),
            resource.key(),
            "deny",
            "public-access-block",
            HttpStatus.FORBIDDEN.value()
        ));
    }

    private static boolean requiresPayloadHashVerification(String declaredPayloadHash) {
        return declaredPayloadHash != null
            && !declaredPayloadHash.isBlank()
            && !"UNSIGNED-PAYLOAD".equals(declaredPayloadHash);
    }

    private static Mono<byte[]> bufferBody(ServerWebExchange exchange) {
        return exchange.getRequest().getBody()
            .collectList()
            .map(buffers -> {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                for (DataBuffer buffer : buffers) {
                    byte[] chunk = new byte[buffer.readableByteCount()];
                    buffer.read(chunk);
                    output.writeBytes(chunk);
                    DataBufferUtils.release(buffer);
                }
                return output.toByteArray();
            });
    }

    private record RequestResource(String action, String bucket, String key) {
        static RequestResource from(ServerWebExchange exchange) {
            String[] path = Arrays.stream(exchange.getRequest().getURI().getRawPath().split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
            String bucket = path.length > 0 ? path[0] : "";
            String key = path.length > 1 ? String.join("/", Arrays.copyOfRange(path, 1, path.length)) : "";
            String method = exchange.getRequest().getMethod().name();
            String action = "GET".equals(method) && !key.isBlank() ? "s3:GetObject" : method + " " + exchange.getRequest().getURI().getRawPath();
            return new RequestResource(action, bucket, key);
        }
    }

    private static String sha256Hex(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
