package com.example.magrathea.storageengine.domain.distributed;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuorumPolicyTest {

    private static final String BUCKET = "distributed-bucket";
    private static final String KEY = "datasets/2026-06/report.parquet";
    private static final String MANIFEST = "manifest-distributed-bucket-datasets-2026-06-report-parquet";
    private static final String CHECKSUM = "sha256:4c81f0bfe0c4f7c9d8f971a5de89b372176f2cdd1f6e8ee4b316f8f94b35f987";

    private final QuorumPolicy quorumPolicy = new QuorumPolicy();

    @Test
    void writeQuorumSucceedsWhenTwoOfThreeReplicasAcknowledgePersistence() {
        QuorumDecision decision = quorumPolicy.evaluateWriteQuorum(
                MANIFEST,
                List.of("node-a", "node-b", "node-c"),
                List.of("node-b", "node-a"),
                2);

        assertEquals(QuorumDecision.QUORUM_MET, decision.decision());
        assertIterableEquals(List.of("node-a", "node-b"), decision.acknowledgedNodes());
        assertIterableEquals(List.of("node-c"), decision.missingNodes());
        assertEquals(2, decision.requiredQuorum());
        assertEquals(2, decision.acknowledgementCount());
        assertTrue(decision.mayPublishManifest());
    }

    @Test
    void writeQuorumFailsWhenOnlyOneOfThreeReplicasAcknowledgesPersistence() {
        QuorumDecision decision = quorumPolicy.evaluateWriteQuorum(
                MANIFEST,
                List.of("node-a", "node-b", "node-c"),
                List.of("node-a"),
                2);

        assertEquals(QuorumDecision.QUORUM_NOT_MET, decision.decision());
        assertIterableEquals(List.of("node-a"), decision.acknowledgedNodes());
        assertIterableEquals(List.of("node-b", "node-c"), decision.missingNodes());
        assertFalse(decision.mayPublishManifest());
        assertTrue(decision.failureReason().orElseThrow().contains("required write quorum 2"));
        assertTrue(decision.failureReason().orElseThrow().contains("acknowledgement count 1"));
    }

    @Test
    void readQuorumFailsWhenOneValidReplicaAndOneCorruptedReplicaAreObserved() {
        QuorumDecision decision = quorumPolicy.evaluateReadQuorum(
                BUCKET,
                KEY,
                MANIFEST,
                CHECKSUM,
                List.of(
                        ReplicaObservation.verified("node-a", CHECKSUM),
                        ReplicaObservation.verified("node-b", "sha256:0000000000000000000000000000000000000000000000000000000000000000"),
                        ReplicaObservation.unavailable("node-c")),
                2);

        assertEquals(QuorumDecision.INTEGRITY_QUORUM_NOT_MET, decision.decision());
        assertEquals(1, decision.validReplicaCount());
        assertIterableEquals(List.of("node-b"), decision.corruptedNodes());
        assertIterableEquals(List.of("node-c"), decision.missingNodes());
        assertFalse(decision.objectBytesMayBeReturned());
        IntegrityFinding finding = decision.integrityFindings().get(0);
        assertEquals(BUCKET, finding.bucket());
        assertEquals(KEY, finding.key());
        assertEquals("node-b", finding.nodeId());
        assertEquals("checksum-mismatch", finding.findingType());
    }
}
