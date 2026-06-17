package com.example.magrathea.storageengine.infrastructure.pipeline;

import com.example.magrathea.storageengine.application.pipeline.StorePort;
import com.example.magrathea.storageengine.application.pipeline.StorageTrace;
import com.example.magrathea.storageengine.application.pipeline.StorageUnit;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.FingerprintAlgorithm;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteFaultInjector;
import com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemWriteInterruptedException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
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
 *   FileUnit     → chunksRoot / &lt;uuid&gt; (with &lt;uuid&gt;.sha256 sidecar; UUID format
 *                  is compatible with FileSystemStorageNode.read(ChunkId) which
 *                  uses chunkId.value().toString() as the filename)
 *   ChunkUnit    → chunksRoot / &lt;sha256&gt;
 *   ECStripeUnit → NOT YET IMPLEMENTED (TODO criticality 3)
 *   PartUnit     → NOT YET IMPLEMENTED (future multipart support)
 *
 * FileUnit naming convention: the file is stored as &lt;uuid&gt; (not sha256) so that the
 * existing read path via FileSystemStorageNode.read(ChunkId) can locate it using
 * chunkId.value().toString(). The SHA-256 is written to the &lt;uuid&gt;.sha256 sidecar for
 * integrity verification by FileSystemStorageNode.read(). The sha256 hex is also returned
 * in StorageTrace.fingerprint so the orchestrator can build ChunkPersistenceTrace checksums.
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
            case StorageUnit.FileUnit f          -> streamFileUnitToChunksDir(f);
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
            case StorageUnit.ECStripeUnit ignored -> Mono.error(
                new UnsupportedOperationException(
                    "ECStripeUnit storage not yet implemented — TODO criticality 3"));
            case StorageUnit.PartUnit ignored     -> Mono.error(
                new UnsupportedOperationException(
                    "PartUnit storage not yet implemented — future multipart support"));
        };
    }

    /**
     * Stores a FileUnit to chunksRoot using a freshly generated UUID as the filename.
     * Writes an integrity sidecar (&lt;uuid&gt;.sha256) in the same directory so that
     * FileSystemStorageNode.read(ChunkId) can verify the data on read.
     *
     * The protocol mirrors FileSystemStorageNode.write():
     *   1. Stream data to a temp file, computing SHA-256 incrementally.
     *   2. Write sha256 hex to a temp sidecar.
     *   3. Atomically rename sidecar first, then data file.
     *
     * Returns StorageTrace with:
     *   - storageRef = uuid.toString() (matches ChunkId.value().toString() used by read path)
     *   - fingerprint = Fingerprint(SHA256, sha256hex) so the orchestrator can build trace checksums
     */
    /**
     * Stores a FileUnit to chunksRoot using a freshly generated UUID as the filename.
     *
     * Temp-file naming uses the same {@code <uuid>.tmp.<tmpUuid>} pattern as
     * {@link com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageNode}
     * so that filesystem-inspection tests can discover partial artifacts via ".tmp." scans.
     *
     * Protocol (mirrors FileSystemStorageNode.write):
     *   1. Stream data to a temp file {@code <uuid>.tmp.<tmpUuid>}, computing SHA-256.
     *   2. Call the fault injector (throws FileSystemWriteInterruptedException if enabled).
     *   3. Write sha256 hex to temp sidecar {@code <uuid>.sha256.tmp.<tmpUuid>}.
     *   4. Atomically rename sidecar first ({@code <uuid>.sha256}), then data ({@code <uuid>}).
     *
     * Returns StorageTrace with:
     *   - storageRef  = uuid (matches ChunkId.value().toString() used by the read path)
     *   - fingerprint = Fingerprint(SHA256, sha256hex) for orchestrator checksum chain building
     */
    private Mono<StorageTrace> streamFileUnitToChunksDir(StorageUnit.FileUnit unit) {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(chunksRoot);
                    return UUID.randomUUID();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(fileId -> {
                    String idStr = fileId.toString();
                    // Use the same <id>.tmp.<uuid> naming pattern as FileSystemStorageNode
                    // so that filesystem-reliability tests can find partial artifacts.
                    String tmpSuffix = UUID.randomUUID().toString();
                    Path tmp = chunksRoot.resolve(idStr + ".tmp." + tmpSuffix);
                    Path tmpSidecar = chunksRoot.resolve(idStr + ".sha256.tmp." + tmpSuffix);
                    Path finalFile = chunksRoot.resolve(idStr);
                    Path finalSidecar = chunksRoot.resolve(idStr + ".sha256");

                    AtomicReference<MessageDigest> digest = new AtomicReference<>(sha256());
                    AtomicLong size = new AtomicLong(0);

                    Flux<DataBuffer> tracked = Flux.from(unit.data())
                        .doOnNext(buf -> {
                            size.addAndGet(buf.readableByteCount());
                            try (DataBuffer.ByteBufferIterator it = buf.readableByteBuffers()) {
                                while (it.hasNext()) {
                                    digest.get().update(it.next());
                                }
                            }
                        });

                    return DataBufferUtils.write(tracked, tmp, CREATE_NEW, WRITE)
                        .then(Mono.fromCallable(() -> {
                            String sha256Hex = HexFormat.of().formatHex(digest.get().digest());
                            long stored = Files.size(tmp);

                            // Fault injection hook — matches FileSystemStorageNode.write() timing
                            // (after temp file is written, before atomic rename).
                            faultInjector.afterChunkTempFileWritten(
                                new FileSystemWriteFaultInjector.ChunkWriteContext(
                                    NodeId.of("node-001"),
                                    ChunkId.of(fileId),
                                    tmp, finalFile, stored));

                            // Write sidecar first (same protocol as FileSystemStorageNode.write)
                            Files.write(tmpSidecar, sha256Hex.getBytes(StandardCharsets.UTF_8), CREATE_NEW);
                            Files.move(tmpSidecar, finalSidecar, ATOMIC_MOVE, REPLACE_EXISTING);
                            // Then move data file
                            Files.move(tmp, finalFile, ATOMIC_MOVE, REPLACE_EXISTING);

                            return new StorageTrace(
                                unit.info(), "file",
                                Optional.of(Fingerprint.of(FingerprintAlgorithm.SHA256, sha256Hex)),
                                Optional.of(idStr),
                                false,
                                size.get(), stored);
                        }).subscribeOn(Schedulers.boundedElastic()))
                        .onErrorResume(e -> {
                            // If the fault injector signalled "preserve temporary artifacts",
                            // leave the partial temp data file on disk (the test verifies it
                            // remains). Always clean up the sidecar temp (may not exist).
                            boolean preserve = e instanceof FileSystemWriteInterruptedException fi
                                    && fi.preserveTemporaryArtifacts();
                            return Mono.fromRunnable(() -> {
                                if (!preserve) {
                                    try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
                                }
                                try { Files.deleteIfExists(tmpSidecar); } catch (IOException ignored) {}
                            }).then(Mono.error(e));
                        });
                });
    }

    private Mono<StorageTrace> streamToStore(StorageUnit unit, Path root, String kind) {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(root);
                    return root.resolve(".tmp-" + UUID.randomUUID());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(tmp -> {
                    AtomicReference<MessageDigest> digest = new AtomicReference<>(sha256());
                    AtomicLong size = new AtomicLong(0);

                    // doOnNext peeks at each buffer to feed the digest.
                    // readableByteBuffers() returns a ByteBufferIterator view that does
                    // not advance the DataBuffer's readPosition, so DataBufferUtils.write()
                    // still sees the full content. Both asByteBuffer() and toByteBuffer()
                    // are deprecated since Spring 6.0.5; ByteBufferIterator is the
                    // recommended non-deprecated replacement.
                    Flux<DataBuffer> tracked = Flux.from(unit.data())
                        .doOnNext(buf -> {
                            size.addAndGet(buf.readableByteCount());
                            try (DataBuffer.ByteBufferIterator it = buf.readableByteBuffers()) {
                                while (it.hasNext()) {
                                    digest.get().update(it.next());
                                }
                            }
                        });

                    return DataBufferUtils.write(tracked, tmp, CREATE_NEW, WRITE)
                        .then(Mono.fromCallable(() -> {
                            String ref = HexFormat.of().formatHex(digest.get().digest());
                            long stored = Files.size(tmp);
                            Files.move(tmp, root.resolve(ref), ATOMIC_MOVE, REPLACE_EXISTING);
                            return new StorageTrace(
                                unit.info(), kind,
                                Optional.empty(),   // fingerprint filled by caller for ChunkUnit
                                Optional.of(ref),
                                false,
                                size.get(), stored);
                        }).subscribeOn(Schedulers.boundedElastic()))
                        .onErrorResume(e -> Mono.fromRunnable(() -> {
                            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
                        }).then(Mono.error(e)));
                });
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
