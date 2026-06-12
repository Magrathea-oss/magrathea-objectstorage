package com.example.magrathea.storageengine.infrastructure.yaml.dto;

/**
 * YAML DTO representing a serialised {@code StoragePolicy}.
 *
 * <p>Fields mirror the YAML schema exactly. Mapping to domain types is performed
 * by {@link com.example.magrathea.storageengine.infrastructure.yaml.YamlStoragePolicyCatalog}.
 *
 * <p>Public fields are intentional — Jackson binds YAML properties to public fields
 * without requiring getter/setter boilerplate.
 */
public class StoragePolicyYaml {

    /** Catalog entry identifier, e.g. {@code "minio-standard"}. Used as the lookup key. */
    public String policyId;

    /** Schema version string, e.g. {@code "1.0"}. Reserved for future migration use. */
    public String version = "1.0";

    /** Maps to {@code StorageClassId.value()}, e.g. {@code "STANDARD"} or {@code "MINIO_STANDARD"}. */
    public String storageClassId;

    /** Compression configuration; {@code null} means no compression. */
    public CompressionYaml compression;

    /** Encryption configuration; {@code null} or {@code enabled: false} means no encryption. */
    public EncryptionYaml encryption;

    /** Deduplication configuration; {@code null} or {@code enabled: false} means no dedup. */
    public DedupYaml dedup;

    /** Replication configuration; defaults to factor 1 if absent. */
    public ReplicationYaml replication;

    /** Erasure-coding configuration; {@code null} or {@code enabled: false} means no EC. */
    public ErasureCodingYaml erasureCoding;

    // -------------------------------------------------------------------------
    // Nested DTOs
    // -------------------------------------------------------------------------

    public static class CompressionYaml {
        /** When {@code false} the compression block is ignored. */
        public boolean enabled;
        /**
         * Maps to {@code CompressionAlgorithm} enum.
         * Recognised values: {@code GZIP}, {@code ZSTD}, {@code LZ4}.
         */
        public String algorithm;
        /**
         * Compression level (0–22). Defaults to {@code 0} when not specified.
         */
        public int level = 0;
    }

    public static class EncryptionYaml {
        /** When {@code false} the encryption block is ignored. */
        public boolean enabled;
        /**
         * Encryption mode / algorithm hint.
         * Recognised values: {@code NONE}, {@code SSE_S3}, {@code SSE_KMS}.
         */
        public String mode;
    }

    public static class DedupYaml {
        /** When {@code false} the dedup block is ignored. */
        public boolean enabled;
        /**
         * Dedup scope.
         * Recognised values: {@code BUCKET} (maps to {@code BUCKET_LEVEL}),
         * {@code GLOBAL} (maps to {@code GLOBAL_LEVEL}).
         */
        public String scope;
        /**
         * Chunk size in bytes. Must be in [4096, 1 GB].
         * Defaults to 1 MiB (1 048 576) when not specified.
         */
        public long chunkSizeBytes = 1_048_576L;
        /**
         * Fingerprint / content-address algorithm.
         * Recognised values: {@code SHA256}, {@code BLAKE2}, {@code XXHASH}.
         * Defaults to {@code SHA256}.
         */
        public String algorithm = "SHA256";
        /**
         * Chunk alignment strategy.
         * Recognised values: {@code NONE}, {@code BLOCK_BOUNDARY}.
         * Defaults to {@code NONE}.
         */
        public String alignment = "NONE";
        /** Namespace hint (informational, not mapped to domain). */
        public NamespaceYaml namespace;

        public static class NamespaceYaml {
            /** {@code BUCKET} or {@code GLOBAL}. */
            public String type;
        }
    }

    public static class ReplicationYaml {
        /** Number of replicas; must be >= 1. Defaults to {@code 1}. */
        public int factor = 1;
    }

    public static class ErasureCodingYaml {
        /** When {@code false} the erasure-coding block is ignored. */
        public boolean enabled;
        /** Number of data blocks (k). Must be >= 2 when enabled. */
        public int dataBlocks;
        /** Number of parity blocks (m). Must be >= 1 when enabled. */
        public int parityBlocks;
    }
}
