package com.example.magrathea.storageengine.domain.valueobject;

import com.example.magrathea.storageengine.domain.TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainCollectionImmutabilityTest {

    @Test
    void objectMetadataDescriptorDefensivelyCopiesEntries() {
        Map<String, String> entries = new HashMap<>();
        entries.put("content-type", "text/plain");

        ObjectMetadataDescriptor descriptor = ObjectMetadataDescriptor.of(entries);
        entries.put("content-type", "application/octet-stream");

        assertEquals("text/plain", descriptor.entries().get("content-type"));
        assertThrows(UnsupportedOperationException.class, () -> descriptor.entries().put("x", "y"));
    }

    @Test
    void persistencePlanDefensivelyCopiesSteps() {
        List<StepPlan> steps = orderedStepPlans();
        PersistencePlan plan = new PersistencePlan(
            TestFixtures.aMinimalEffectivePolicy(),
            TestFixtures.aBucketDevice(),
            Optional.of(WorkflowCompatibilityKey.from(TestFixtures.aMinimalEffectivePolicy())),
            TestFixtures.aBucketDevice().configurationHash(),
            steps);

        steps.clear();

        assertEquals(6, plan.steps().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.steps().add(stepPlan(StepId.STORE)));
    }

    @Test
    void stepOutcomesDefensivelyCopyNodeLists() {
        List<NodeId> dataNodes = new ArrayList<>(List.of(NodeId.of("node-a"), NodeId.of("node-b")));
        List<NodeId> parityNodes = new ArrayList<>(List.of(NodeId.of("node-c")));
        StepOutcome.ErasureCodingOutcome erasure = new StepOutcome.ErasureCodingOutcome(2, 1, dataNodes, parityNodes);

        dataNodes.add(NodeId.of("node-late"));
        parityNodes.add(NodeId.of("node-late-parity"));

        assertEquals(2, erasure.dataNodes().size());
        assertEquals(1, erasure.parityNodes().size());
        assertThrows(UnsupportedOperationException.class, () -> erasure.dataNodes().add(NodeId.of("node-extra")));
        assertThrows(UnsupportedOperationException.class, () -> erasure.parityNodes().add(NodeId.of("node-extra")));

        List<NodeId> replicas = new ArrayList<>(List.of(NodeId.of("node-r1")));
        StepOutcome.ReplicationOutcome replication = new StepOutcome.ReplicationOutcome(1, replicas);
        replicas.add(NodeId.of("node-r2"));

        assertEquals(1, replication.nodes().size());
        assertThrows(UnsupportedOperationException.class, () -> replication.nodes().add(NodeId.of("node-r3")));
    }

    @Test
    void chunkPersistenceTraceDefensivelyCopiesSteps() {
        List<StepExecutionRecord> steps = orderedExecutionRecords();
        ChunkPersistenceTrace trace = new ChunkPersistenceTrace(
            ChunkId.generate(),
            Fingerprint.of(FingerprintAlgorithm.SHA256, "fingerprint-1"),
            1024L,
            steps);

        steps.clear();

        assertEquals(6, trace.steps().size());
        assertThrows(UnsupportedOperationException.class, () -> trace.steps().add(executedRecord(StepId.STORE)));
    }

    @Test
    void optionalCollectionFieldsDefensivelyCopyContainedLists() {
        List<PartDescriptor> parts = new ArrayList<>(List.of(PartDescriptor.of(1, 128L, Optional.empty())));
        CompleteUploadCommand command = new CompleteUploadCommand(
            TestFixtures.aDefaultUploadRequestContext(TestFixtures.aBucketRef(), 128L),
            UploadMode.MULTIPART,
            Optional.of(parts));

        parts.add(PartDescriptor.of(2, 256L, Optional.empty()));

        assertEquals(1, command.parts().orElseThrow().size());
        assertThrows(UnsupportedOperationException.class,
            () -> command.parts().orElseThrow().add(PartDescriptor.of(3, 512L, Optional.empty())));

        List<PartChecksumResult> results = new ArrayList<>(List.of(partChecksumResult(1)));
        UploadCompletionTrace trace = new UploadCompletionTrace(
            UploadMode.MULTIPART,
            Optional.empty(),
            ContentHash.of(ChecksumAlgorithm.SHA256, "object-hash"),
            true,
            128L,
            true,
            Optional.of(results));

        results.add(partChecksumResult(2));

        assertEquals(1, trace.partChecksumResults().orElseThrow().size());
        assertThrows(UnsupportedOperationException.class,
            () -> trace.partChecksumResults().orElseThrow().add(partChecksumResult(3)));
    }

    private static List<StepPlan> orderedStepPlans() {
        return new ArrayList<>(List.of(
            stepPlan(StepId.DEDUP),
            stepPlan(StepId.COMPRESS),
            stepPlan(StepId.CRYPT),
            stepPlan(StepId.ERASURE_CODING),
            stepPlan(StepId.REPLICATION),
            stepPlan(StepId.STORE)));
    }

    private static StepPlan stepPlan(StepId stepId) {
        return StepPlan.of(stepId, StepExecutionStatus.SKIPPED, Optional.empty(), false, false, false, false);
    }

    private static List<StepExecutionRecord> orderedExecutionRecords() {
        return new ArrayList<>(List.of(
            executedRecord(StepId.DEDUP),
            executedRecord(StepId.COMPRESS),
            executedRecord(StepId.CRYPT),
            executedRecord(StepId.ERASURE_CODING),
            executedRecord(StepId.REPLICATION),
            executedRecord(StepId.STORE)));
    }

    private static StepExecutionRecord executedRecord(StepId stepId) {
        ContentHash hash = ContentHash.of(ChecksumAlgorithm.SHA256, "hash-" + stepId.name().toLowerCase());
        return new StepExecutionRecord(
            stepId,
            StepExecutionStatus.EXECUTED,
            Optional.of(new StepOutcome.StoreOutcome(TestFixtures.aBucketDevice(), List.of(NodeId.of("node-1")), 128L)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(hash),
            Optional.of(hash));
    }

    private static PartChecksumResult partChecksumResult(int partNumber) {
        return PartChecksumResult.of(
            partNumber,
            128L,
            Optional.empty(),
            ContentHash.of(ChecksumAlgorithm.SHA256, "part-hash-" + partNumber),
            true);
    }
}
