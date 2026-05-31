package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.port.ContentAddressIndex;
import com.example.magrathea.storageengine.domain.valueobject.ChunkId;
import com.example.magrathea.storageengine.domain.valueobject.ChunkReferenceDescriptor;
import com.example.magrathea.storageengine.domain.valueobject.DeviceConfigurationHash;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Filesystem-backed content-address index.
 * Maps (deviceHash, fingerprint) -> chunkId.
 * Layout: indexRoot/{deviceHash}/{fingerprint} -> chunkId UUID string.
 */
public class FileSystemContentAddressIndex implements ContentAddressIndex {

    private final Path indexRoot;

    public FileSystemContentAddressIndex(Path indexRoot) {
        this.indexRoot = java.util.Objects.requireNonNull(indexRoot, "indexRoot must not be null");
        try {
            Files.createDirectories(indexRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create index root: " + indexRoot, e);
        }
    }

    @Override
    public Mono<Optional<ChunkReferenceDescriptor>> find(DeviceConfigurationHash deviceHash, Fingerprint fingerprint) {
        return Mono.fromCallable(() -> {
            Path entryPath = indexRoot
                    .resolve(deviceHash.value())
                    .resolve(fingerprint.value());
            if (Files.exists(entryPath)) {
                String chunkIdStr = Files.readString(entryPath).trim();
                // Return a minimal descriptor — full descriptor would require reading chunk metadata
                ChunkId chunkId = ChunkId.of(java.util.UUID.fromString(chunkIdStr));
                return Optional.of(new ChunkReferenceDescriptor(
                        chunkId, fingerprint, 0, 0,
                        java.util.List.of(),
                        null,
                        java.util.List.of()));
            }
            return Optional.empty();
        });
    }

    @Override
    public Mono<Void> record(DeviceConfigurationHash deviceHash, Fingerprint fingerprint, ChunkId chunkId) {
        return Mono.fromRunnable(() -> {
            try {
                Path deviceDir = indexRoot.resolve(deviceHash.value());
                Files.createDirectories(deviceDir);
                Path entryPath = deviceDir.resolve(fingerprint.value());
                Files.writeString(entryPath, chunkId.value().toString(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to record content address entry", e);
            }
        });
    }
}
