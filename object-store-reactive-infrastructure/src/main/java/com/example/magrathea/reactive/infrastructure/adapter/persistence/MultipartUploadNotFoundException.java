package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.valueobject.UploadId;

/**
 * @deprecated Use {@link com.example.magrathea.objectstore.reactive.repository.application.MultipartUploadNotFoundException} instead.
 * This class will be removed when object-store-reactive-application no longer depends on object-store-reactive-infrastructure.
 */
@Deprecated
public class MultipartUploadNotFoundException extends RuntimeException {
    private final UploadId uploadId;

    public MultipartUploadNotFoundException(UploadId uploadId) {
        super("MultipartUpload not found: " + uploadId.value());
        this.uploadId = uploadId;
    }

    public UploadId uploadId() { return uploadId; }
}
