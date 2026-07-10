package com.example.magrathea.s3api.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public final class S3AuthenticationToken extends AbstractAuthenticationToken {

    private final String principal;

    private S3AuthenticationToken(String principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    public static S3AuthenticationToken authenticated(String principal) {
        return new S3AuthenticationToken(principal, List.of());
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal;
    }
}
