package com.example.magrathea.s3api.security;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public final class S3SecurityWebFilter implements WebFilter, Ordered {

    private final S3SecurityProperties properties;
    private final S3SigV4Verifier verifier;

    public S3SecurityWebFilter(S3SecurityProperties properties, S3SigV4Verifier verifier) {
        this.properties = properties;
        this.verifier = verifier;
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
        if (result.allowed()) {
            var secured = exchange.mutate()
                .request(exchange.getRequest().mutate()
                    .header("x-magrathea-principal", result.principal())
                    .build())
                .build();
            return chain.filter(secured);
        }
        return xmlError(exchange, HttpStatus.FORBIDDEN, result.errorCode(), result.message());
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
