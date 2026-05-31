package com.example.magrathea.storageengine.domain.valueobject;

public abstract sealed class VirtualDevice {
    public abstract DeviceConfigurationHash configurationHash();

    public static final class BucketDevice extends VirtualDevice {
        private final BucketRef bucketRef;
        private final EffectiveStoragePolicy effectivePolicy;

        public BucketDevice(BucketRef bucketRef, EffectiveStoragePolicy effectivePolicy) {
            java.util.Objects.requireNonNull(bucketRef, "bucketRef must not be null");
            java.util.Objects.requireNonNull(effectivePolicy, "effectivePolicy must not be null");
            this.bucketRef = bucketRef;
            this.effectivePolicy = effectivePolicy;
        }

        public BucketRef bucketRef() {
            return bucketRef;
        }

        public EffectiveStoragePolicy effectivePolicy() {
            return effectivePolicy;
        }

        @Override
        public DeviceConfigurationHash configurationHash() {
            return WorkflowCompatibilityKey.from(effectivePolicy).deriveDeviceHash();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BucketDevice that)) return false;
            return bucketRef.equals(that.bucketRef) && effectivePolicy.equals(that.effectivePolicy);
        }

        @Override
        public int hashCode() {
            return 31 * bucketRef.hashCode() + effectivePolicy.hashCode();
        }

        @Override
        public String toString() {
            return "BucketDevice[" + bucketRef.bucketId().value() + "]";
        }
    }

    public static final class DedupDevice extends VirtualDevice {
        private final DedupNamespace namespace;
        private final WorkflowCompatibilityKey workflowKey;

        public DedupDevice(DedupNamespace namespace, WorkflowCompatibilityKey workflowKey) {
            java.util.Objects.requireNonNull(namespace, "namespace must not be null");
            java.util.Objects.requireNonNull(workflowKey, "workflowKey must not be null");
            this.namespace = namespace;
            this.workflowKey = workflowKey;
        }

        public DedupNamespace namespace() {
            return namespace;
        }

        public WorkflowCompatibilityKey workflowKey() {
            return workflowKey;
        }

        @Override
        public DeviceConfigurationHash configurationHash() {
            return workflowKey.deriveDeviceHash();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DedupDevice that)) return false;
            return namespace.equals(that.namespace) && workflowKey.equals(that.workflowKey);
        }

        @Override
        public int hashCode() {
            return 31 * namespace.hashCode() + workflowKey.hashCode();
        }

        @Override
        public String toString() {
            return "DedupDevice[" + namespace.canonicalRepresentation() + "]";
        }
    }
}
