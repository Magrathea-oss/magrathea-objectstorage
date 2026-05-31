package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record PartDescriptor(int partNumber, long partSize, Optional<DeclaredChecksum> partChecksum) {
    public PartDescriptor {
        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber must be positive: " + partNumber);
        }
        if (partSize < 0) {
            throw new IllegalArgumentException("partSize must be >= 0: " + partSize);
        }
        java.util.Objects.requireNonNull(partChecksum, "partChecksum must not be null");
    }

    public static PartDescriptor of(int partNumber, long partSize, Optional<DeclaredChecksum> partChecksum) {
        return new PartDescriptor(partNumber, partSize, partChecksum);
    }
}
