package com.example.magrathea.storageengine.domain.valueobject;

public abstract sealed class DedupNamespace {
    public abstract String canonicalRepresentation();

    public static final class GlobalDedupNamespace extends DedupNamespace {
        public static final GlobalDedupNamespace INSTANCE = new GlobalDedupNamespace();

        private GlobalDedupNamespace() {}

        @Override
        public String canonicalRepresentation() {
            return "global";
        }

        // Singleton: overrides equals/hashCode for identity semantics
        @Override
        public boolean equals(Object obj) {
            return obj instanceof GlobalDedupNamespace;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    public static final class BucketDedupNamespace extends DedupNamespace {
        private final BucketRef bucketRef;

        public BucketDedupNamespace(BucketRef bucketRef) {
            java.util.Objects.requireNonNull(bucketRef, "bucketRef must not be null");
            this.bucketRef = bucketRef;
        }

        public BucketRef bucketRef() {
            return bucketRef;
        }

        @Override
        public String canonicalRepresentation() {
            return "bucket:" + bucketRef.bucketId().value();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BucketDedupNamespace that)) return false;
            return bucketRef.equals(that.bucketRef);
        }

        @Override
        public int hashCode() {
            return bucketRef.hashCode();
        }

        @Override
        public String toString() {
            return "BucketDedupNamespace[" + canonicalRepresentation() + "]";
        }
    }
}
