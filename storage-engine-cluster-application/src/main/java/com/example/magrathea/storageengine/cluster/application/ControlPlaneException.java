package com.example.magrathea.storageengine.cluster.application;

/** Explicit transport-neutral control-plane failure. */
public final class ControlPlaneException extends RuntimeException {
    public enum Code {
        IDENTITY_CONFLICT, INVALID_MEMBERSHIP, INSUFFICIENT_DURABLE_ACKNOWLEDGEMENTS,
        INVALID_ACKNOWLEDGEMENT, STALE_GENERATION, STALE_TOPOLOGY_EPOCH,
        STALE_POLICY_EPOCH, QUORUM_UNAVAILABLE, NOT_FOUND, INTERNAL_FAILURE
    }

    private final Code code;

    public ControlPlaneException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ControlPlaneException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
