package com.example.magrathea.s3api.security;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class S3CredentialStore {

    private final Map<String, Credential> credentialsByAccessKey;

    public S3CredentialStore(S3SecurityProperties properties) {
        this.credentialsByAccessKey = properties.getCredentials().stream()
            .filter(c -> c.getAccessKey() != null && !c.getAccessKey().isBlank())
            .filter(c -> c.getSecretKey() != null && !c.getSecretKey().isBlank())
            .collect(Collectors.toUnmodifiableMap(
                S3SecurityProperties.Credential::getAccessKey,
                c -> new Credential(c.getAccessKey(), c.getSecretKey(),
                    c.getPrincipal() == null || c.getPrincipal().isBlank() ? c.getAccessKey() : c.getPrincipal()),
                (left, right) -> right));
    }

    public Optional<Credential> findByAccessKey(String accessKey) {
        return Optional.ofNullable(credentialsByAccessKey.get(accessKey));
    }

    public record Credential(String accessKey, String secretKey, String principal) {
    }
}
