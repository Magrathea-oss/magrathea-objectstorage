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
        int artifactCount,
        long totalOriginalSize,
        long totalStoredSize,
        List<StorageArtifactReferenceDescriptor> artifacts) {

    public ObjectManifest {
        java.util.Objects.requireNonNull(manifestId, "manifestId must not be null");
        java.util.Objects.requireNonNull(objectId, "objectId must not be null");
        java.util.Objects.requireNonNull(versionId, "versionId must not be null");
        java.util.Objects.requireNonNull(storageClassId, "storageClassId must not be null");
        java.util.Objects.requireNonNull(targetDevice, "targetDevice must not be null");
        java.util.Objects.requireNonNull(deviceHash, "deviceHash must not be null");
        java.util.Objects.requireNonNull(uploadTrace, "uploadTrace must not be null");
        java.util.Objects.requireNonNull(policyDecisions, "policyDecisions must not be null");
        if (artifactCount < 0) {
            throw new IllegalArgumentException("artifactCount must be >= 0: " + artifactCount);
        }
        if (totalOriginalSize < 0) {
            throw new IllegalArgumentException("totalOriginalSize must be >= 0: " + totalOriginalSize);
        }
        if (totalStoredSize < 0) {
            throw new IllegalArgumentException("totalStoredSize must be >= 0: " + totalStoredSize);
        }
        java.util.Objects.requireNonNull(artifacts, "artifacts must not be null");
        if (artifactCount != artifacts.size()) {
            throw new IllegalArgumentException(
                    "artifactCount (" + artifactCount + ") must equal artifacts.size() (" + artifacts.size() + ")");
        }
        policyDecisions = List.copyOf(policyDecisions);
        artifacts = List.copyOf(artifacts);
    }

    /** Compatibility alias for schema 0/1 callers. */
    public int chunkCount() {
        return artifactCount;
    }

    /** Compatibility alias for code migrating from the schema 0/1 chunk-only model. */
    public List<StorageArtifactReferenceDescriptor> chunks() {
        return artifacts;
    }
}
