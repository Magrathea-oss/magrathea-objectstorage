package com.example.magrathea.storageengine.application.pipeline;

import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.StorageUnitInfo;

import java.util.Optional;

/**
 * Result of writing one StorageUnit to durable storage via StorePort.
 * Carries the information needed by the orchestrator to build the object manifest.
 */
public record StorageTrace(
    StorageUnitInfo info,
    String unitKind,                      // "file" | "chunk" | "ec-stripe" | "part"
    Optional<Fingerprint> fingerprint,    // present for ChunkUnit after dedup
    Optional<String> storageRef,          // opaque id of the stored bytes (e.g. chunk file name)
    boolean deduplicatedReuse,            // true if an existing content reference was reused
    long originalSize,                    // bytes before any transform
    long storedSize                       // bytes actually written (after compress/encrypt/EC)
) {

    public static String kindOf(StorageUnit unit) {
        return switch (unit) {
            case StorageUnit.FileUnit ignored     -> "file";
            case StorageUnit.ChunkUnit ignored    -> "chunk";
            case StorageUnit.ECStripeUnit ignored -> "ec-stripe";
            case StorageUnit.ECShardUnit shard    -> shard.parity()
                    ? "ec-parity-shard" : "ec-data-shard";
            case StorageUnit.PartUnit ignored     -> "part";
        };
    }
}
