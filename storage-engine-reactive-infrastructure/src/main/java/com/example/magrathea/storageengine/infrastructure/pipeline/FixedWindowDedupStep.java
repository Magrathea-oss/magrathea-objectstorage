package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.port.ContentAddressIndex;
import com.example.magrathea.storageengine.application.pipeline.DeduplicationStep;
import com.example.magrathea.storageengine.application.pipeline.StorageUnit;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.DeviceConfigurationHash;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Fixed-size window deduplication step.
 *
 * Windows the FileUnit data flux into configured chunkSize byte ranges while consuming
 * DataBuffers incrementally. For each completed window:
 * 1. Computes SHA-256 fingerprint directly (no ChecksumPort)
 * 2. Looks up fingerprint in ContentAddressIndex
 * 3. If found → emits ChunkUnit with Flux.empty() and deduplicatedReuse=true
 * 4. If not found → emits ChunkUnit with data re-wrapped as Flux<DataBuffer>
 *
 * ChunkUnit carries fingerprint as ContentHash (SHA-256, hex string).
 * The orchestrator reconstructs Fingerprint from the hex value for contentAddressIndex.record().
 */
public class FixedWindowDedupStep implements DeduplicationStep {

    private static final int DEFAULT_CHUNK_SIZE = 1_048_576; // 1 MB
    private static final DataBufferFactory BUF_FACTORY = new DefaultDataBufferFactory();

    private final ContentAddressIndex contentAddressIndex;
    private final int chunkSize;

    public FixedWindowDedupStep(ContentAddressIndex contentAddressIndex) {
        this(contentAddressIndex, DEFAULT_CHUNK_SIZE);
    }

    public FixedWindowDedupStep(ContentAddressIndex contentAddressIndex, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        this.contentAddressIndex = contentAddressIndex;
        this.chunkSize = chunkSize;
    }

    @Override
    public Publisher<StorageUnit> apply(StorageUnit unit) {
        if (unit instanceof StorageUnit.FileUnit fileUnit) {
            return windowAndDedup(fileUnit);
        }
        // ChunkUnit / ECStripeUnit / PartUnit pass through unchanged
        return Mono.just(unit);
    }

    /**
     * Emits chunk windows as DataBuffers arrive. Each window is fingerprinted and
     * looked up in the content address index before it is passed downstream.
     */
    private Publisher<StorageUnit> windowAndDedup(StorageUnit.FileUnit fileUnit) {
        DeviceConfigurationHash deviceHash = fileUnit.info().deviceHash()
                .orElseThrow(() -> new IllegalArgumentException(
                        "deviceHash required for dedup — set on StorageUnitInfo"));
        return Flux.defer(() -> {
            WindowAccumulator accumulator = new WindowAccumulator(fileUnit, deviceHash);
            return fileUnit.data()
                    .concatMap(buffer -> Flux.fromIterable(accumulator.append(buffer))
                            .concatMap(window -> window, 1), 1)
                    .concatWith(Mono.defer(accumulator::finish))
                    .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
        });
    }

    private Mono<StorageUnit> deduplicateWindow(
            byte[] windowBytes,
            StorageUnit.FileUnit fileUnit,
            DeviceConfigurationHash deviceHash,
            int windowIndex) {
        String hex = sha256Hex(windowBytes);
        Fingerprint fingerprint = Fingerprint.of(FingerprintAlgorithm.SHA256, hex);
        return contentAddressIndex.find(deviceHash, fingerprint)
                .map(optDescriptor -> {
                    if (optDescriptor.isPresent()) {
                        return (StorageUnit) new StorageUnit.ChunkUnit(
                                Flux.empty(),
                                fileUnit.info(),
                                windowIndex,
                                Optional.of(fingerprint),
                                true);
                    }
                    Flux<DataBuffer> chunkData = Flux.just(BUF_FACTORY.wrap(windowBytes));
                    return new StorageUnit.ChunkUnit(
                            chunkData,
                            fileUnit.info(),
                            windowIndex,
                            Optional.of(fingerprint),
                            false);
                });
    }

    private final class WindowAccumulator {
        private final StorageUnit.FileUnit fileUnit;
        private final DeviceConfigurationHash deviceHash;
        private final byte[] currentWindow = new byte[chunkSize];
        private int currentLength;
        private int nextIndex;

        private WindowAccumulator(StorageUnit.FileUnit fileUnit, DeviceConfigurationHash deviceHash) {
            this.fileUnit = fileUnit;
            this.deviceHash = deviceHash;
        }

        private List<Mono<StorageUnit>> append(DataBuffer buffer) {
            List<Mono<StorageUnit>> completedWindows = new ArrayList<>();
            try {
                while (buffer.readableByteCount() > 0) {
                    int bytesToRead = Math.min(chunkSize - currentLength, buffer.readableByteCount());
                    buffer.read(currentWindow, currentLength, bytesToRead);
                    currentLength += bytesToRead;
                    if (currentLength == chunkSize) {
                        completedWindows.add(deduplicateWindow(
                                Arrays.copyOf(currentWindow, currentLength),
                                fileUnit,
                                deviceHash,
                                nextIndex++));
                        currentLength = 0;
                    }
                }
                return completedWindows;
            } finally {
                DataBufferUtils.release(buffer);
            }
        }

        private Mono<StorageUnit> finish() {
            if (currentLength == 0) {
                return Mono.empty();
            }
            return deduplicateWindow(
                    Arrays.copyOf(currentWindow, currentLength),
                    fileUnit,
                    deviceHash,
                    nextIndex++);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
