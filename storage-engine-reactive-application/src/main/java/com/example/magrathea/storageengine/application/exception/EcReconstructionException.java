package com.example.magrathea.storageengine.application.exception;

/** Fail-closed rejection raised by the bounded local EC reconstruction boundary. */
public final class EcReconstructionException extends RuntimeException {

    public enum Reason {
        UNSUPPORTED_SCHEMA,
        AMBIGUOUS_LAYOUT,
        UNSUPPORTED_GEOMETRY,
        INCONSISTENT_METADATA,
        INVALID_SURVIVOR_SET,
        INSUFFICIENT_SURVIVORS,
        INTEGRITY_MISMATCH
    }

    private final Reason reason;

    public EcReconstructionException(Reason reason, String message) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "reason must not be null");
    }

    public EcReconstructionException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = java.util.Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason reason() {
        return reason;
    }
}
