package com.example.magrathea.storageengine.domain.valueobject;

import java.util.List;
import java.util.Optional;

public sealed interface StepOutcome {
    record DedupOutcome(boolean matched, Optional<ChunkId> existingChunkId) implements StepOutcome {
        public DedupOutcome {
            java.util.Objects.requireNonNull(existingChunkId, "existingChunkId must not be null");
        }
    }

    record CompressOutcome(CompressionAlgorithm algorithm, long originalSize, long compressedSize)
            implements StepOutcome {
        public CompressOutcome {
            java.util.Objects.requireNonNull(algorithm, "algorithm must not be null");
            if (originalSize < 0) {
                throw new IllegalArgumentException("originalSize must be >= 0: " + originalSize);
            }
            if (compressedSize < 0) {
                throw new IllegalArgumentException("compressedSize must be >= 0: " + compressedSize);
            }
        }
    }

    record CryptOutcome(EncryptionAlgorithm algorithm, Optional<KeyReference> keyReference)
            implements StepOutcome {
        public CryptOutcome {
            java.util.Objects.requireNonNull(algorithm, "algorithm must not be null");
            java.util.Objects.requireNonNull(keyReference, "keyReference must not be null");
        }
    }

    record ErasureCodingOutcome(int k, int m, List<NodeId> dataNodes, List<NodeId> parityNodes)
            implements StepOutcome {
        public ErasureCodingOutcome {
            if (k < 2) {
                throw new IllegalArgumentException("k must be >= 2: " + k);
            }
            if (m < 1) {
                throw new IllegalArgumentException("m must be >= 1: " + m);
            }
            java.util.Objects.requireNonNull(dataNodes, "dataNodes must not be null");
            java.util.Objects.requireNonNull(parityNodes, "parityNodes must not be null");
            dataNodes = List.copyOf(dataNodes);
            parityNodes = List.copyOf(parityNodes);
        }
    }

    record ReplicationOutcome(int factor, List<NodeId> nodes) implements StepOutcome {
        public ReplicationOutcome {
            if (factor < 1) {
                throw new IllegalArgumentException("factor must be >= 1: " + factor);
            }
            java.util.Objects.requireNonNull(nodes, "nodes must not be null");
            nodes = List.copyOf(nodes);
        }
    }

    record StoreOutcome(VirtualDevice targetDevice, List<NodeId> locations, long storedSize)
            implements StepOutcome {
        public StoreOutcome {
            java.util.Objects.requireNonNull(targetDevice, "targetDevice must not be null");
            java.util.Objects.requireNonNull(locations, "locations must not be null");
            locations = List.copyOf(locations);
            if (storedSize < 0) {
                throw new IllegalArgumentException("storedSize must be >= 0: " + storedSize);
            }
        }
    }
}
