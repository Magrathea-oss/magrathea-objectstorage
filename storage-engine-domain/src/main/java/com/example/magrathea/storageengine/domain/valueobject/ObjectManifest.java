package com.example.magrathea.storageengine.domain.valueobject;

import java.util.List;

public record ObjectManifest(
        ManifestId manifestId,
        ObjectId objectId,
        VersionId versionId,
        StorageClassId storageClassId,
        VirtualDevice targetDevice,
        DeviceConfigurationHash deviceHash,
        UploadCompletionTrace uploadTrace,
        List<PolicyDecision> policyDecisions,
        int chunkCount,
        long totalOriginalSize,
        long totalStoredSize,
        List<ChunkReferenceDescriptor> chunks) {

    public ObjectManifest {
        java.util.Objects.requireNonNull(manifestId, "manifestId must not be null");
        java.util.Objects.requireNonNull(objectId, "objectId must not be null");
        java.util.Objects.requireNonNull(versionId, "versionId must not be null");
        java.util.Objects.requireNonNull(storageClassId, "storageClassId must not be null");
        java.util.Objects.requireNonNull(targetDevice, "targetDevice must not be null");
        java.util.Objects.requireNonNull(deviceHash, "deviceHash must not be null");
        java.util.Objects.requireNonNull(uploadTrace, "uploadTrace must not be null");
        java.util.Objects.requireNonNull(policyDecisions, "policyDecisions must not be null");
        if (chunkCount < 0) {
            throw new IllegalArgumentException("chunkCount must be >= 0: " + chunkCount);
        }
        if (totalOriginalSize < 0) {
            throw new IllegalArgumentException("totalOriginalSize must be >= 0: " + totalOriginalSize);
        }
        if (totalStoredSize < 0) {
            throw new IllegalArgumentException("totalStoredSize must be >= 0: " + totalStoredSize);
        }
        java.util.Objects.requireNonNull(chunks, "chunks must not be null");
    }
}
