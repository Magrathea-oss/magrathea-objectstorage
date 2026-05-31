package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record UploadRequestContext(
        ObjectKey objectKey,
        BucketRef bucket,
        StorageClassId storageClassId,
        ObjectContentDescriptor contentDescriptor,
        ObjectMetadataDescriptor metadata,
        EncryptionRequest encryptionRequest,
        Optional<DeclaredChecksum> declaredChecksum) {

    public UploadRequestContext {
        java.util.Objects.requireNonNull(objectKey, "objectKey must not be null");
        java.util.Objects.requireNonNull(bucket, "bucket must not be null");
        java.util.Objects.requireNonNull(storageClassId, "storageClassId must not be null");
        java.util.Objects.requireNonNull(contentDescriptor, "contentDescriptor must not be null");
        java.util.Objects.requireNonNull(metadata, "metadata must not be null");
        java.util.Objects.requireNonNull(encryptionRequest, "encryptionRequest must not be null");
        java.util.Objects.requireNonNull(declaredChecksum, "declaredChecksum must not be null");
    }

    public static UploadRequestContext of(
            ObjectKey objectKey,
            BucketRef bucket,
            StorageClassId storageClassId,
            ObjectContentDescriptor contentDescriptor,
            ObjectMetadataDescriptor metadata,
            EncryptionRequest encryptionRequest,
            Optional<DeclaredChecksum> declaredChecksum) {
        return new UploadRequestContext(
                objectKey, bucket, storageClassId, contentDescriptor, metadata,
                encryptionRequest, declaredChecksum);
    }
}
