package com.example.magrathea.s3api.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class FileS3SecurityAuditSink implements S3SecurityAuditSink {

    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private final Path file;

    public FileS3SecurityAuditSink(Path file) {
        this.file = file;
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create audit directory " + file.getParent(), e);
        }
    }

    @Override
    public synchronized void record(AuditEvent event) {
        try {
            String previousHash = lastHash();
            String serializedFields = serializeFields(event);
            String line = serializedFields + "\t" + chainHash(previousHash, serializedFields) + System.lineSeparator();
            try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                channel.force(true);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append S3 security audit event", e);
        }
    }

    @Override
    public synchronized List<AuditEvent> events() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<AuditEvent> events = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    events.add(deserialize(line));
                }
            }
            return List.copyOf(events);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read S3 security audit events", e);
        }
    }

    public synchronized boolean verifyIntegrity() {
        if (!Files.exists(file)) {
            return true;
        }
        try {
            String previousHash = GENESIS_HASH;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length == 10) {
                    previousHash = chainHash(previousHash, line);
                    continue;
                }
                if (parts.length != 11) {
                    return false;
                }
                String fields = String.join("\t", java.util.Arrays.copyOf(parts, 10));
                String expected = chainHash(previousHash, fields);
                if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[10].getBytes(StandardCharsets.UTF_8))) {
                    return false;
                }
                previousHash = parts[10];
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public synchronized void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clear S3 security audit events", e);
        }
    }

    public Path file() {
        return file;
    }

    private String lastHash() throws IOException {
        if (!Files.exists(file)) {
            return GENESIS_HASH;
        }
        String previousHash = GENESIS_HASH;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length == 11) {
                previousHash = parts[10];
            } else if (parts.length == 10) {
                previousHash = chainHash(previousHash, line);
            }
        }
        return previousHash;
    }

    private static String serializeFields(AuditEvent event) {
        return String.join("\t",
            encode(event.timestamp().toString()),
            encode(event.requestId()),
            encode(event.principal()),
            encode(event.action()),
            encode(event.bucket()),
            encode(event.key()),
            encode(event.decision()),
            encode(event.reason()),
            Integer.toString(event.responseStatus()),
            encode(event.encryptionMode())
        );
    }

    private static AuditEvent deserialize(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 10 && parts.length != 11) {
            throw new IllegalStateException("Malformed audit event line: " + line);
        }
        return new AuditEvent(
            Instant.parse(decode(parts[0])),
            decode(parts[1]),
            decode(parts[2]),
            decode(parts[3]),
            decode(parts[4]),
            decode(parts[5]),
            decode(parts[6]),
            decode(parts[7]),
            Integer.parseInt(parts[8]),
            decode(parts[9])
        );
    }

    private static String chainHash(String previousHash, String serializedFields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(previousHash.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(serializedFields.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        return java.util.Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return new String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
