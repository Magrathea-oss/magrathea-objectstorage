package com.example.magrathea.cluster.control.ratis.reqcluster027;

import com.example.magrathea.storageengine.cluster.application.ReferencePage;
import com.example.magrathea.storageengine.cluster.application.RepairJob;
import com.example.magrathea.storageengine.cluster.application.RepairState;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Exact Story BDD glue for REQ-CLUSTER-027 periodic current-replica discovery. */
public final class ReqCluster027Steps {
    private static final String NON_CORE_STEP =
            "^((?!the focused periodic anti-entropy gate executes bounded cycles$)"
                    + "(?!current consensus contains these ).*)$";

    private ReqCluster027Harness harness;
    private ReqCluster027Harness.Evidence evidence;
    private boolean recoveryScenario;

    @After("@REQ-CLUSTER-027")
    public void closeHarness() {
        if (harness != null) harness.close();
    }

    @Given(NON_CORE_STEP)
    public void requirementStep(String text) {
        if (evidence == null) {
            require(text != null && !text.isBlank(), "blank REQ-CLUSTER-027 precondition");
            if (text.startsWith("a process-local exclusive cursor")) recoveryScenario = true;
            return;
        }
        assertOutcome(text);
    }

    @Given("current consensus contains these {string} ObjectReferenceGeneration records "
            + "in canonical bucket, object-key, and generation order:")
    public void documentedCurrentReferences(String representation, DataTable references) {
        require("WHOLE_OBJECT".equals(representation) && references.height() == 5,
                "REQ-CLUSTER-027 reference fixture changed unexpectedly");
        List<String> keys = references.asMaps().stream().map(row -> row.get("key")).toList();
        require(keys.equals(keys.stream().sorted().toList()),
                "reference fixture is not in canonical object-key order");
    }

    @When("the focused periodic anti-entropy gate executes bounded cycles")
    public void executeBoundedCycles() throws Exception {
        harness = new ReqCluster027Harness();
        evidence = recoveryScenario ? harness.runRecoveryAndRaces()
                : harness.runSuccessfulDiscovery();
    }

