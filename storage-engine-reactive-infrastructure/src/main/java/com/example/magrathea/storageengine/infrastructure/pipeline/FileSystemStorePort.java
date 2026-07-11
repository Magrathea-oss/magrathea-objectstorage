package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.pipeline.StorePort;
import com.example.magrathea.storageengine.application.pipeline.StorageTrace;
import com.example.magrathea.storageengine.application.pipeline.StorageUnit;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.infrastructure.filesystem.AtomicChunkWriteProtocol;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteFaultInjector;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteInterruptedException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Infrastructure implementation of StorePort using the filesystem.
 *
 * Streams Flux<DataBuffer> directly to disk without materialising the full object in memory.
 * Computes SHA-256 incrementally via doOnNext while DataBufferUtils.write() streams the
 * same buffers. DataBuffer.asByteBuffer() returns a read-only view — does not advance
 * the DataBuffer read position, so both the digest and the write see the full bytes.
 *
 * After streaming, atomically renames temp → final path named by SHA-256.
 *
 * Unit type dispatch (single instanceof point in the pipeline):
 *   FileUnit     → directObjectsRoot / &lt;uuid&gt; (with &lt;uuid&gt;.sha256 sidecar)
 *   ChunkUnit    → chunksRoot / &lt;uuid&gt; (with &lt;uuid&gt;.sha256 sidecar)
 *   ECStripeUnit → NOT YET IMPLEMENTED (TODO criticality 3)
 *   PartUnit     → NOT YET IMPLEMENTED (future multipart support)
 *
 * FileUnit naming convention: the file is stored as &lt;uuid&gt; in the dedicated
 * whole-object namespace. The schema-2 manifest labels it {@code WHOLE_OBJECT}; typed
 * reads use that kind to select the namespace. The SHA-256 sidecar preserves the same
 * atomic publication and integrity protocol used by segmented artifacts.
 *
 * TODO (criticality 3): ECStripeUnit and PartUnit not yet implemented.
 * TODO (criticality 4): Temp-file cleanup on mid-pipeline failure is per-call only
 *   (handled by onErrorResume below). Cross-cutting cleanup port not yet added.
 */
public class FileSystemStorePort implements StorePort {

    private final Path directObjectsRoot;
    private final Path chunksRoot;
    private final FileSystemWriteFaultInjector faultInjector;

    /** No-fault-injection constructor (unit tests and non-reliability integration paths). */
    public FileSystemStorePort(Path directObjectsRoot, Path chunksRoot) {
        this(directObjectsRoot, chunksRoot, FileSystemWriteFaultInjector.disabled());
    }

    /** Full constructor for production wiring with fault injection support. */
    public FileSystemStorePort(
            Path directObjectsRoot,
            Path chunksRoot,
            FileSystemWriteFaultInjector faultInjector) {
        this.directObjectsRoot = directObjectsRoot;
        this.chunksRoot = chunksRoot;
        this.faultInjector = java.util.Objects.requireNonNull(faultInjector,
                "faultInjector must not be null");
    }

    @Override
    public Mono<StorageTrace> write(StorageUnit unit) {
        return switch (unit) {
            case StorageUnit.FileUnit f          -> streamFileUnitToWholeObjectsDir(f);
            case StorageUnit.ChunkUnit c         -> {
                // Deduplicated chunks carry the deduplicatedReuse flag set by DeduplicationStep.
                // When true, no data is written — we emit a reuse trace directly.
                if (c.deduplicatedReuse()) {
                    Fingerprint fp = c.fingerprint()
                            .orElseThrow(() -> new IllegalStateException(
                                    "ChunkUnit with deduplicatedReuse must have a fingerprint"));
                    yield Mono.just(new StorageTrace(
                            c.info(), "chunk",
                            Optional.of(fp),
                            Optional.empty(),
                            true,
                            0L,
                            0L));
                }
                // Non-empty chunk: stream to store
                yield streamToStore(c, chunksRoot, "chunk")
                        .map(t -> new StorageTrace(
                            t.info(), t.unitKind(),
                            c.fingerprint(),
                            t.storageRef(),
                            t.deduplicatedReuse(),
                            t.originalSize(), t.storedSize()));
            }
            case StorageUnit.ECStripeUnit stripe -> streamToStore(stripe, chunksRoot, "ec-stripe");
            case StorageUnit.ECShardUnit shard    -> streamToStore(
                    shard, chunksRoot, shard.parity() ? "ec-parity-shard" : "ec-data-shard")
                    .map(trace -> new StorageTrace(
                            trace.info(), trace.unitKind(), trace.fingerprint(), trace.storageRef(),
                            false, shard.logicalSize(), trace.storedSize()));
            case StorageUnit.PartUnit ignored     -> Mono.error(
                new UnsupportedOperationException(
                    "PartUnit storage not yet implemented — future multipart support"));
        };
    }

