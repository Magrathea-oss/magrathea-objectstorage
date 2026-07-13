package com.example.magrathea.storageengine.cluster.application;

/** Stable application error vocabulary for direct replica transfer. */
public enum TransferError {
    CANCELLED, DEADLINE_EXCEEDED, OFFSET_MISMATCH, LENGTH_MISMATCH, CHECKSUM_MISMATCH,
    FRAME_TOO_LARGE, IDENTITY_MISMATCH, UNTRUSTED_PEER, ARTIFACT_CONFLICT, IO_FAILURE, PROTOCOL_ERROR
}
