package com.example.magrathea.s3api.adapter.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;

/**
 * Durable object ACL sidecar store for S3 object metadata operations.
 *
 * <p>This store is intentionally internal to the S3 adapter boundary: it persists
 * S3 ACL metadata under the selected storage-engine root without exposing a
 * parallel storage-engine object API.</p>
 */
public final class S3ObjectAclStore {

    private final Path root;

    public S3ObjectAclStore(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create S3 object ACL store " + root, e);
        }
    }

    public void save(String bucket, String key, String permission, String grantee) {
        try {
            Path path = path(bucket, key);
            Files.createDirectories(path.getParent());
            Properties properties = new Properties();
            properties.setProperty("bucket", bucket);
            properties.setProperty("key", key);
            properties.setProperty("permission", permission == null || permission.isBlank() ? "private" : permission);
            properties.setProperty("grantee", grantee == null || grantee.isBlank() ? "owner" : grantee);
            try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                properties.store(writer, "Magrathea S3 object ACL");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save S3 object ACL", e);
        }
    }

    public Optional<ObjectAcl> find(String bucket, String key) {
        Path path = path(bucket, key);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return Optional.of(new ObjectAcl(
                properties.getProperty("permission", "private"),
                properties.getProperty("grantee", "owner")));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read S3 object ACL", e);
        }
    }

    public void clear() {
        deleteRecursively(root);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to recreate S3 object ACL store " + root, e);
        }
    }

    private Path path(String bucket, String key) {
        return root.resolve(encode(bucket)).resolve(encode(key) + ".properties");
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clear S3 object ACL store " + path, e);
        }
    }

    public record ObjectAcl(String permission, String grantee) {
    }
}
