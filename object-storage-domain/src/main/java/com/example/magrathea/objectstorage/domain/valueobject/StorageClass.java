package com.example.magrathea.objectstorage.domain.valueobject;

/**
 * AWS S3 Storage Class — value object.
 * Java 17+ record — immutable, transparent.
 * Reflects actual AWS S3 storage classes.
 */
public record StorageClass(String name, String description, long minStorageDurationDays, double pricePerGBMonth) {

    public static final StorageClass STANDARD = new StorageClass("STANDARD", "Standard", 0, 0.025);
    public static final StorageClass STANDARD_IA = new StorageClass("STANDARD_IA", "Infrequent Access", 30, 0.0125);
    public static final StorageClass ONEZONE_IA = new StorageClass("ONEZONE_IA", "One Zone IA", 30, 0.01);
    public static final StorageClass GLACIER = new StorageClass("GLACIER", "Glacier", 90, 0.004);
    public static final StorageClass GLACIER_DEEP_ARCHIVE = new StorageClass("GLACIER_DEEP_ARCHIVE", "Glacier Deep Archive", 180, 0.001);
    public static final StorageClass INTELLIGENT_TIERING = new StorageClass("INTELLIGENT_TIERING", "Intelligent Tiering", 0, 0.025);
}
