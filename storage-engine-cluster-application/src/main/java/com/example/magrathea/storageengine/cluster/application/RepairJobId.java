package com.example.magrathea.storageengine.cluster.application;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Stable identifier derived only from the typed semantic repair identity. */
public record RepairJobId(String value) implements Comparable<RepairJobId> {
    private static final String TYPE = "REPAIR";

    public RepairJobId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("repair job ID is required");
    }

    public static RepairJobId canonical(
            String bucket, String objectKey, long referenceGeneration, String artifactId, NodeIdentity target) {
        if (referenceGeneration < 1) throw new IllegalArgumentException("reference generation must be positive");
        Objects.requireNonNull(target, "target");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writeCanonical(out, TYPE);
                writeCanonical(out, requireText(bucket, "bucket"));
                writeCanonical(out, requireText(objectKey, "object key"));
                out.writeLong(referenceGeneration);
                writeCanonical(out, requireText(artifactId, "artifact ID"));
                writeCanonical(out, target.toString());
            }
            return new RepairJobId("repair-" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())));
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void writeCanonical(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    @Override public int compareTo(RepairJobId other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
