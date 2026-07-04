package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * BucketMetadataTableConfiguration — bucket metadata-table configuration rules.
 * <p>
 * Each rule binds a metadata table name and database with a status, mirroring the shape
 * exposed by the S3-compatible bucket metadata-table configuration API so that the
 * aggregate can persist and return exactly what the API accepts.
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record BucketMetadataTableConfiguration(
    List<MetadataTableRule> rules
) {

    public BucketMetadataTableConfiguration {
        Objects.requireNonNull(rules);
        rules = List.copyOf(rules);
    }

    public static BucketMetadataTableConfiguration of(List<MetadataTableRule> rules) {
        return new BucketMetadataTableConfiguration(rules);
    }

    public static BucketMetadataTableConfiguration empty() {
        return new BucketMetadataTableConfiguration(List.of());
    }

    public List<MetadataTableRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    /** A single metadata-table configuration rule. */
    public record MetadataTableRule(
        String id,
        String status,
        String metadataTableName,
        String metadataTableDatabase
    ) {
        public static MetadataTableRule of(String id, String status,
                                           String metadataTableName, String metadataTableDatabase) {
            return new MetadataTableRule(id, status, metadataTableName, metadataTableDatabase);
        }
    }
}
