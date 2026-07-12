package com.example.magrathea.s3api.security;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
            return chain.filter(securedStreamingExchange(exchange, result.principal(), declaredPayloadHash))
                .onErrorResume(S3PayloadHashMismatchException.class, error -> xmlError(
                    exchange, HttpStatus.FORBIDDEN, "XAmzContentSHA256Mismatch",
                    "The provided X-Amz-Content-SHA256 header does not match the request body"));
        }

        return chain.filter(securedExchange(exchange, result.principal()));
    }

    private static boolean requiresPayloadHashVerification(String declaredPayloadHash) {
        return declaredPayloadHash != null
            && !declaredPayloadHash.isBlank()
            && !"UNSIGNED-PAYLOAD".equals(declaredPayloadHash);
    }

    private static ServerWebExchange securedExchange(ServerWebExchange exchange, String principal) {
        return exchange.mutate().request(exchange.getRequest().mutate()
            .header("x-magrathea-principal", principal).build()).build();
    }

    private static ServerWebExchange securedStreamingExchange(ServerWebExchange exchange, String principal,
                                                               String declaredPayloadHash) {
        var original = exchange.getRequest().mutate().header("x-magrathea-principal", principal).build();
        var decorated = new ServerHttpRequestDecorator(original) {
            @Override
            public Flux<DataBuffer> getBody() {
                MessageDigest digest = sha256();
                return super.getBody()
                    .doOnNext(buffer -> {
                        try (DataBuffer.ByteBufferIterator iterator = buffer.readableByteBuffers()) {
                            while (iterator.hasNext()) digest.update(iterator.next());
                        }
                    })
                    .concatWith(Mono.defer(() -> {
                        String actual = HexFormat.of().formatHex(digest.digest());
                        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                                declaredPayloadHash.getBytes(StandardCharsets.UTF_8))
                            ? Mono.empty() : Mono.error(new S3PayloadHashMismatchException());
                    }));
            }
        };
        return exchange.mutate().request(decorated).build();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
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
