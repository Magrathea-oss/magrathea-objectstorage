package com.example.magrathea.storageengine.domain.valueobject;

public record DeviceConfigurationHash(String value) {
    public DeviceConfigurationHash {
        java.util.Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static DeviceConfigurationHash of(String value) {
        return new DeviceConfigurationHash(value);
    }
}
