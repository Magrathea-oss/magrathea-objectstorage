package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstorage.domain.aggregate.S3Object;

public class S3ObjectNotFoundException extends RuntimeException {
    private final S3Object.Id objectId;

    public S3ObjectNotFoundException(S3Object.Id objectId) {
        super("S3Object not found: " + objectId.value());
        this.objectId = objectId;
    }

    public S3Object.Id objectId() { return objectId; }
}
