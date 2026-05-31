package com.example.magrathea.storageengine.domain.valueobject;

public record NodeId(String value) {
    public NodeId {
        java.util.Objects.requireNonNull(value, "NodeId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("NodeId value must not be blank");
        }
    }

    public static NodeId of(String value) {
        return new NodeId(value);
    }
}
