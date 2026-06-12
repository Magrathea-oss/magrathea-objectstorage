package com.example.magrathea.reactive.infrastructure.adapter.persistence;

import com.example.magrathea.objectstore.domain.valueobject.ObjectKey;

/**
 * @deprecated Use {@link com.example.magrathea.objectstore.reactive.repository.application.S3ObjectNotFoundException} instead.
 * This class will be removed when object-store-reactive-application no longer depends on object-store-reactive-infrastructure.
 */
@Deprecated
public class S3ObjectNotFoundException extends RuntimeException {
    private final ObjectKey objectKey;

    public S3ObjectNotFoundException(ObjectKey objectKey) {
        super("S3Object not found: " + objectKey);
        this.objectKey = objectKey;
    }

    public ObjectKey objectKey() { return objectKey; }
}
