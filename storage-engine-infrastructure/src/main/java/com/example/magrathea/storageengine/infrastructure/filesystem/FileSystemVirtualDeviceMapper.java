package com.example.magrathea.storageengine.infrastructure.filesystem;

import com.example.magrathea.storageengine.domain.valueobject.VirtualDevice;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Maps VirtualDevice instances to filesystem paths.
 * <p>
 * BucketDevice -> devices/bucket/{bucketId}
 * DedupDevice -> devices/dedup/{configurationHash}
 */
public class FileSystemVirtualDeviceMapper {

    private final Path devicesRoot;

    public FileSystemVirtualDeviceMapper(Path devicesRoot) {
        this.devicesRoot = java.util.Objects.requireNonNull(devicesRoot, "devicesRoot must not be null");
        try {
            Files.createDirectories(devicesRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create devices directory: " + devicesRoot, e);
        }
    }

    /**
     * Resolves the filesystem path for a given VirtualDevice.
     */
    public Path resolveDevicePath(VirtualDevice device) {
        if (device instanceof VirtualDevice.BucketDevice bd) {
            return devicesRoot.resolve("bucket")
                    .resolve(bd.bucketRef().bucketId().value());
        } else if (device instanceof VirtualDevice.DedupDevice dd) {
            return devicesRoot.resolve("dedup")
                    .resolve(dd.configurationHash().value());
        } else {
            throw new IllegalArgumentException("Unknown VirtualDevice type: " + device.getClass());
        }
    }
}
