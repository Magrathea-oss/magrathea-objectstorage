package com.example.magrathea.storageengine.cluster.application;

/**
 * Bounded read-only query for current consensus references in canonical namespace order.
 * The exclusive cursor is process-local input and is never committed to consensus.
 */
public record ReferencePageQuery(Cursor exclusiveAfter, int limit) {
    public static final int MAXIMUM_LIMIT = 256;

    public ReferencePageQuery {
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException("reference page limit must be between 1 and "
                    + MAXIMUM_LIMIT);
        }
    }

    /** Exact last-seen reference identity; ordering uses its canonical namespace key. */
    public record Cursor(String bucket, String objectKey, long generation) {
        public Cursor {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalArgumentException("cursor bucket is required");
            }
            if (objectKey == null || objectKey.isBlank()) {
                throw new IllegalArgumentException("cursor object key is required");
            }
            if (generation < 1) {
                throw new IllegalArgumentException("cursor generation must be positive");
            }
        }

        public static Cursor after(ObjectReferenceGeneration reference) {
            return new Cursor(reference.bucket(), reference.objectKey(), reference.generation());
        }

        public String namespaceKey() {
            return bucket + "\u0000" + objectKey;
        }
    }
}
