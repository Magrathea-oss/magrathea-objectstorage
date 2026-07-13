package com.example.magrathea.s3api.cucumber.ep8;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class Ep8EvidenceSupport {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path ROOT = locateProjectRoot();

    private Ep8EvidenceSupport() {
    }

    static String read(String relativePath) throws IOException {
        return Files.readString(path(relativePath));
    }

    static JsonNode json(String relativePath) throws IOException {
        Path artifact = requiredFile(relativePath);
        return JSON.readTree(artifact.toFile());
    }

    static Path requiredFile(String relativePath) {
        Path artifact = path(relativePath);
        if (!Files.isRegularFile(artifact)) {
            throw new AssertionError("Required EP-8 evidence artifact is absent: " + relativePath
                    + ". Run scripts/run-supply-chain-evidence.sh from a clean commit first.");
        }
        return artifact;
    }

    static Path path(String relativePath) {
        return ROOT.resolve(relativePath).normalize();
    }

    static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Path locateProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("s3-reactive-api-adapter"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
