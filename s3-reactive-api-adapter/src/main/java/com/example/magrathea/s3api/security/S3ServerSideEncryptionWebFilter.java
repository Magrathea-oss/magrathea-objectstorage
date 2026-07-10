package com.example.magrathea.s3api.security;

import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;

public final class S3ServerSideEncryptionWebFilter implements WebFilter, Ordered {

    private final S3SecurityProperties properties;
    private final S3SecurityAuditSink auditSink;
    private final LocalS3KeyManagementService keyManagementService;

    public S3ServerSideEncryptionWebFilter(S3SecurityProperties properties,
                                           S3SecurityAuditSink auditSink,
                                           LocalS3KeyManagementService keyManagementService) {
        this.properties = properties;
        this.auditSink = auditSink;
        this.keyManagementService = keyManagementService;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        RequestResource resource = RequestResource.from(exchange);
        if (!isEncryptedBucket(resource.bucket())) {
            return chain.filter(exchange);
        }
        if (HttpMethod.PUT.equals(exchange.getRequest().getMethod()) && !resource.key().isBlank()) {
            return encryptPut(exchange, chain, resource);
        }
        if (HttpMethod.GET.equals(exchange.getRequest().getMethod()) && !resource.key().isBlank()) {
            return decryptGet(exchange, chain);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> encryptPut(ServerWebExchange exchange, WebFilterChain chain, RequestResource resource) {
        return bufferBody(exchange)
            .flatMap(plainBody -> {
                byte[] encrypted = keyManagementService.encryptObject(resource.bucket(), resource.key(), plainBody);
                writeInspectionCopy(resource, encrypted);
                var originalRequest = exchange.getRequest().mutate()
                    .header("x-amz-server-side-encryption", "AES256")
                    .build();
                var decoratedRequest = new ServerHttpRequestDecorator(originalRequest) {
                    @Override
                    public Flux<DataBuffer> getBody() {
                        return Flux.just(exchange.getResponse().bufferFactory().wrap(encrypted));
                    }
                };
                ServerWebExchange securedExchange = exchange.mutate().request(decoratedRequest).build();
                return chain.filter(securedExchange)
                    .then(recordEncryptionAudit(resource));
            });
    }

    private Mono<Void> decryptGet(ServerWebExchange exchange, WebFilterChain chain) {
        var decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                Flux<DataBuffer> decrypted = Flux.from(body).map(buffer -> {
                    byte[] encrypted = new byte[buffer.readableByteCount()];
                    buffer.read(encrypted);
                    DataBufferUtils.release(buffer);
                    RequestResource resource = RequestResource.from(exchange);
                    return bufferFactory().wrap(keyManagementService.decryptObject(resource.bucket(), resource.key(), encrypted));
                });
                return super.writeWith(decrypted);
            }
        };
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private Mono<Void> recordEncryptionAudit(RequestResource resource) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(authentication -> authentication instanceof S3AuthenticationToken)
            .map(authentication -> authentication.getName())
            .defaultIfEmpty("unknown")
            .doOnNext(principal -> auditSink.record(new S3SecurityAuditSink.AuditEvent(
                Instant.now(),
                java.util.UUID.randomUUID().toString(),
                principal,
                "s3:PutObject",
                resource.bucket(),
                resource.key(),
                "allow",
                "allowing-policy",
                200,
                "SSE-S3"
            )))
            .then();
    }

    private boolean isEncryptedBucket(String bucket) {
        S3SecurityProperties.BucketRule rule = properties.bucketRule(bucket);
        return rule != null && rule.isDefaultSseS3();
    }

    private void writeInspectionCopy(RequestResource resource, byte[] encrypted) {
        if (properties.getEncryptedInspectionRoot() == null || properties.getEncryptedInspectionRoot().isBlank()) {
            return;
        }
        try {
            Path root = Path.of(properties.getEncryptedInspectionRoot());
            Path file = root.resolve(resource.bucket()).resolve(resource.key());
            Files.createDirectories(file.getParent());
            Files.write(file, encrypted, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write encrypted inspection copy", e);
        }
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

    private record RequestResource(String bucket, String key) {
        static RequestResource from(ServerWebExchange exchange) {
            String[] path = Arrays.stream(exchange.getRequest().getURI().getRawPath().split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
            String bucket = path.length > 0 ? path[0] : "";
            String key = path.length > 1 ? String.join("/", Arrays.copyOfRange(path, 1, path.length)) : "";
            return new RequestResource(bucket, key);
        }
    }
}
