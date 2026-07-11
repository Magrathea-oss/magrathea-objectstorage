package com.example.magrathea.storageengine.domain.valueobject;

/**
 * Semantic role of a persisted storage artifact.
 *
 * <p>Only multipart, deduplication, and erasure coding may produce segmented artifacts.
 * A normal single-object upload is represented by exactly one {@link #WHOLE_OBJECT} artifact.
 * {@link #LEGACY_CHUNK} exists only when reading schema 0/1 manifests whose historical
 * chunk entries did not retain enough information to infer a more specific role.</p>
 */
public enum StorageArtifactKind {
    WHOLE_OBJECT,
    MULTIPART_PART,
    DEDUP_CHUNK,
    EC_STRIPE,
    EC_DATA_SHARD,
    EC_PARITY_SHARD,
    LEGACY_CHUNK
}
