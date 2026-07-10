package com.example.magrathea.s3api.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class S3CredentialStore {

    private final S3SecurityProperties properties;
    private final LocalS3KeyManagementService keyManagementService;

    public S3CredentialStore(S3SecurityProperties properties) {
        this(properties, null);
    }

    public S3CredentialStore(S3SecurityProperties properties, LocalS3KeyManagementService keyManagementService) {
        this.properties = properties;
        this.keyManagementService = keyManagementService;
    }

    public Optional<Credential> findByAccessKey(String accessKey) {
        return loadCredentials().stream()
            .filter(credential -> credential.accessKey().equals(accessKey))
            .findFirst()
            .filter(credential -> !credential.revoked());
    }

    public Optional<Credential> findIncludingRevoked(String accessKey) {
        return loadCredentials().stream()
            .filter(credential -> credential.accessKey().equals(accessKey))
            .findFirst();
    }

    public List<Credential> loadCredentials() {
        Map<String, Credential> configured = properties.getCredentials().stream()
            .filter(c -> c.getAccessKey() != null && !c.getAccessKey().isBlank())
            .filter(c -> c.getSecretKey() != null && !c.getSecretKey().isBlank())
            .collect(Collectors.toMap(
                S3SecurityProperties.Credential::getAccessKey,
                c -> new Credential(c.getAccessKey(), c.getSecretKey(),
                    c.getPrincipal() == null || c.getPrincipal().isBlank() ? c.getAccessKey() : c.getPrincipal(),
                    c.isRevoked()),
                (left, right) -> right));
        loadFileCredentials().forEach(credential -> configured.put(credential.accessKey(), credential));
        return List.copyOf(configured.values());
    }

    public void upsertCredential(String accessKey, String secretKey, String principal, boolean revoked) {
        requireFileBacked();
        List<Credential> credentials = new ArrayList<>(loadFileCredentials());
        credentials.removeIf(existing -> existing.accessKey().equals(accessKey));
        credentials.add(new Credential(accessKey, secretKey, principal, revoked));
        writeFileCredentials(credentials);
    }

    public void revoke(String accessKey) {
        Credential credential = findIncludingRevoked(accessKey)
            .orElseThrow(() -> new IllegalArgumentException("Unknown access key " + accessKey));
        upsertCredential(credential.accessKey(), credential.secretKey(), credential.principal(), true);
    }

    private List<Credential> loadFileCredentials() {
        if (properties.getCredentialFile() == null || properties.getCredentialFile().isBlank()) {
            return List.of();
        }
        Path file = Path.of(properties.getCredentialFile());
        if (!Files.exists(file)) {
            return List.of();
        }
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines
                .filter(line -> !line.isBlank())
                .map(this::deserialize)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read S3 credential store " + file, e);
        }
    }

    private void writeFileCredentials(List<Credential> credentials) {
        Path file = Path.of(properties.getCredentialFile());
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String content = credentials.stream().map(this::serialize).collect(Collectors.joining(System.lineSeparator()));
            if (!content.isEmpty()) {
                content += System.lineSeparator();
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write S3 credential store " + file, e);
        }
    }

    private String serialize(Credential credential) {
        String secret = keyManagementService == null
            ? credential.secretKey()
            : "enc:" + keyManagementService.encryptSecret(credential.secretKey());
        return String.join("\t",
            encode(credential.accessKey()),
            encode(secret),
            encode(credential.principal()),
            credential.revoked() ? "revoked" : "active");
    }

    private Credential deserialize(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 4) {
            throw new IllegalStateException("Malformed credential store line");
        }
        String secret = decode(parts[1]);
        if (secret.startsWith("enc:")) {
            if (keyManagementService == null) {
                throw new IllegalStateException("Encrypted credential requires local key-management service");
            }
            secret = keyManagementService.decryptSecret(secret.substring("enc:".length()));
        }
        return new Credential(decode(parts[0]), secret, decode(parts[2]), "revoked".equals(parts[3]));
    }

    private void requireFileBacked() {
        if (properties.getCredentialFile() == null || properties.getCredentialFile().isBlank()) {
            throw new IllegalStateException("s3.security.credential-file is required for durable credential mutations");
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public record Credential(String accessKey, String secretKey, String principal, boolean revoked) {
        public Credential(String accessKey, String secretKey, String principal) {
            this(accessKey, secretKey, principal, false);
        }
    }
}
