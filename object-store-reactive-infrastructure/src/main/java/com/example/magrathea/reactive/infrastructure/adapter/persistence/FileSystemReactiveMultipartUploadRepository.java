package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.aggregate.Bucket;
import com.example.magrathea.objectstore.domain.aggregate.MultipartUpload;
import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;
import com.example.magrathea.objectstore.domain.valueobject.PartNumber;
import com.example.magrathea.objectstore.domain.valueobject.UploadId;
import com.example.magrathea.objectstore.domain.valueobject.UploadPart;
import com.example.magrathea.objectstore.reactive.repository.application.CommandResult;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadCommandRepository;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadNotFoundException;
import com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadQueryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Filesystem-backed multipart upload repository for the {@code filesystem-multipart} profile.
 * Stores each {@link MultipartUpload} as a JSON file under
 * {@code ${storage.root:./data}/multipart/{uploadId}.json}.
 * Provides restart-safe durability for REQ-S3-002-C.
 */
@Repository
@Profile("filesystem-multipart")
public class FileSystemReactiveMultipartUploadRepository
        implements MultipartUploadCommandRepository, MultipartUploadQueryRepository {

    private final Path multipartDir;
    private final AtomicLong versionCounter = new AtomicLong(1);

    public FileSystemReactiveMultipartUploadRepository(
            @Value("${storage.root:./data}") String storageRoot) {
        this.multipartDir = Path.of(storageRoot).resolve("multipart");
        try {
            Files.createDirectories(multipartDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create multipart storage directory: " + multipartDir, e);
        }
    }

    @Override
    public Mono<CommandResult<MultipartUpload>> save(MultipartUpload upload) {
        return Mono.fromCallable(() -> {
            var json = toJson(upload);
            var target = multipartDir.resolve(upload.uploadId().value() + ".json");
            var tmp = multipartDir.resolve(upload.uploadId().value() + ".json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            boolean exists = Files.exists(target.resolveSibling(target.getFileName()));
            long version = versionCounter.getAndIncrement();
            MultipartUpload clean = upload.clearEvents();
            return (CommandResult<MultipartUpload>) (version > 1
                ? new CommandResult.Updated<>(clean, upload.domainEvents(), version)
                : new CommandResult.Created<>(clean, upload.domainEvents(), version));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<CommandResult<MultipartUpload>> delete(MultipartUpload upload) {
        return Mono.fromCallable(() -> {
            var target = multipartDir.resolve(upload.uploadId().value() + ".json");
            if (!Files.deleteIfExists(target)) {
                throw new MultipartUploadNotFoundException(upload.uploadId());
            }
            long version = versionCounter.getAndIncrement();
            return (CommandResult<MultipartUpload>)
                new CommandResult.Deleted<>(upload.clearEvents(), upload.domainEvents(), version);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<MultipartUpload> findById(UploadId uploadId) {
        return Mono.fromCallable(() -> {
            var target = multipartDir.resolve(uploadId.value() + ".json");
            if (!Files.exists(target)) {
                return null;
            }
            var json = Files.readString(target, StandardCharsets.UTF_8);
            return fromJson(json);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<MultipartUpload> findByBucket(Bucket.Id bucketId) {
        return Flux.defer(() ->
            Flux.fromStream(() -> {
                try {
                    return Files.list(multipartDir).filter(p -> p.toString().endsWith(".json"));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            })
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(path -> Mono.fromCallable(() -> {
                var json = Files.readString(path, StandardCharsets.UTF_8);
                return fromJson(json);
            }).subscribeOn(Schedulers.boundedElastic()))
            .filter(u -> u.bucketId().equals(bucketId))
        );
    }

    @Override
    public Flux<UploadPart> findParts(UploadId uploadId) {
        return findById(uploadId)
            .flatMapMany(u -> Flux.fromIterable(u.parts()));
    }

    public void reset() {
        try (var stream = Files.list(multipartDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to reset multipart directory", e);
        }
        versionCounter.set(1);
    }

    // ── Simple JSON serialization (no external library needed) ──

    private static String toJson(MultipartUpload u) {
        var sb = new StringBuilder();
        sb.append("{");
        appendField(sb, "id", u.id().value()); sb.append(",");
        appendField(sb, "bucketId", u.bucketId().value()); sb.append(",");
        appendField(sb, "bucket", u.key().bucket()); sb.append(",");
        appendField(sb, "key", u.key().key()); sb.append(",");
        appendField(sb, "uploadId", u.uploadId().value()); sb.append(",");
        appendField(sb, "initiated", u.initiated().toString()); sb.append(",");
        appendFieldBool(sb, "completed", u.completed()); sb.append(",");
        appendFieldBool(sb, "aborted", u.aborted()); sb.append(",");
        sb.append("\"parts\":[");
        var parts = u.parts();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(",");
            var p = parts.get(i);
            sb.append("{");
            appendField(sb, "partNumber", String.valueOf(p.partNumber().value())); sb.append(",");
            appendField(sb, "etag", p.etag() != null ? p.etag() : ""); sb.append(",");
            appendField(sb, "size", String.valueOf(p.size())); sb.append(",");
            appendField(sb, "lastModified", p.lastModified().toString());
            sb.append("}");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String name, String value) {
        sb.append("\"").append(name).append("\":\"")
            .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
            .append("\"");
    }

    private static void appendFieldBool(StringBuilder sb, String name, boolean value) {
        sb.append("\"").append(name).append("\":").append(value);
    }

    private static MultipartUpload fromJson(String json) {
        // Simple JSON parser for our known format
        var id = extractString(json, "id");
        var bucketId = extractString(json, "bucketId");
        var bucket = extractString(json, "bucket");
        var key = extractString(json, "key");
        var uploadId = extractString(json, "uploadId");
        var initiated = extractString(json, "initiated");
        var completed = extractBool(json, "completed");
        var aborted = extractBool(json, "aborted");
        var parts = extractParts(json);

        return MultipartUpload.restore(
            MultipartUpload.Id.of(id),
            Bucket.Id.of(bucketId),
            ObjectKey.of(bucket, key),
            UploadId.of(uploadId),
            Instant.parse(initiated),
            parts,
            completed,
            aborted
        );
    }

    private static String extractString(String json, String field) {
        var marker = "\"" + field + "\":\"";
        var start = json.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        var end = json.indexOf("\"", start);
        while (end > start && json.charAt(end - 1) == '\\') {
            end = json.indexOf("\"", end + 1);
        }
        return json.substring(start, end)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    private static boolean extractBool(String json, String field) {
        var marker = "\"" + field + "\":";
        var start = json.indexOf(marker);
        if (start < 0) return false;
        start += marker.length();
        return json.startsWith("true", start);
    }

    private static List<UploadPart> extractParts(String json) {
        var partsStart = json.indexOf("\"parts\":[");
        if (partsStart < 0) return List.of();
        partsStart += "\"parts\":[".length();
        var partsEnd = json.lastIndexOf("]");
        if (partsEnd < partsStart) return List.of();
        var partsJson = json.substring(partsStart, partsEnd);

        List<UploadPart> parts = new ArrayList<>();
        var idx = 0;
        while (idx < partsJson.length()) {
            var objStart = partsJson.indexOf("{", idx);
            if (objStart < 0) break;
            var objEnd = partsJson.indexOf("}", objStart);
            if (objEnd < 0) break;
            var partJson = partsJson.substring(objStart, objEnd + 1);
            var partNumberStr = extractString(partJson, "partNumber");
            var etag = extractString(partJson, "etag");
            var sizeStr = extractString(partJson, "size");
            var lastModified = extractString(partJson, "lastModified");
            if (!partNumberStr.isEmpty()) {
                parts.add(UploadPart.of(
                    PartNumber.of(Integer.parseInt(partNumberStr)),
                    etag.isEmpty() ? null : etag,
                    sizeStr.isEmpty() ? 0L : Long.parseLong(sizeStr),
                    lastModified.isEmpty() ? Instant.now() : Instant.parse(lastModified)
                ));
            }
            idx = objEnd + 1;
        }
        return List.copyOf(parts);
    }
}
