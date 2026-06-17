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
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Fixed-size window deduplication step.
 *
 * Windows the FileUnit data flux into chunks of chunkSize bytes.
 * For each window:
 * 1. Materialises bytes into byte[] (single-subscription issue — see criticality 1)
 * 2. Computes SHA-256 fingerprint directly (no ChecksumPort)
 * 3. Looks up fingerprint in ContentAddressIndex
 * 4. If found → emits ChunkUnit with Flux.empty() and deduplicatedReuse=true
 * 5. If not found → emits ChunkUnit with data re-wrapped as Flux<DataBuffer>
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
     * Materialises all DataBuffers from the FileUnit into a single byte array,
     * then splits into chunkSize windows. Each window is fingerprinted and
     * looked up in the content address index.
     */
    private Publisher<StorageUnit> windowAndDedup(StorageUnit.FileUnit fileUnit) {
        DeviceConfigurationHash deviceHash = fileUnit.info().deviceHash()
                .orElseThrow(() -> new IllegalArgumentException(
                        "deviceHash required for dedup — set on StorageUnitInfo"));
        // Materialise all data buffers into a single byte array
        return DataBufferUtils.join(fileUnit.data())
                .flatMapMany(buffer -> {
                    byte[] allBytes = new byte[buffer.readableByteCount()];
                    buffer.read(allBytes);
                    DataBufferUtils.release(buffer);
                    return splitIntoWindows(allBytes, fileUnit, deviceHash);
                });
    }

    private Publisher<StorageUnit> splitIntoWindows(
            byte[] allBytes, StorageUnit.FileUnit fileUnit, DeviceConfigurationHash deviceHash) {
        int total = allBytes.length;
        int offset = 0;
        int index = 0;
        List<Mono<StorageUnit>> windows = new ArrayList<>();
        while (offset < total) {
            int len = Math.min(chunkSize, total - offset);
            byte[] windowBytes = new byte[len];
            System.arraycopy(allBytes, offset, windowBytes, 0, len);
            String hex = sha256Hex(windowBytes);
            Fingerprint fingerprint = Fingerprint.of(FingerprintAlgorithm.SHA256, hex);
            int currentIndex = index++;
            windows.add(contentAddressIndex.find(deviceHash, fingerprint)
                    .map(optDescriptor -> {
                        if (optDescriptor.isPresent()) {
                            return (StorageUnit) new StorageUnit.ChunkUnit(
                                    Flux.empty(),
                                    fileUnit.info(),
                                    currentIndex,
                                    Optional.of(fingerprint),
                                    true);
                        } else {
                            Flux<DataBuffer> chunkData = Flux.just(
                                    BUF_FACTORY.wrap(windowBytes));
                            return new StorageUnit.ChunkUnit(
                                    chunkData,
                                    fileUnit.info(),
                                    currentIndex,
                                    Optional.of(fingerprint),
                                    false);
                        }
                    }));
            offset += len;
        }
        return Flux.fromIterable(windows).concatMap(Function.identity(), 1);
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
