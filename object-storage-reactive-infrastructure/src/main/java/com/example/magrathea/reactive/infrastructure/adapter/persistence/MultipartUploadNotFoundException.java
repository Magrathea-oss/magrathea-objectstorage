package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstorage.domain.valueobject.UploadId;

public class MultipartUploadNotFoundException extends RuntimeException {
    private final UploadId uploadId;

    public MultipartUploadNotFoundException(UploadId uploadId) {
        super("MultipartUpload not found: " + uploadId.value());
        this.uploadId = uploadId;
    }

    public UploadId uploadId() { return uploadId; }
}
