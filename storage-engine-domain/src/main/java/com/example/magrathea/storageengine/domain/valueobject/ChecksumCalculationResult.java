package com.example.magrathea.storageengine.domain.valueobject;

public record ChecksumCalculationResult(ContentHash calculatedChecksum) {
    public ChecksumCalculationResult {
        java.util.Objects.requireNonNull(calculatedChecksum, "calculatedChecksum must not be null");
    }

    public static ChecksumCalculationResult of(ContentHash calculatedChecksum) {
        return new ChecksumCalculationResult(calculatedChecksum);
    }
}
