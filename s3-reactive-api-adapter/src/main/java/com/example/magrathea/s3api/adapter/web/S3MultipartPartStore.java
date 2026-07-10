package com.example.magrathea.s3api.adapter.web;

import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Filesystem-backed store for uploaded multipart part bodies.
 *
 * <p>The multipart aggregate tracks S3-visible part metadata (part number, ETag,
 * size, last-modified). This adapter-local store keeps the corresponding part
 * bytes under the configured storage root so CompleteMultipartUpload can assemble
 * and persist the final S3 object through the normal object service.</p>
 */
public class S3MultipartPartStore {

    private static final DefaultDataBufferFactory DATA_BUFFER_FACTORY = new DefaultDataBufferFactory();
    private static final int READ_BUFFER_SIZE = 64 * 1024;

    private final Path root;

    public S3MultipartPartStore(Path root) {
        this.root = root;
    }

    public Mono<StoredPart> savePart(UploadId uploadId, PartNumber partNumber, Flux<DataBuffer> content) {
        return Mono.defer(() -> {
            var digest = md5();
            var size = new AtomicLong();
            Path uploadDir = uploadDirectory(uploadId);
            Path target = partPath(uploadId, partNumber);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            var measuredContent = content.doOnNext(buffer -> {
                size.addAndGet(buffer.readableByteCount());
                digest.update(buffer.toByteBuffer().asReadOnlyBuffer());
            });

            return Mono.fromRunnable(() -> prepareTarget(uploadDir, tmp))
                .subscribeOn(Schedulers.boundedElastic())
                .then(DataBufferUtils.write(measuredContent, tmp,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
                .then(Mono.fromCallable(() -> {
                    moveAtomically(tmp, target);
                    return new StoredPart("\"" + ETagComputer.toHex(digest.digest()) + "\"", size.get());
                }).subscribeOn(Schedulers.boundedElastic()))
                .doOnError(error -> deleteIfExists(tmp))
                .doFinally(signal -> {
                    if (signal == SignalType.CANCEL) {
                        deleteIfExists(tmp);
                    }
                });
        });
    }

    public Flux<DataBuffer> readPart(UploadId uploadId, PartNumber partNumber) {
        return DataBufferUtils.read(partPath(uploadId, partNumber), DATA_BUFFER_FACTORY, READ_BUFFER_SIZE);
    }

    public Mono<Void> deleteUpload(UploadId uploadId) {
        return Mono.fromRunnable(() -> deleteRecursively(uploadDirectory(uploadId)))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    public void reset() {
        deleteRecursively(root);
    }

    private Path uploadDirectory(UploadId uploadId) {
        return root.resolve(safe(uploadId.value()));
    }

    private Path partPath(UploadId uploadId, PartNumber partNumber) {
        return uploadDirectory(uploadId).resolve("part-" + partNumber.value() + ".bin");
    }

    private static MessageDigest md5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }

    private static void prepareTarget(Path uploadDir, Path tmp) {
        try {
            Files.createDirectories(uploadDir);
            Files.deleteIfExists(tmp);
            Files.createFile(tmp);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare multipart part body target", e);
        }
    }

    private static void moveAtomically(Path tmp, Path target) {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to commit multipart part body", e);
        }
    }

    private static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup for a failed write.
        }
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void deleteRecursively(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            try (var walk = Files.walk(path)) {
                for (Path current : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(current);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete multipart part store " + path, e);
        }
    }

    public record StoredPart(String etag, long size) {
    }
}
