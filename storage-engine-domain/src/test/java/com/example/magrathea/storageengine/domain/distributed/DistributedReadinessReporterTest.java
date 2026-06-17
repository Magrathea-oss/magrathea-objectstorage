package com.example.magrathea.storageengine.domain.distributed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedReadinessReporterTest {

    private final DistributedReadinessReporter reporter = new DistributedReadinessReporter();

    @Test
    void singleProcessFilesystemBackendReportsSingleNodeAndMissingDistributedCapabilities() {
        DistributedReadinessReport report = reporter.defaultFilesystemReport();

        assertEquals(DistributedReadinessReport.SINGLE_NODE, report.classification());
        assertFalse(report.distributedProductionReady());
        assertFalse("distributed-production-ready".equals(report.classification()));
        assertEquals(DistributedReadinessReport.S3_OBJECT_API_BOUNDARY, report.objectApiBoundary());
        assertTrue(report.missingCapabilities().contains(DistributedReadinessReport.NETWORKED_MEMBERSHIP));
        assertTrue(report.missingCapabilities().contains(DistributedReadinessReport.REAL_REPLICATION_JOB_EXECUTION));
        assertTrue(report.missingCapabilities().contains(DistributedReadinessReport.MULTI_NODE_END_TO_END_VALIDATION));
    }

    @Test
    void modeledDistributedReportDistinguishesSimulationFromProductionReadiness() {
        DistributedReadinessReport report = reporter.report(false, false, false, true);

        assertEquals(DistributedReadinessReport.DISTRIBUTED_SIMULATION_NOT_READY, report.classification());
        assertFalse(report.distributedProductionReady());
        assertTrue(report.missingCapabilities().contains(DistributedReadinessReport.NETWORKED_MEMBERSHIP));
        assertTrue(report.objectApiBoundary().contains("S3-compatible API"));
        assertThrows(IllegalArgumentException.class, () -> new DistributedReadinessReport(
                "distributed-production-ready",
                report.missingCapabilities(),
                DistributedReadinessReport.S3_OBJECT_API_BOUNDARY));
    }
}
