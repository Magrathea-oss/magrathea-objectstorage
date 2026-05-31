package com.example.magrathea.storageengine.domain.valueobject;

import java.util.Optional;

public record PartChecksumResult(
        int partNumber,
        long partSize,
        Optional<DeclaredChecksum> declaredChecksum,
        ContentHash calculatedChecksum,
        boolean matched) {

    public PartChecksumResult {
        if (partNumber <= 0) {
            throw new IllegalArgumentException("partNumber must be positive: " + partNumber);
        }
        if (partSize < 0) {
            throw new IllegalArgumentException("partSize must be >= 0: " + partSize);
        }
        java.util.Objects.requireNonNull(declaredChecksum, "declaredChecksum must not be null");
        java.util.Objects.requireNonNull(calculatedChecksum, "calculatedChecksum must not be null");
    }

    public static PartChecksumResult of(
            int partNumber,
            long partSize,
            Optional<DeclaredChecksum> declaredChecksum,
            ContentHash calculatedChecksum,
            boolean matched) {
        return new PartChecksumResult(partNumber, partSize, declaredChecksum, calculatedChecksum, matched);
    }
}
