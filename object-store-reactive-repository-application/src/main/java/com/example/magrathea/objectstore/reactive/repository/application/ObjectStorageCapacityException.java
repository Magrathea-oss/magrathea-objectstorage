package com.example.magrathea.objectstore.reactive.repository.application;

/** Adapter-neutral capacity failure exposed by an object repository. */
public final class ObjectStorageCapacityException extends RuntimeException {
    public enum Kind { QUOTA, BACKEND }

    private final Kind kind;

    public ObjectStorageCapacityException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
