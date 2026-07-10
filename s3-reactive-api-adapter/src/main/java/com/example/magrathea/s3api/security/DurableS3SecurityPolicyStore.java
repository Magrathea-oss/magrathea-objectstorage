package com.example.magrathea.s3api.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DurableS3SecurityPolicyStore {

    private final Path file;

    public DurableS3SecurityPolicyStore(Path file) {
        this.file = file;
    }

    public List<PolicyRule> rules() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.filter(line -> !line.isBlank()).map(this::deserialize).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read S3 security policy store " + file, e);
        }
    }

    public void append(PolicyRule rule) {
        List<PolicyRule> rules = new ArrayList<>(rules());
        rules.add(rule);
        write(rules);
    }

    public void write(List<PolicyRule> rules) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String content = rules.stream().map(this::serialize).collect(Collectors.joining(System.lineSeparator()));
            if (!content.isEmpty()) {
                content += System.lineSeparator();
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write S3 security policy store " + file, e);
        }
    }

    public Path file() {
        return file;
    }

    private String serialize(PolicyRule rule) {
        return String.join("\t",
            rule.effect(),
            encode(rule.principal()),
            encode(rule.action()),
            encode(rule.bucket()),
            encode(rule.keyPrefix()));
    }

    private PolicyRule deserialize(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 5) {
            throw new IllegalStateException("Malformed policy store line");
        }
        return new PolicyRule(parts[0], decode(parts[1]), decode(parts[2]), decode(parts[3]), decode(parts[4]));
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public record PolicyRule(String effect, String principal, String action, String bucket, String keyPrefix) {
        public boolean allow() {
            return "allow".equals(effect);
        }

        public boolean deny() {
            return "deny".equals(effect);
        }

        boolean matches(String candidatePrincipal, String candidateAction, String candidateBucket, String candidateKey) {
            return matchesValue(principal, candidatePrincipal)
                && matchesValue(action, candidateAction)
                && matchesValue(bucket, candidateBucket)
                && (keyPrefix == null || keyPrefix.isBlank() || candidateKey.startsWith(keyPrefix));
        }

        private static boolean matchesValue(String configured, String actual) {
            return "*".equals(configured) || (configured != null && configured.equals(actual));
        }
    }
}
