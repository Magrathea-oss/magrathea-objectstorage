package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.application.exception.StorageCapacityException;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Conservatively translates native filesystem exhaustion into the typed capacity contract. */
public final class FileSystemCapacityErrors {
    private FileSystemCapacityErrors() { }

    public static RuntimeException translate(IOException error, Path storageRoot, long requestedBytes, String fallbackMessage) {
        String details = ((error.getMessage() == null ? "" : error.getMessage()) + " "
                + (error instanceof java.nio.file.FileSystemException fs && fs.getReason() != null
                    ? fs.getReason() : "")).toLowerCase(Locale.ROOT);
        if (details.contains("no space left on device")
                || details.contains("disk quota exceeded")
                || details.contains("not enough space")) {
            return new StorageCapacityException(
                    "storage-engine", storageRoot, requestedBytes, availableBytes(storageRoot));
        }
        return new java.io.UncheckedIOException(fallbackMessage, error);
    }

    private static long availableBytes(Path storageRoot) {
        try {
            Path existing = storageRoot;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }
            if (existing == null) {
                return -1;
            }
            FileStore store = Files.getFileStore(existing);
            return store.getUsableSpace();
        } catch (IOException ignored) {
            return -1;
        }
    }
}
