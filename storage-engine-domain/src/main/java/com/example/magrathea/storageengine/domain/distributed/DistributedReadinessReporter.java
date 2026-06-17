package com.example.magrathea.storageengine.domain.distributed;

import java.util.ArrayList;
import java.util.List;

/** Pure reporter that intentionally never reports distributed-production-ready. */
public class DistributedReadinessReporter {

    public DistributedReadinessReport defaultFilesystemReport() {
        return new DistributedReadinessReport(
                DistributedReadinessReport.SINGLE_NODE,
                defaultMissingCapabilities(),
                DistributedReadinessReport.S3_OBJECT_API_BOUNDARY);
    }

    public DistributedReadinessReport simulatedDistributedReport(boolean topologyModeled) {
        return new DistributedReadinessReport(
                topologyModeled
                        ? DistributedReadinessReport.DISTRIBUTED_SIMULATION
                        : DistributedReadinessReport.DISTRIBUTED_SIMULATION_NOT_READY,
                defaultMissingCapabilities(),
                DistributedReadinessReport.S3_OBJECT_API_BOUNDARY);
    }

    public DistributedReadinessReport report(
            boolean networkedMembershipValidated,
            boolean realReplicationJobExecutionValidated,
            boolean multiNodeEndToEndValidated,
            boolean simulatedDistributedModelAvailable) {
        List<String> missing = new ArrayList<>();
        if (!networkedMembershipValidated) {
            missing.add(DistributedReadinessReport.NETWORKED_MEMBERSHIP);
        }
        if (!realReplicationJobExecutionValidated) {
            missing.add(DistributedReadinessReport.REAL_REPLICATION_JOB_EXECUTION);
        }
        if (!multiNodeEndToEndValidated) {
            missing.add(DistributedReadinessReport.MULTI_NODE_END_TO_END_VALIDATION);
        }
        String classification = simulatedDistributedModelAvailable
                ? DistributedReadinessReport.DISTRIBUTED_SIMULATION
                : DistributedReadinessReport.SINGLE_NODE;
        if (!missing.isEmpty() && simulatedDistributedModelAvailable) {
            classification = DistributedReadinessReport.DISTRIBUTED_SIMULATION_NOT_READY;
        }
        return new DistributedReadinessReport(
                classification,
                missing,
                DistributedReadinessReport.S3_OBJECT_API_BOUNDARY);
    }

    private static List<String> defaultMissingCapabilities() {
        return List.of(
                DistributedReadinessReport.NETWORKED_MEMBERSHIP,
                DistributedReadinessReport.REAL_REPLICATION_JOB_EXECUTION,
                DistributedReadinessReport.MULTI_NODE_END_TO_END_VALIDATION);
    }
}
