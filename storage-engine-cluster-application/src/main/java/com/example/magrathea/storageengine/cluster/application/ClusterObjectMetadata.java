package com.example.magrathea.storageengine.cluster.application;

import java.time.Instant;
import java.util.Map;

/** Bounded S3-facing metadata committed beside an immutable whole-object reference. */
public record ClusterObjectMetadata(
        String storageClass,
        Map<String, String> userMetadata,
        Map<String, String> objectTags,
        String etag,
        Instant createdAt) {
    public static final ClusterObjectMetadata EMPTY =
            new ClusterObjectMetadata("STANDARD", Map.of(), Map.of(), "", Instant.EPOCH);

    public ClusterObjectMetadata {
        storageClass = storageClass == null || storageClass.isBlank() ? "STANDARD" : storageClass;
        userMetadata = Map.copyOf(userMetadata == null ? Map.of() : userMetadata);
        objectTags = Map.copyOf(objectTags == null ? Map.of() : objectTags);
        if (etag == null) etag = "";
        if (!etag.isEmpty() && !etag.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("whole-object lowercase MD5 ETag is required");
        }
        if (createdAt == null) createdAt = Instant.EPOCH;
    }
}
