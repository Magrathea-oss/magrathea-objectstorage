package com.example.magrathea.s3api.capacity;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/** Fail-fast admission, timeout, and streaming body-size controls at the S3 HTTP boundary. */
public final class S3CapacityWebFilter implements WebFilter, Ordered {

    private final S3CapacityProperties properties;
    private final S3CapacityMetrics metrics;
    private final Semaphore permits;
    private final S3TokenBucket tokenBucket;

    public S3CapacityWebFilter(S3CapacityProperties properties, S3CapacityMetrics metrics) {
        this(properties, metrics, Clock.systemUTC());
    }

    public S3CapacityWebFilter(S3CapacityProperties properties, S3CapacityMetrics metrics, Clock clock) {
        this.properties = properties;
        this.metrics = metrics;
        this.permits = new Semaphore(properties.getMaxConcurrentRequests());
        this.tokenBucket = new S3TokenBucket(properties.getRateLimitPerSecond(), properties.getRateLimitBurst(), clock);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled() || !isS3Request(exchange)) return chain.filter(exchange);

        String operation = operation(exchange);
        long bodyLimit = bodyLimit(exchange);
        long declaredLength = exchange.getRequest().getHeaders().getContentLength();
        if (bodyLimit > 0 && declaredLength > bodyLimit) {
            metrics.entityTooLarge(operation);
            return xmlError(exchange, HttpStatus.PAYLOAD_TOO_LARGE, "EntityTooLarge",
                "Your proposed upload exceeds the configured maximum allowed object size", null);
        }
        if (!tokenBucket.tryConsume()) {
            metrics.rateRejected(operation);
            return xmlError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SlowDown",
                "Please reduce your request rate", "1");
        }
        if (!permits.tryAcquire()) {
            metrics.concurrencyRejected(operation);
            return xmlError(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SlowDown",
                "The S3 server concurrency limit is currently exhausted", null);
        }

        metrics.admitted(operation);
        ServerWebExchange bounded = bodyLimit > 0 ? withBoundedBody(exchange, bodyLimit) : exchange;
        return chain.filter(bounded)
            .timeout(properties.getRequestTimeout())
            .onErrorResume(S3EntityTooLargeException.class, error -> {
                metrics.entityTooLarge(operation);
                return xmlError(exchange, HttpStatus.PAYLOAD_TOO_LARGE, "EntityTooLarge",
                    "Your proposed upload exceeds the configured maximum allowed object size", null);
            })
            .onErrorResume(TimeoutException.class, error -> {
                metrics.timedOut(operation);
                return xmlError(exchange, HttpStatus.REQUEST_TIMEOUT, "RequestTimeout",
                    "Your socket connection to the server was not read from or written to within the timeout period", null);
            })
            .doFinally(signal -> {
                metrics.completed();
                permits.release();
            });
    }

    private ServerWebExchange withBoundedBody(ServerWebExchange exchange, long maximumBytes) {
        var request = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                var received = new AtomicLong();
                return super.getBody()
                    .timeout(properties.getRequestTimeout())
                    .handle((buffer, sink) -> {
                        if (received.addAndGet(buffer.readableByteCount()) > maximumBytes) {
                            sink.error(new S3EntityTooLargeException(maximumBytes));
                        } else {
                            sink.next(buffer);
                        }
                    });
            }
        };
        return exchange.mutate().request(request).build();
    }

    private long bodyLimit(ServerWebExchange exchange) {
        if (exchange.getRequest().getMethod() != HttpMethod.PUT) return -1;
        var query = exchange.getRequest().getQueryParams();
        if (query.containsKey("uploadId") && query.containsKey("partNumber")
            && exchange.getRequest().getHeaders().getFirst("x-amz-copy-source") == null) {
            return properties.getMaxMultipartPartBytes();
        }
        if (!query.containsKey("uploadId") && exchange.getRequest().getHeaders().getFirst("x-amz-copy-source") == null) {
            return properties.getMaxSinglePutBytes();
        }
        return -1;
    }

    private static boolean isS3Request(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        return !(path.equals("/health") || path.startsWith("/actuator/")
            || path.equals("/actuator") || path.startsWith("/admin/") || path.equals("/admin"));
    }

    static String operation(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        var query = request.getQueryParams();
        if (request.getMethod() == HttpMethod.PUT && query.containsKey("uploadId")) return "UploadPart";
        if (request.getMethod() == HttpMethod.POST && query.containsKey("uploadId")) return "CompleteMultipartUpload";
        if (request.getMethod() == HttpMethod.POST && query.containsKey("uploads")) return "CreateMultipartUpload";
        if (request.getMethod() == HttpMethod.PUT) return "PutObject";
        if (request.getMethod() == HttpMethod.GET) return "Get";
        if (request.getMethod() == HttpMethod.HEAD) return "Head";
        if (request.getMethod() == HttpMethod.DELETE) return "Delete";
        return "Other";
    }

    private static Mono<Void> xmlError(ServerWebExchange exchange, HttpStatus status,
                                       String code, String message, String retryAfter) {
        var response = exchange.getResponse();
        if (response.isCommitted()) return Mono.error(new IllegalStateException(code + " after response commit"));
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_XML);
        if (retryAfter != null) response.getHeaders().set(HttpHeaders.RETRY_AFTER, retryAfter);
        String xml = "<Error><Code>" + code + "</Code><Message>" + message + "</Message></Error>";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
