package com.example.magrathea.s3api.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class S3AuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        String errorCode = "AccessDenied";
        String message = "Access denied";
        if (ex instanceof S3AuthenticationException s3Exception) {
            errorCode = s3Exception.errorCode();
            message = s3Exception.getMessage();
        } else if (ex != null && ex.getMessage() != null && !ex.getMessage().isBlank()) {
            message = ex.getMessage();
        }
        return S3XmlErrorResponse.write(exchange, HttpStatus.FORBIDDEN, errorCode, message);
    }
}
