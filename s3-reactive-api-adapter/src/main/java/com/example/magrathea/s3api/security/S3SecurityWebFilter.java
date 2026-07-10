package com.example.magrathea.s3api.security;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public final class S3SecurityWebFilter implements WebFilter, Ordered {

    private final S3SecurityProperties properties;
    private final S3SigV4Verifier verifier;
    private final S3SecurityAuthorizer authorizer;
    private final S3SecurityAuditSink auditSink;

    public S3SecurityWebFilter(S3SecurityProperties properties, S3SigV4Verifier verifier) {
        this(properties, verifier, new S3SecurityAuthorizer(properties), new InMemoryS3SecurityAuditSink());
    }

    public S3SecurityWebFilter(S3SecurityProperties properties, S3SigV4Verifier verifier,
                               S3SecurityAuthorizer authorizer, S3SecurityAuditSink auditSink) {
        this.properties = properties;
        this.verifier = verifier;
        this.authorizer = authorizer;
        this.auditSink = auditSink;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        var result = verifier.verify(exchange.getRequest());
        if (!result.allowed()) {
            return xmlError(exchange, HttpStatus.FORBIDDEN, result.errorCode(), result.message());
        }

        var authorization = authorizer.authorize(result.principal(), exchange.getRequest());
        if (!authorization.allowed()) {
            auditSink.record(new S3SecurityAuditSink.AuditEvent(
                Instant.now(),
                result.principal(),
                authorization.action(),
                authorization.bucket(),
                authorization.key(),
                "deny",
                authorization.reason(),
                HttpStatus.FORBIDDEN.value()
            ));
            return xmlError(exchange, HttpStatus.FORBIDDEN, "AccessDenied", "Access denied by S3 security policy");
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
                        return xmlError(
                            exchange,
                            HttpStatus.FORBIDDEN,
                            "XAmzContentSHA256Mismatch",
                            "The provided X-Amz-Content-SHA256 header does not match the request body"
                        );
                    }
                    return chain.filter(securedExchange(exchange, result.principal(), body));
                });
        }

        return chain.filter(securedExchange(exchange, result.principal(), null));
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

    private static ServerWebExchange securedExchange(ServerWebExchange exchange, String principal, byte[] cachedBody) {
        var requestBuilder = exchange.getRequest().mutate()
            .header("x-magrathea-principal", principal);
        if (cachedBody == null) {
            return exchange.mutate().request(requestBuilder.build()).build();
        }
        var originalRequest = requestBuilder.build();
        var decoratedRequest = new ServerHttpRequestDecorator(originalRequest) {
            @Override
            public Flux<DataBuffer> getBody() {
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(cachedBody);
                return Flux.just(buffer);
            }
        };
        return exchange.mutate().request(decoratedRequest).build();
    }

    private static String sha256Hex(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static Mono<Void> xmlError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_XML);
        String body = "<Error><code>" + escapeXml(code) + "</code><message>" + escapeXml(message) + "</message></Error>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length));
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
