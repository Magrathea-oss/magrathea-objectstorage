package com.example.magrathea.s3api.security;

import org.springframework.security.core.AuthenticationException;

public final class S3AuthenticationException extends AuthenticationException {

    private final String errorCode;

    public S3AuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