    private void assertOutcome(String text) {
        if (text.startsWith("Ratis exposes only current")) {
            require(!evidence.pages().isEmpty() && evidence.pages().stream()
                    .flatMap(page -> page.references().stream())
                    .allMatch(reference -> reference.generation() > 0),
                    "Ratis did not expose current typed references");
            require(evidence.pages().stream().allMatch(page -> page.references().equals(
                    page.references().stream().sorted(Comparator.comparing(
                            reference -> reference.namespaceKey())).toList())),
                    "Ratis page was not in canonical namespace order");
        } else if (text.startsWith("each non-terminal page")) {
            require(evidence.pages().stream().filter(page -> !page.terminal()).allMatch(page ->
                            page.nextExclusiveCursor().equals(
                                    com.example.magrathea.storageengine.cluster.application
                                            .ReferencePageQuery.Cursor.after(
                                                    page.references().get(page.references().size() - 1)))),
                    "exclusive cursor did not identify the last record");
        } else if (text.startsWith("invalid page limits and cursors")) {
            require(evidence.codecFailClosed(),
                    "Ratis page query or codec accepted invalid or oversized input");
        } else if (text.startsWith("no page exceeds limit 2")) {
            require(evidence.pages().stream().allMatch(page -> page.references().size() <= 2)
                            && evidence.maximumPageActive() == 1
                            && evidence.status().overlaps() == 0,
                    "page or target processing was unbounded or overlapping");
        } else if (text.startsWith("periodic cycles on fixed nodes")) {
            require(evidence.fixedNodesInspected(),
                    "fixed A/B/C production schedulers did not inspect named local references");
        } else if (text.startsWith("B never probes or repairs")) {
            require(evidence.otherUnprobed()
                            && !evidence.ensures().containsKey(ReqCluster027Harness.OTHER),
                    "B touched a reference that does not name B");
        } else if (text.startsWith("each local named target is probed")) {
            require(evidence.status().exactTargets() > 0
                            && evidence.status().missingTargets() > 0
                            && evidence.status().corruptTargets() > 0,
                    "committed length/SHA-256 probing did not classify every target state");
        } else if (text.startsWith("every local filesystem probe")) {
            require(evidence.probesOnBoundedElastic(),
                    "a filesystem probe escaped Reactor boundedElastic");
        } else if (text.startsWith("closing B's process-local discovery scheduler")) {
            require(evidence.activeCloseCancelled(),
                    "closing discovery did not cancel its active delayed page cycle");
        } else if (text.startsWith("the missing and corrupt B obligations")) {
            require(canonicalJobs(Set.of(ReqCluster027Harness.CORRUPT,
                    ReqCluster027Harness.MISSING)) == 2,
                    "missing and corrupt targets did not produce two canonical jobs");
        } else if (text.startsWith("duplicate observations do not create")) {
            require(evidence.jobs().stream().allMatch(job -> job.history().stream()
                            .filter(entry -> entry.event().equals("ENSURE")).count() == 1),
                    "duplicate discovery created another repair transition");
        } else if (text.startsWith("existing exact-current claim")) {
            require(evidence.rpcReads().getOrDefault(ReqCluster027Harness.CORRUPT, 0L) > 0
                            && evidence.rpcReads().getOrDefault(
                                    ReqCluster027Harness.MISSING, 0L) > 0
                            && evidence.jobs().stream().filter(job -> Set.of(
                                    ReqCluster027Harness.CORRUPT,
                                    ReqCluster027Harness.MISSING).contains(
                                            job.specification().artifactId()))
                            .allMatch(job -> job.state() == RepairState.SUCCEEDED),
                    "existing fenced worker did not repair through grpc-java mTLS");
        } else if (text.startsWith("the repaired B targets")) {
            require(evidence.exactTargets()
                            && !evidence.ensures().containsKey(ReqCluster027Harness.EXACT)
                            && !evidence.rpcReads().containsKey(ReqCluster027Harness.EXACT),
                    "exact target caused repair work or repaired targets are invalid");
        } else if (text.startsWith("the process-local cursor advances")) {
            require(evidence.status().completedCycles() < evidence.status().cycles()
                            && evidence.status().retries() > 0,
                    "failed pages incorrectly advanced without a retry");
        } else if (text.startsWith("a query, probe, or ensure failure")) {
            require(evidence.queryFailure() && evidence.probeFailure()
                            && evidence.ensureFailure() && evidence.failedCursorRetried()
                            && evidence.status().lastFailure() != null,
                    "query/probe/ensure failures were not retained as bounded status");
        } else if (text.startsWith("a fenced repair execution failure")) {
            require(evidence.repairFailure() && evidence.jobs().stream().anyMatch(job ->
                            job.history().stream().anyMatch(entry ->
                                    entry.toState() == RepairState.RETRY_WAIT)),
                    "repair execution failure did not remain committed retryable state");
        } else if (text.startsWith("after a terminal page")) {
            require(evidence.status().cursorResets() > 0,
                    "terminal page did not reset the next cycle");
        } else if (text.startsWith("after B's process-local discovery")) {
            require(evidence.restartFromFirst() && evidence.rebuiltOnePage(),
                    "reconstructed B schedulers trusted a prior process cursor");
        } else if (text.startsWith("repeated first-page discovery")) {
            require(evidence.rebuiltOnePage(),
                    "restarted first-page discovery did not deduplicate");
        } else if (text.startsWith("the distinct generation 7 work")) {
            require(evidence.staleBeforeEnsure() && evidence.oldObsolete()
                            && evidence.publicationBoundaryRejected()
                            && evidence.oldBytesAbsentAtBoundary(),
                    "a generation 7 race bypassed the ensure or publication fence");
        } else if (text.startsWith("a later cycle discovers both generation 8")) {
            require(evidence.exactTargets() && evidence.jobs().stream()
                            .filter(job -> Set.of(ReqCluster027Harness.ENSURE_RACE8,
                                    ReqCluster027Harness.PUBLICATION_RACE8)
                                    .contains(job.specification().artifactId()))
                            .filter(job -> job.state() == RepairState.SUCCEEDED)
                            .count() == 2,
                    "later cycles did not repair both current generation 8 obligations");
        } else if (text.startsWith("after B's process-local schedulers are closed")) {
            require(evidence.schedulersClosed(),
                    "closed B schedulers retained active process-local work");
        } else if (text.startsWith("inspection exposes cycle")) {
            require(evidence.status().cycles() > 0 && evidence.status().pages() > 0
                            && evidence.status().records() > 0
                            && evidence.status().queryFailures() > 0
                            && evidence.status().probeFailures() > 0
                            && evidence.status().ensureFailures() > 0
                            && evidence.status().repairFailures() > 0,
                    "message-free inspection counters are incomplete");
        } else if (text.startsWith("PA-6 AntiEntropyPlanner")) {
            require(evidence.status().ensures() > 0
                            && evidence.status().pages() > 0,
                    "production evidence was not derived from actual discovery and ensure");
        } else if (text.startsWith("this bounded gate excludes")) {
            require(evidence.status().overlaps() == 0,
                    "focused gate escaped its bounded scope");
        } else {
            throw new AssertionError("unverified REQ-CLUSTER-027 outcome: " + text);
        }
    }

    private long canonicalJobs(Set<String> artifacts) {
        return evidence.jobs().stream().map(RepairJob::specification)
                .filter(specification -> artifacts.contains(specification.artifactId()))
                .map(specification -> specification.jobId().value()).distinct().count();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
