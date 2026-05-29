package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;

public class S3ObjectNotFoundException extends RuntimeException {
    private final ObjectKey objectKey;

    public S3ObjectNotFoundException(ObjectKey objectKey) {
        super("S3Object not found: " + objectKey);
        this.objectKey = objectKey;
    }

    public ObjectKey objectKey() { return objectKey; }
}
