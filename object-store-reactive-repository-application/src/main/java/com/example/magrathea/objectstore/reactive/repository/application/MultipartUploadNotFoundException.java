package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.valueobject.UploadId;

/**
 * Thrown when a multipart upload is not found in the repository.
 */
public class MultipartUploadNotFoundException extends RuntimeException {
    private final UploadId uploadId;

    public MultipartUploadNotFoundException(UploadId uploadId) {
        super("MultipartUpload not found: " + uploadId.value());
        this.uploadId = uploadId;
    }

    public UploadId uploadId() { return uploadId; }
}
