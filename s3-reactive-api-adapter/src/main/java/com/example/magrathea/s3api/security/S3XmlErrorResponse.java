package com.example.magrathea.s3api.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

final class S3XmlErrorResponse {

    private S3XmlErrorResponse() {
    }

    static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
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
