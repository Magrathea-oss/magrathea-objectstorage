package com.example.magrathea.bootstrap;

import java.util.Objects;

/**
 * Runtime-visible object-store backend selection status.
 */
public record ObjectStoreBackendStatus(Backend backend) {

    public ObjectStoreBackendStatus {
        Objects.requireNonNull(backend, "backend must not be null");
    }

    public enum Backend {
        IN_MEMORY("in-memory"),
        STORAGE_ENGINE("storage-engine");

        private final String propertyValue;

        Backend(String propertyValue) {
            this.propertyValue = propertyValue;
        }

        public String propertyValue() {
            return propertyValue;
        }

        public static Backend fromProperty(String value) {
            if (value == null || value.isBlank()) {
                return IN_MEMORY;
            }
            String normalized = value.trim().toLowerCase();
            return switch (normalized) {
                case "in-memory" -> IN_MEMORY;
                case "storage-engine" -> STORAGE_ENGINE;
                default -> throw new IllegalArgumentException(
                    "Unsupported magrathea.object-store.backend value '" + value
                        + "'. Accepted values: in-memory, storage-engine");
            };
        }
    }
}
