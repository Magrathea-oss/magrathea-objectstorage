package com.example.magrathea.s3api.security;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

public final class S3ReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (authentication instanceof S3AuthenticationToken && authentication.isAuthenticated()) {
            return Mono.just(authentication);
        }
        return Mono.empty();
    }
}
