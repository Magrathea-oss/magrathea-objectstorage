package com.example.magrathea.objectstore.reactive.repository.application;

import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;

/**
 * Thrown when an S3 object is not found in the repository.
 */
public class S3ObjectNotFoundException extends RuntimeException {
    private final ObjectKey objectKey;

    public S3ObjectNotFoundException(ObjectKey objectKey) {
        super("S3Object not found: " + objectKey);
        this.objectKey = objectKey;
    }

    public ObjectKey objectKey() { return objectKey; }
}
