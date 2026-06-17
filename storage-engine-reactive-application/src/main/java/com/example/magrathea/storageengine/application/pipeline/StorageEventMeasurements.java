package com.example.magrathea.storageengine.application.pipeline;

/**
 * Numeric, non-sensitive measurements attached to storage events for metrics and tracing.
 */
public record StorageEventMeasurements(
        long bytesWritten,
        long bytesRead,
        long chunks,
        long manifests,
        long failures,
        long recoveryFindings,
        long recoveryQuarantines,
        long dedupHits,
        long dedupMisses) {

    private static final StorageEventMeasurements EMPTY = new StorageEventMeasurements(0, 0, 0, 0, 0, 0, 0, 0, 0);

    public StorageEventMeasurements {
        requireNonNegative(bytesWritten, "bytesWritten");
        requireNonNegative(bytesRead, "bytesRead");
        requireNonNegative(chunks, "chunks");
        requireNonNegative(manifests, "manifests");
        requireNonNegative(failures, "failures");
        requireNonNegative(recoveryFindings, "recoveryFindings");
        requireNonNegative(recoveryQuarantines, "recoveryQuarantines");
        requireNonNegative(dedupHits, "dedupHits");
        requireNonNegative(dedupMisses, "dedupMisses");
    }

    public static StorageEventMeasurements empty() {
        return EMPTY;
    }

    public static StorageEventMeasurements failure() {
        return new StorageEventMeasurements(0, 0, 0, 0, 1, 0, 0, 0, 0);
    }

    public static StorageEventMeasurements writeChunkStage(
            long bytesWritten,
            long chunks,
            long dedupHits,
            long dedupMisses) {
        return new StorageEventMeasurements(bytesWritten, 0, chunks, 0, 0, 0, 0, dedupHits, dedupMisses);
    }

    public static StorageEventMeasurements readChunkStage(long bytesRead, long chunks) {
        return new StorageEventMeasurements(0, bytesRead, chunks, 0, 0, 0, 0, 0, 0);
    }

    public static StorageEventMeasurements manifestStage(long manifests) {
        return new StorageEventMeasurements(0, 0, 0, manifests, 0, 0, 0, 0, 0);
    }

    public static StorageEventMeasurements recoveryScan(long findings) {
        return new StorageEventMeasurements(0, 0, 0, 0, 0, findings, 0, 0, 0);
    }

    public static StorageEventMeasurements recoveryQuarantine(long quarantines) {
        return new StorageEventMeasurements(0, 0, 0, 0, 0, 0, quarantines, 0, 0);
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0: " + value);
        }
    }
}
