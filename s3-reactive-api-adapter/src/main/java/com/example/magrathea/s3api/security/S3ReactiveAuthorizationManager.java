package com.example.magrathea.s3api.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import reactor.core.publisher.Mono;

import java.time.Instant;

public final class S3ReactiveAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final S3SecurityAuthorizer authorizer;
    private final S3SecurityAuditSink auditSink;

    public S3ReactiveAuthorizationManager(S3SecurityAuthorizer authorizer, S3SecurityAuditSink auditSink) {
        this.authorizer = authorizer;
        this.auditSink = auditSink;
    }

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
            .filter(Authentication::isAuthenticated)
            .filter(auth -> auth instanceof S3AuthenticationToken)
            .map(Authentication::getName)
            .map(principal -> authorize(principal, context))
            .cast(AuthorizationResult.class)
            .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private AuthorizationDecision authorize(String principal, AuthorizationContext context) {
        S3SecurityAuthorizer.AuthorizationResult authorization = authorizer.authorize(principal, context.getExchange().getRequest());
        if (authorization.allowed()) {
            auditSink.record(new S3SecurityAuditSink.AuditEvent(
                Instant.now(),
                principal,
                authorization.action(),
                authorization.bucket(),
                authorization.key(),
                "allow",
                "allowing-policy",
                HttpStatus.OK.value()
            ));
            return new AuthorizationDecision(true);
        }
        auditSink.record(new S3SecurityAuditSink.AuditEvent(
            Instant.now(),
            principal,
            authorization.action(),
            authorization.bucket(),
            authorization.key(),
            "deny",
            authorization.reason(),
            HttpStatus.FORBIDDEN.value()
        ));
        return new AuthorizationDecision(false);
    }
}
