package com.example.magrathea.storageengine.application.pipeline;

import com.example.magrathea.storageengine.domain.valueobject.EcShardLayout;
import com.example.magrathea.storageengine.domain.valueobject.Fingerprint;
import com.example.magrathea.storageengine.domain.valueobject.NodeId;
import com.example.magrathea.storageengine.domain.valueobject.StorageUnitInfo;

import java.util.List;
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
    long storedSize,                      // bytes actually written (after compress/encrypt/EC)
    List<NodeId> locations,               // ordered node/device identities committed by the store adapter
    Optional<EcShardLayout> ecShardLayout // explicit for EC shards; never inferred from emission order
) {

    public StorageTrace {
        java.util.Objects.requireNonNull(info, "info must not be null");
        java.util.Objects.requireNonNull(unitKind, "unitKind must not be null");
        java.util.Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        java.util.Objects.requireNonNull(storageRef, "storageRef must not be null");
        java.util.Objects.requireNonNull(locations, "locations must not be null");
        java.util.Objects.requireNonNull(ecShardLayout, "ecShardLayout must not be null");
        locations = List.copyOf(locations);
    }

    /** Compatibility constructor for traces written before explicit EC layout propagation. */
    public StorageTrace(
            StorageUnitInfo info,
            String unitKind,
            Optional<Fingerprint> fingerprint,
            Optional<String> storageRef,
            boolean deduplicatedReuse,
            long originalSize,
            long storedSize,
            List<NodeId> locations) {
        this(info, unitKind, fingerprint, storageRef, deduplicatedReuse,
                originalSize, storedSize, locations, Optional.empty());
    }

    /** Compatibility constructor for stores that do not expose an ordered location. */
    public StorageTrace(
            StorageUnitInfo info,
            String unitKind,
            Optional<Fingerprint> fingerprint,
            Optional<String> storageRef,
            boolean deduplicatedReuse,
            long originalSize,
            long storedSize) {
        this(info, unitKind, fingerprint, storageRef, deduplicatedReuse,
                originalSize, storedSize, List.of(), Optional.empty());
    }

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
