package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * BucketMetadataConfiguration — bucket metadata configuration rules.
 * <p>
 * Each rule declares a metadata resource type/subtype and its status, mirroring the
 * shape exposed by the S3-compatible bucket metadata configuration API so that the
 * aggregate can persist and return exactly what the API accepts.
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record BucketMetadataConfiguration(
    List<MetadataRule> rules
) {

    public BucketMetadataConfiguration {
        Objects.requireNonNull(rules);
        rules = List.copyOf(rules);
    }

    public static BucketMetadataConfiguration of(List<MetadataRule> rules) {
        return new BucketMetadataConfiguration(rules);
    }

    public static BucketMetadataConfiguration empty() {
        return new BucketMetadataConfiguration(List.of());
    }

    public List<MetadataRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    /** A single metadata configuration rule. */
    public record MetadataRule(
        String id,
        String status,
        String metadataResourceType,
        String metadataResourceSubtype
    ) {
        public static MetadataRule of(String id, String status,
                                      String metadataResourceType, String metadataResourceSubtype) {
            return new MetadataRule(id, status, metadataResourceType, metadataResourceSubtype);
        }
    }
}
