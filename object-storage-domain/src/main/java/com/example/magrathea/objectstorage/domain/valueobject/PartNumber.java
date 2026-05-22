package com.example.magrathea.objectstorage.domain.valueobject;

/**
 * AWS S3 Part number — value object.
 * Part numbers range from 1 to 10000 (inclusive).
 * Immutable, zero framework dependencies.
 */
public record PartNumber(int value) {

    public PartNumber {
        if (value < 1 || value > 10000)
            throw new IllegalArgumentException("Part number must be between 1 and 10000");
    }

    public static PartNumber of(int value) {
        return new PartNumber(value);
    }
}
