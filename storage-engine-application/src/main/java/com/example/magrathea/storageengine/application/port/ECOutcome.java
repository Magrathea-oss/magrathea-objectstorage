package com.example.magrathea.storageengine.application.port;

import com.example.magrathea.storageengine.domain.valueobject.NodeId;

import java.util.List;

/**
 * Outcome of erasure coding: data nodes, parity nodes, and the encoded data block.
 */
public record ECOutcome(
        List<NodeId> dataNodes,
        List<NodeId> parityNodes,
        byte[] encodedData) {

    public ECOutcome {
        java.util.Objects.requireNonNull(dataNodes, "dataNodes must not be null");
        java.util.Objects.requireNonNull(parityNodes, "parityNodes must not be null");
        java.util.Objects.requireNonNull(encodedData, "encodedData must not be null");
    }
}
