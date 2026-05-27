package com.example.magrathea.objectstore.domain.valueobject;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * BucketMetadataConfiguration — a value object for bucket metadata key definitions.
 * <p>
 * Defines metadata keys and their types (e.g., dimension, timestamp) for S3 object metadata.
 * Used by CreateBucketMetadataConfiguration, GetBucketMetadataConfiguration, etc.
 * </p>
 * Pure domain — NO framework dependencies.
 */
public record BucketMetadataConfiguration(
    List<MetadataKeyDefinition> keys
) {

    public BucketMetadataConfiguration {
        Objects.requireNonNull(keys);
        keys = List.copyOf(keys);
    }

    /**
     * Factory method — create from a list of key definitions.
     */
    public static BucketMetadataConfiguration of(List<MetadataKeyDefinition> keys) {
        return new BucketMetadataConfiguration(keys);
    }

    /**
     * Factory method — empty configuration.
     */
    public static BucketMetadataConfiguration empty() {
        return new BucketMetadataConfiguration(List.of());
    }

    public List<MetadataKeyDefinition> keys() {
        return Collections.unmodifiableList(keys);
    }

    /**
     * A single metadata key definition.
     */
    public record MetadataKeyDefinition(
        String key,
        String type
    ) {

        public MetadataKeyDefinition {
            Objects.requireNonNull(key);
            Objects.requireNonNull(type);
            if (key.isBlank()) throw new IllegalArgumentException("key must not be blank");
            if (type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        }

        /**
         * Factory method.
         */
        public static MetadataKeyDefinition of(String key, String type) {
            return new MetadataKeyDefinition(key, type);
        }
    }
}
