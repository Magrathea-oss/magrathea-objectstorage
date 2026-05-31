package com.example.magrathea.storageengine.domain.valueobject;

public sealed interface StepConfig {
    StepId stepId();

    record DedupStepConfig(DedupConfig config) implements StepConfig {
        public DedupStepConfig {
            java.util.Objects.requireNonNull(config, "config must not be null");
        }

        @Override
        public StepId stepId() {
            return StepId.DEDUP;
        }
    }

    record CompressStepConfig(CompressionConfig config) implements StepConfig {
        public CompressStepConfig {
            java.util.Objects.requireNonNull(config, "config must not be null");
        }

        @Override
        public StepId stepId() {
            return StepId.COMPRESS;
        }
    }

    record CryptStepConfig(EncryptionConfig config) implements StepConfig {
        public CryptStepConfig {
            java.util.Objects.requireNonNull(config, "config must not be null");
        }

        @Override
        public StepId stepId() {
            return StepId.CRYPT;
        }
    }

    record ECStepConfig(ErasureCodingConfig config) implements StepConfig {
        public ECStepConfig {
            java.util.Objects.requireNonNull(config, "config must not be null");
        }

        @Override
        public StepId stepId() {
            return StepId.ERASURE_CODING;
        }
    }

    record ReplicationStepConfig(ReplicationConfig config) implements StepConfig {
        public ReplicationStepConfig {
            java.util.Objects.requireNonNull(config, "config must not be null");
        }

        @Override
        public StepId stepId() {
            return StepId.REPLICATION;
        }
    }

    record StoreStepConfig(VirtualDevice targetDevice) implements StepConfig {
        public StoreStepConfig {
            java.util.Objects.requireNonNull(targetDevice, "targetDevice must not be null");
        }

        @Override
        public StepId stepId() {
            return StepId.STORE;
        }
    }
}