    /** Stores a FileUnit in the dedicated whole-object namespace. */
    private Mono<StorageTrace> streamFileUnitToWholeObjectsDir(StorageUnit.FileUnit unit) {
        return streamToStore(unit, directObjectsRoot, "whole-object");
    }

    /**
     * Streams one unit into the shared atomic chunk protocol. Both FileUnit and ChunkUnit
     * therefore use UUID references, forced temp data/checksum files, sidecar-first atomic
     * renames, and the same error/cancellation cleanup behavior as FileSystemStorageNode.
     */
    private Mono<StorageTrace> streamToStore(StorageUnit unit, Path root, String kind) {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(root);
                    return AtomicChunkWriteProtocol.prepare(root, ChunkId.generate());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(pending -> {
                    AtomicReference<MessageDigest> digest = new AtomicReference<>(sha256());
                    AtomicLong size = new AtomicLong(0);
                    AtomicBoolean cancelled = new AtomicBoolean();

                    Flux<DataBuffer> tracked = Flux.from(unit.data())
                            .doOnNext(buffer -> {
                                size.addAndGet(buffer.readableByteCount());
                                try (DataBuffer.ByteBufferIterator iterator = buffer.readableByteBuffers()) {
                                    while (iterator.hasNext()) {
                                        digest.get().update(iterator.next());
                                    }
                                }
                            });

                    Mono<StorageTrace> write = DataBufferUtils.write(
                                    tracked, pending.tempData(), CREATE_NEW, WRITE)
                            .then(Mono.fromCallable(() -> {
                                AtomicChunkWriteProtocol.force(pending.tempData());
                                long stored = Files.size(pending.tempData());
                                faultInjector.afterChunkTempFileWritten(
                                        new FileSystemWriteFaultInjector.ChunkWriteContext(
                                                NodeId.of("node-001"), pending.chunkId(),
                                                pending.tempData(), pending.finalData(), stored));
                                String checksum = HexFormat.of().formatHex(digest.get().digest());
                                if (cancelled.get()) {
                                    cleanupCancelledWrite(pending);
                                    throw new CancellationException("chunk write cancelled before commit");
                                }
                                AtomicChunkWriteProtocol.commit(pending, checksum);
                                if (cancelled.get()) {
                                    cleanupCancelledWrite(pending);
                                    throw new CancellationException("chunk write cancelled during commit");
                                }
                                return new StorageTrace(
                                        unit.info(), kind,
                                        Optional.of(Fingerprint.of(FingerprintAlgorithm.SHA256, checksum)),
                                        Optional.of(pending.chunkId().value().toString()),
                                        false,
                                        size.get(), stored);
                            }).subscribeOn(Schedulers.boundedElastic()))
                            .onErrorResume(error -> {
                                boolean preserve = error instanceof FileSystemWriteInterruptedException interrupted
                                        && interrupted.preserveTemporaryArtifacts();
                                return Mono.fromRunnable(() ->
                                                AtomicChunkWriteProtocol.cleanupUncommitted(pending, preserve))
                                        .then(Mono.error(error));
                            });

                    return write.doFinally(signal -> {
                        if (signal == SignalType.CANCEL) {
                            cancelled.set(true);
                            cleanupCancelledWrite(pending);
                        }
                    });
                });
    }

    private static void cleanupCancelledWrite(AtomicChunkWriteProtocol.PendingWrite pending) {
        AtomicChunkWriteProtocol.cleanupUncommitted(pending, false);
        try {
            Files.deleteIfExists(pending.finalChecksum());
            Files.deleteIfExists(pending.finalData());
        } catch (java.io.IOException ignored) {
            // Cancellation cleanup is best-effort here; pipeline cleanup retries by chunk id.
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
