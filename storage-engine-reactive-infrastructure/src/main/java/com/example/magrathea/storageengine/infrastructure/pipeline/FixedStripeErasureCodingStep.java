package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.pipeline.ErasureCodingStep;
import com.example.magrathea.storageengine.application.pipeline.StorageUnit;
import com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfig;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bounded physical erasure-coding step.
 *
 * <p>Each stripe contains {@code dataBlocks} one-MiB data shards and
 * {@code parityBlocks} parity shards. Parity uses independent GF(256) equations;
 * the first two parity rows are sufficient to reconstruct up to two missing data
 * shards. Only one stripe is retained at a time, so memory does not grow with the
 * object size.</p>
 */
public final class FixedStripeErasureCodingStep implements ErasureCodingStep {

    static final int SHARD_SIZE = 1024 * 1024;
    private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    private final int dataBlocks;
    private final int parityBlocks;
    private final int stripeSize;

    public FixedStripeErasureCodingStep(ErasureCodingConfig config) {
        this.dataBlocks = config.dataBlocks();
        this.parityBlocks = config.parityBlocks();
        this.stripeSize = Math.multiplyExact(dataBlocks, SHARD_SIZE);
    }

    @Override
    public Publisher<StorageUnit> apply(StorageUnit unit) {
        if (!(unit instanceof StorageUnit.FileUnit fileUnit)) {
            return Mono.just(unit);
        }
        return Flux.defer(() -> {
            StripeAccumulator accumulator = new StripeAccumulator(fileUnit);
            return fileUnit.data()
                    .concatMap(buffer -> Flux.fromIterable(accumulator.append(buffer)), 1)
                    .concatWith(Flux.defer(accumulator::finish))
                    .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        });
    }

    private final class StripeAccumulator {
        private final StorageUnit.FileUnit fileUnit;
        private final byte[] stripe = new byte[stripeSize];
        private int length;
        private int stripeIndex;

        private StripeAccumulator(StorageUnit.FileUnit fileUnit) {
            this.fileUnit = fileUnit;
        }

        private List<StorageUnit> append(DataBuffer buffer) {
            List<StorageUnit> completed = new ArrayList<>();
            try {
                while (buffer.readableByteCount() > 0) {
                    int copied = Math.min(stripeSize - length, buffer.readableByteCount());
                    buffer.read(stripe, length, copied);
                    length += copied;
                    if (length == stripeSize) {
                        completed.addAll(encode(Arrays.copyOf(stripe, stripeSize), length, stripeIndex++));
                        length = 0;
                    }
                }
                return completed;
            } finally {
                DataBufferUtils.release(buffer);
            }
        }

        private Publisher<StorageUnit> finish() {
            if (length == 0) {
                return Flux.empty();
            }
            List<StorageUnit> encoded = encode(Arrays.copyOf(stripe, stripeSize), length, stripeIndex++);
            length = 0;
            return Flux.fromIterable(encoded);
        }

        private List<StorageUnit> encode(byte[] stripeBytes, int logicalLength, int index) {
            byte[][] data = new byte[dataBlocks][SHARD_SIZE];
            long[] logicalSizes = new long[dataBlocks];
            for (int shard = 0; shard < dataBlocks; shard++) {
                int offset = shard * SHARD_SIZE;
                int available = Math.max(0, Math.min(SHARD_SIZE, logicalLength - offset));
                logicalSizes[shard] = available;
                if (available > 0) {
                    System.arraycopy(stripeBytes, offset, data[shard], 0, available);
                }
            }

            byte[][] parity = GaloisField256Codec.encodeParity(data, parityBlocks);

            List<StorageUnit> units = new ArrayList<>(dataBlocks + parityBlocks);
            for (int shard = 0; shard < dataBlocks; shard++) {
                units.add(new StorageUnit.ECShardUnit(
                        Flux.just(BUFFER_FACTORY.wrap(data[shard])), fileUnit.info(), index, shard,
                        false, logicalSizes[shard], dataBlocks, parityBlocks, logicalLength));
            }
            for (int shard = 0; shard < parityBlocks; shard++) {
                units.add(new StorageUnit.ECShardUnit(
                        Flux.just(BUFFER_FACTORY.wrap(parity[shard])), fileUnit.info(), index,
                        dataBlocks + shard, true, 0L, dataBlocks, parityBlocks, logicalLength));
            }
            return units;
        }
    }

}
