package com.example.magrathea.storageengine.application.pipeline;

public enum StorageEventType {
    STAGE_STARTED,
    STAGE_SUCCEEDED,
    STAGE_FAILED,
    STAGE_CANCELLED,
    CLEANUP_COMPLETED,
    RECOVERY_SCAN_COMPLETED,
    RECOVERY_ARTIFACT_QUARANTINED
}
