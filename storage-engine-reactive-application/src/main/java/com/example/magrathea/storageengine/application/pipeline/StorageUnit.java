package com.example.magrathea.storageengine.application.pipeline;

import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.StorageUnitInfo;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * Transport object for the data processing pipeline.
 * Carries a reactive data stream (Flux<DataBuffer>) plus domain metadata (StorageUnitInfo).
 *
 * Lives in the application layer — NOT in domain — because it depends on Reactor types.
 *
 * Unit types:
 *   FileUnit     — whole object, not yet split (non-dedup path or before structural steps)
 *   ChunkUnit    — dedup window produced by DeduplicationStep
 *   ECStripeUnit — erasure coding stripe produced by ErasureCodingStep
 *   PartUnit     — multipart upload part (reserved for future use)
 *
 * KNOWN CRITICALITY (1): Flux<DataBuffer> is single-subscription. DeduplicationStep
 * must materialize each chunk window to compute the fingerprint, then re-wrap as a new
 * Flux for downstream steps. Accepted trade-off — each window is small (dedup chunk size).
 *
 * KNOWN CRITICALITY (2): ChunkUnit.fingerprint is Optional to permit lazy/parallel
 * fingerprint strategies in infrastructure. Implementations must ensure fingerprint is
 * present before StorePort.write() is called.
 *
 * TODO (criticality 4): Cleanup handles for partial writes on mid-pipeline failure
 * are not yet modelled in this type. To be added in a follow-up.
 */
public sealed interface StorageUnit {

    Flux<DataBuffer> data();
    StorageUnitInfo info();

    /**
     * Returns a copy of this unit with the given data stream replacing the current one.
     * Used by transform steps (compress, encrypt) to update data while preserving
     * the unit subtype and all metadata — without the step knowing the concrete type.
     */
    StorageUnit withData(Flux<DataBuffer> newData);

    record FileUnit(
        Flux<DataBuffer> data,
        StorageUnitInfo info
    ) implements StorageUnit {
        @Override
        public StorageUnit withData(Flux<DataBuffer> newData) {
            return new FileUnit(newData, info);
        }
    }

    record ChunkUnit(
        Flux<DataBuffer> data,
        StorageUnitInfo info,
        int index,
        Optional<Fingerprint> fingerprint,
        boolean deduplicatedReuse
    ) implements StorageUnit {
        @Override
        public StorageUnit withData(Flux<DataBuffer> newData) {
            return new ChunkUnit(newData, info, index, fingerprint, deduplicatedReuse);
        }
    }

    record ECStripeUnit(
        Flux<DataBuffer> data,
        StorageUnitInfo info,
        int stripeIndex,
        int dataBlocks,
        int parityBlocks
    ) implements StorageUnit {
        @Override
        public StorageUnit withData(Flux<DataBuffer> newData) {
            return new ECStripeUnit(newData, info, stripeIndex, dataBlocks, parityBlocks);
        }
    }

    /** One physical data or parity shard produced from a bounded EC stripe. */
    record ECShardUnit(
        Flux<DataBuffer> data,
        StorageUnitInfo info,
        int stripeIndex,
        int shardIndex,
        boolean parity,
        long logicalSize,
        int dataBlocks,
        int parityBlocks
    ) implements StorageUnit {
        @Override
        public StorageUnit withData(Flux<DataBuffer> newData) {
            return new ECShardUnit(newData, info, stripeIndex, shardIndex, parity,
                    logicalSize, dataBlocks, parityBlocks);
        }
    }

    /** Reserved for multipart upload — not yet integrated into the pipeline. */
    record PartUnit(
        Flux<DataBuffer> data,
        StorageUnitInfo info,
        int partNumber
    ) implements StorageUnit {
        @Override
        public StorageUnit withData(Flux<DataBuffer> newData) {
            return new PartUnit(newData, info, partNumber);
        }
    }
}
