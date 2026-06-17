package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Objects;
import java.util.Optional;

/**
 * Common metadata carried by every StorageUnit through the processing pipeline.
 * Contains only fields that are guaranteed to be present on all unit types.
 * Type-specific fields (chunk index, fingerprint, stripe index, part number)
 * live directly on the StorageUnit subtype records.
 */
public record StorageUnitInfo(
        UploadRequestContext uploadContext,
        long contentLengthHint,              // -1 if unknown (HTTP chunked transfer, no Content-Length)
        Optional<DeviceConfigurationHash> deviceHash  // present when dedup is enabled
) {
    public StorageUnitInfo {
        Objects.requireNonNull(uploadContext, "uploadContext must not be null");
        Objects.requireNonNull(deviceHash, "deviceHash must not be null");
    }

    public static StorageUnitInfo of(UploadRequestContext ctx, long contentLengthHint) {
        return new StorageUnitInfo(ctx, contentLengthHint, Optional.empty());
    }

    public static StorageUnitInfo of(UploadRequestContext ctx) {
        return new StorageUnitInfo(ctx, -1L, Optional.empty());
    }

    public static StorageUnitInfo of(UploadRequestContext ctx, long contentLengthHint, DeviceConfigurationHash deviceHash) {
        return new StorageUnitInfo(ctx, contentLengthHint, Optional.of(deviceHash));
    }
}
