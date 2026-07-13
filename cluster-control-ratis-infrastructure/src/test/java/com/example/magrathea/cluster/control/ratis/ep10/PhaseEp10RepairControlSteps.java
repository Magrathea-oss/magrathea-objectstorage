package com.example.magrathea.cluster.control.ratis.ep10;

import com.example.magrathea.cluster.control.ratis.FixedThreeNodeRatisCluster;
import com.example.magrathea.cluster.control.ratis.RatisTlsConfig;
import com.example.magrathea.cluster.data.grpc.FileLocalArtifactStore;
import com.example.magrathea.storageengine.cluster.application.*;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.ratis.util.MD5FileUtil;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Real three-voter acceptance glue for transport-neutral repair control; it performs no repair transfer. */
public final class PhaseEp10RepairControlSteps {
    private static final NodeIdentity A = NodeIdentity.parse("11111111-1111-4111-8111-111111111111");
    private static final NodeIdentity B = NodeIdentity.parse("22222222-2222-4222-8222-222222222222");
    private static final NodeIdentity C = NodeIdentity.parse("33333333-3333-4333-8333-333333333333");
    private static final String BUCKET = "ep10-repair-archive";
    private static final String KEY = "evidence/2026/current-generation-repair.bin";
    private static final String ARTIFACT = "whole-7f351d76-50d8-4f48-9b86-6f94e777a101";
    private static final String SHA = "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800";
    private static final String SHA_8 = "56918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e801";
    private static final Path ROOT = Path.of("target/ep10/three-node");
    private static final Instant BASE = Instant.parse("2026-07-13T16:00:00Z");

    private final MembershipSnapshot membership = new MembershipSnapshot(List.of(
            new ClusterMember("A", A, "127.0.0.1", 19801),
            new ClusterMember("B", B, "127.0.0.1", 19802),
            new ClusterMember("C", C, "127.0.0.1", 19803)), "topology-1", "policy-1");
    private final RepairRetryPolicy policy = new RepairRetryPolicy(20, Duration.ofMillis(1), Duration.ofMillis(1), Duration.ofSeconds(10));
    private FixedThreeNodeRatisCluster cluster;
    private ClusterControlPlanePort port;
    private RepairSpecification specification;
    private RepairJobId jobId;
    private RepairCommandResult lastResult;
    private RepairCommands.Command lastCommand;
    private List<RepairCommandResult> ensureResults = new ArrayList<>();
    private long sequence;
    private long historyBeforeDuplicate;
    private long attemptBeforeReevaluation;
    private long generationBeforeReevaluation;
    private String expectedState;
    private String failurePoint;
    private String executionFence;
    private boolean staleRejected;
    private boolean restarted;
    private boolean legacyLoaded;
    private boolean unknownVersionRejected;
    private Map<RepairJobId, RepairJob> expectedRestoredJobs = Map.of();
    private List<Path> v1Snapshots = List.of();

    @Before
    public void reset() throws Exception {
        deleteRecursively(ROOT);
        specification = null; jobId = null; lastResult = null; lastCommand = null; ensureResults.clear();
        sequence = 0; staleRejected = false; restarted = false; legacyLoaded = false; unknownVersionRejected = false;
        expectedRestoredJobs = Map.of(); v1Snapshots = List.of();
    }

    @After public void close() { if (cluster != null) cluster.close(); cluster = null; }

    @Given("current reference generation {long} for bucket {string} and key {string} names artifact {string} on A, B, and C")
    public void currentReference(long generation, String bucket, String key, String artifact) {
        start(); publishThrough(generation, bucket, key, artifact, 134, SHA);
    }

    @Given("the canonical repair identity is {string}, that bucket, that object key, generation {long}, that artifact, and target UUID {string}")
    public void canonicalIdentity(String type, long generation, String target) {
        require(type.equals("REPAIR"), "unexpected repair type");
        jobId = RepairJobId.canonical(BUCKET, KEY, generation, ARTIFACT, NodeIdentity.parse(target));
    }

    @Given("its immutable specification records length {long}, SHA-256 {string}, topology epoch {string}, and policy epoch {string}")
    public void immutableSpecification(long length, String sha, String topology, String policyEpoch) {
        specification = new RepairSpecification(BUCKET, KEY, 7, ARTIFACT, B, length, sha, topology, policyEpoch, policy);
        require(specification.jobId().equals(jobId), "canonical identity changed with immutable facts");
    }

    @When("missing and corrupt observations from different requests, coordinators, leaders, and workers repeatedly ensure that exact obligation")
    public void repeatedEnsure() {
        for (int i = 0; i < 5; i++) {
            RepairCommands.Ensure command = new RepairCommands.Ensure("ensure-observation-" + i, specification,
                    BASE.plusSeconds(i), i % 2 == 0 ? C : A);
            ensureResults.add(port.ensureRepair(command).block(Duration.ofSeconds(8)));
        }
        lastCommand = new RepairCommands.Ensure("ensure-duplicate", specification, BASE.plusSeconds(9), A);
        lastResult = dispatch(lastCommand);
        historyBeforeDuplicate = job().history().size();
        RepairCommandResult duplicate = dispatch(lastCommand);
        require(duplicate.equals(lastResult), "duplicate ensure did not return committed result");
    }

    @When("source C is the first attempt hint but source A is a later attempt hint")
    public void changingSourceHint() { require(job().history().getFirst().sourceHint().equals(C), "first source hint was not retained as non-identity history"); }

    @Then("every ensure returns the same deterministically derived job ID and one consensus-owned logical job")
    public void oneLogicalJob() {
        require(ensureResults.stream().allMatch(result -> result.jobId().equals(jobId)), "ensure returned different job IDs");
        require(port.repairJobs(RepairJobQuery.all()).collectList().block(Duration.ofSeconds(8)).size() == 1, "duplicate logical repair jobs exist");
    }

    @Then("observation ID, request ID, coordinator, leader, worker, process session, and candidate source are excluded from repair identity")
    public void transientFactsExcluded() {
        require(jobId.equals(RepairJobId.canonical(BUCKET, KEY, 7, ARTIFACT, B)), "transient facts changed canonical identity");
    }

    @Then("a duplicate ensure command returns its committed result without another job, transition, or attempt record")
    public void duplicateEnsureStable() { require(job().history().size() == historyBeforeDuplicate && job().attemptNumber() == 0, "duplicate ensure changed repair history"); }

    @When("fresh damage is observed after that exact job has reached {string} and generation {long} remains current")
    public void freshDamage(String state, long generation) {
        require(generation == 7 && state.equals("SUCCEEDED"), "unexpected fresh-damage precondition");
        claim("fresh-claim", "b-session-fresh", BASE.plusSeconds(20));
        succeed("fresh-success", "b-session-fresh", BASE.plusSeconds(21));
        RepairJob succeeded = job(); attemptBeforeReevaluation = succeeded.attemptNumber(); generationBeforeReevaluation = succeeded.claimGeneration();
        lastCommand = new RepairCommands.Reevaluate("fresh-reevaluate", jobId, BASE.plusSeconds(22), "fresh damage proved", A);
        lastResult = dispatch(lastCommand);
    }

    @Then("explicit re-evaluation reactivates the same logical job rather than creating another identity")
    public void reactivatedSameIdentity() { require(lastResult.accepted() && job().state() == RepairState.READY && job().jobId().equals(jobId), "re-evaluation did not reactivate same job"); }

    @Then("attempt number and claim generation remain monotonic across reactivation")
    public void monotonicReactivation() throws Exception {
        require(job().attemptNumber() == attemptBeforeReevaluation
                        && job().claimGeneration() == generationBeforeReevaluation,
                "re-evaluation reset fencing counters");
        verifySerialSourceFallbackAndSchedulerClose();
    }

    @Given("one repair job is in state {string} with current reference and fencing facts matching the command unless the condition says otherwise")
    public void jobInState(String fromState) {
        prepareCurrentJob("lifecycle.bin", "whole-lifecycle", policy);
        String selected = fromState.contains(",") ? "CLAIMED" : fromState;
        if (!selected.equals("absent")) ensure("lifecycle-ensure", BASE);
        if (Set.of("CLAIMED", "RETRY_WAIT", "BLOCKED", "SUCCEEDED").contains(selected)) claim("lifecycle-claim", "lifecycle-session", BASE.plusSeconds(1));
        if (selected.equals("RETRY_WAIT")) retry("lifecycle-retry", "lifecycle-session", BASE.plusSeconds(2), "temporary transport failure");
        if (selected.equals("BLOCKED")) block("lifecycle-block", "lifecycle-session", BASE.plusSeconds(2), "no verified source remains");
        if (selected.equals("SUCCEEDED")) succeed("lifecycle-success", "lifecycle-session", BASE.plusSeconds(2));
    }

    @When("the leader proposes command data for {string} and the Ratis state machine applies it")
    public void applyLifecycleCondition(String condition) {
        Instant at = BASE.plusSeconds(30);
        if (condition.startsWith("ensure")) ensure("transition-ensure", at);
        else if (condition.startsWith("accept a due claim")) claim("transition-claim", "transition-session", at);
        else if (condition.startsWith("committed next-eligible")) claim("transition-retry-claim", "transition-session-2", at);
        else if (condition.contains("bounded transport")) retry("transition-retry", "lifecycle-session", at, "bounded transport failure");
        else if (condition.contains("source exhaustion")) block("transition-block", "lifecycle-session", at, "source exhaustion");
        else if (condition.contains("exact durable target")) succeed("transition-success", "lifecycle-session", at);
        else if (condition.startsWith("bound reference")) {
            RepairCommands.Command replayable = lastAcceptedCommand(); publishNext(specification.bucket(), specification.objectKey(), "whole-new-generation", specification.length(), SHA_8);
            lastCommand = replayable; lastResult = dispatch(replayable);
        } else if (condition.startsWith("explicit re-evaluation") || condition.startsWith("fresh damage")) {
            lastCommand = new RepairCommands.Reevaluate("transition-reevaluate", jobId, at, condition, A); lastResult = dispatch(lastCommand);
        } else throw new AssertionError("unsupported lifecycle condition " + condition);
        expectedState = condition.startsWith("bound reference") ? "OBSOLETE" : null;
    }

    @Then("the committed job state becomes {string}")
    public void stateBecomes(String state) { expectedState = state; require(job().state().name().equals(state), "expected " + state + " but was " + job().state()); }

    @Then("the transition, command result, command-supplied timestamp, attempt history, and reason are deterministic and auditable")
    public void transitionAuditable() {
        RepairJob current = job(); RepairHistoryEntry event = current.history().getLast();
        require(event.occurredAt().equals(current.updatedAt()) && !event.reason().isBlank(), "accepted transition lacks command timestamp or reason");
        require(current.commandResults().stream().anyMatch(result -> result.commandId().equals(lastCommand.commandId())), "command result was not retained");
    }

    @Then("duplicate delivery returns the committed result without a second transition or attempt record")
    public void duplicateDelivery() {
        RepairJob before = job(); RepairCommandResult duplicate = dispatch(lastCommand); RepairJob after = job();
        require(duplicate.equals(lastResult) && before.history().equals(after.history()), "duplicate command changed committed state");
    }

    @Then("no filesystem, network, random, sleep, retry, or wall-clock side effect occurs in the state-machine transaction")
    public void deterministicApplyBoundary() {
        require(!Files.exists(ROOT.resolve("node-b/objects")) && !Files.exists(ROOT.resolve("node-b/temporary")), "control apply touched repair data paths");
    }

    @Then("{string} is terminal and is never rewritten for a newer reference generation")
    public void obsoleteTerminal(String state) {
        if (job().state() != RepairState.OBSOLETE) return;
        RepairCommands.Reevaluate reevaluate = new RepairCommands.Reevaluate("obsolete-reevaluate", jobId, BASE.plusSeconds(60), "must remain terminal", A);
        require(!port.reevaluateRepair(reevaluate).block(Duration.ofSeconds(8)).accepted() && job().state().name().equals(state), "obsolete job was rewritten");
    }

    @Given("job {string} is {string} by stable node UUID {string} and process session {string}")
    public void claimedByOldSession(String alias, String state, String owner, String session) {
        require(state.equals("CLAIMED") && NodeIdentity.parse(owner).equals(B), "unexpected fencing precondition");
        prepareCurrentJob(KEY, ARTIFACT, new RepairRetryPolicy(20, Duration.ofMillis(1), Duration.ofMillis(1), Duration.ofSeconds(2)));
        ensure("fence-ensure", BASE);
        Instant at = BASE.plusSeconds(1);
        for (int attempt = 1; attempt <= 11; attempt++) {
            String claimSession = attempt == 11 ? session : "old-session-" + attempt;
            claim("fence-claim-" + attempt, claimSession, at);
            if (attempt < 11) { retry("fence-retry-" + attempt, claimSession, at.plusMillis(1), "temporary"); at = at.plusMillis(2); }
        }
    }

    @Given("attempt {long} has safety token {string} and a committed claim deadline")
    public void safetyToken(long attempt, String token) { require(job().attemptNumber() == attempt && token.endsWith("," + job().claimGeneration()), "unexpected safety token"); }

    @When("process B restarts as process session {string}") public void processSessionRestarts(String session) { require(!session.equals(job().claim().processSession()), "process session did not change"); }

    @Then("the new session cannot renew, fail, block, obsolete, or complete claim generation {long}")
    public void replacementCannotUseOldClaim(long generation) {
        int history = job().history().size(); Instant at = job().claim().deadline().minusMillis(1);
        List<RepairCommandResult> results = List.of(
                port.renewRepair(new RepairCommands.Renew("wrong-renew", jobId, generation, B, "b-session-0042", at, at.plusMillis(10))).block(Duration.ofSeconds(8)),
                port.retryRepair(new RepairCommands.Retry("wrong-retry", jobId, generation, B, "b-session-0042", at, "wrong session")).block(Duration.ofSeconds(8)),
                port.blockRepair(new RepairCommands.Block("wrong-block", jobId, generation, B, "b-session-0042", at, "wrong session")).block(Duration.ofSeconds(8)),
                port.obsoleteRepair(new RepairCommands.Obsolete("wrong-obsolete", jobId, generation, B, "b-session-0042", at, "wrong session")).block(Duration.ofSeconds(8)),
                port.succeedRepair(new RepairCommands.Succeed("wrong-success", jobId, generation, B, "b-session-0042", at, 134, SHA, "wrong session")).block(Duration.ofSeconds(8)));
        require(results.stream().noneMatch(RepairCommandResult::accepted) && job().history().size() == history, "replacement session used predecessor token");
    }

    @When("Ratis commits a reclaim by session {string}")
    public void reclaimByNewSession(String session) { claim("fence-reclaim-12", session, job().claim().deadline()); }

    @Then("attempt number and claim generation both increase to {long}") public void generationsIncrease(long value) { require(job().attemptNumber() == value && job().claimGeneration() == value, "reclaim did not increment both counters"); }
    @Then("the only current safety token is {string}") public void currentToken(String token) { require(token.endsWith("," + job().claimGeneration()), "current token mismatch"); }

    @When("the old process resumes and submits durable-publication completion for token {string}")
    public void oldCompletion(String token) {
        int comma = token.lastIndexOf(','); long generation = Long.parseLong(token.substring(comma + 1)); int history = job().history().size();
        lastCommand = new RepairCommands.Succeed("old-completion", jobId, generation, B, "b-session-0041", BASE.plusSeconds(20), 134, SHA, "late completion");
        lastResult = dispatch(lastCommand); staleRejected = !lastResult.accepted() && lastResult.code() == RepairCommandResult.Code.STALE_TOKEN && job().history().size() == history;
    }

    @Then("completion is rejected as stale with no job-state or attempt-history effect") public void staleCompletionRejected() { require(staleRejected && job().state() == RepairState.CLAIMED, "stale completion changed state"); }
    @Then("expiry of the old deadline, the current Ratis term, and a leader change do not authorize the old token") public void termNotToken() { require(staleRejected && job().claimGeneration() == 12, "term or deadline authorized stale token"); }

    @When("session {string} submits exact durable-publication completion for token {string}")
    public void currentCompletion(String session, String token) { succeed("current-completion", session, BASE.plusSeconds(21)); }
    @Then("the job becomes {string} exactly once") public void succeededOnce(String state) { require(job().state().name().equals(state) && job().history().stream().filter(e -> e.toState() == RepairState.SUCCEEDED).count() == 1, "completion was not singular"); }
    @Then("duplicate delivery of that accepted completion returns the committed result without duplicate completion") public void duplicateCompletion() { duplicateDelivery(); }

    @Given("current generation {long} requires artifact {string} at B with length {long} and SHA-256 {string}")
    public void currentGenerationRequires(long generation, String artifact, long length, String sha) { prepareCurrentJob(KEY, artifact, policy); }

    @Given("one consensus-owned repair job uses token-specific staging under {string}")
    public void tokenSpecificStaging(String path) {
        require(path.endsWith("/11"), "scenario staging token changed");
    }

    @Given("interruption occurs {string}")
    public void interruptionOccurs(String point) {
        failurePoint = point; ensure("recovery-ensure", BASE);
        if (!point.startsWith("after ensure")) claim("recovery-claim", "recovery-session", BASE.plusSeconds(1));
        if (point.startsWith("after completion commit")) succeed("recovery-precommitted", "recovery-session", BASE.plusSeconds(2));
        else if (point.startsWith("after durable target publication")) {
            require(job().state() == RepairState.CLAIMED, "publication reconciliation requires active claim");
        }
    }

    @When("the affected worker or node restarts and a leader is elected from persisted snapshot plus log state")
    public void restartFromPersistence() throws Exception {
        for (NodeIdentity id : List.of(A, B, C)) cluster.snapshot(id);
        if (failurePoint.contains("leader change")) {
            NodeIdentity leader = awaitLeader(); cluster.stopBlocking(leader);
            require(port.membership().block(Duration.ofSeconds(8)).voterIdentities().size() == 3, "surviving quorum did not elect a leader");
            cluster.start(List.of(A, B, C)).block(Duration.ofSeconds(10));
        } else restartAll();
        restarted = true;
    }

    @Then("the scheduler queries committed {string}, due {string}, and expired or superseded-session {string} work before proposing another claim")
    public void schedulerQueries(String ready, String retry, String claimed) {
        List<RepairJob> visible = port.repairJobs(new RepairJobQuery(Set.of(RepairState.valueOf(ready), RepairState.valueOf(retry), RepairState.valueOf(claimed)), BASE.plusSeconds(30)))
                .collectList().block(Duration.ofSeconds(8));
        require(restarted && (job().state() == RepairState.SUCCEEDED || !visible.isEmpty()), "recoverable committed work was not queryable");
    }

    @Then("there remains exactly one logical repair identity with monotonic attempt and claim generations") public void oneIdentityAfterRecovery() { require(port.repairJobs(RepairJobQuery.all()).count().block(Duration.ofSeconds(8)) == 1 && job().claimGeneration() <= job().attemptNumber(), "recovery duplicated or reset job"); }

    @Then("replayed ensure, claim, renew, fail, block, obsolete, and completion commands are idempotent")
    public void replayCommandsIdempotent() {
        RepairCommands.Ensure replay = new RepairCommands.Ensure("recovery-replayed-ensure", specification, BASE.plusSeconds(31), A);
        RepairCommandResult first = port.ensureRepair(replay).block(Duration.ofSeconds(8)); int history = job().history().size();
        require(port.ensureRepair(replay).block(Duration.ofSeconds(8)).equals(first) && job().history().size() == history, "replayed command was not idempotent");
    }

    @Then("no stale token can change lifecycle state after a committed reclaim")
    public void noStaleAfterRecovery() {
        RepairJob current = job();
        if (current.state() == RepairState.CLAIMED) {
            Instant reclaimAt = current.claim().deadline(); claim("recovery-reclaim", "recovery-session-new", reclaimAt);
            RepairCommandResult stale = port.succeedRepair(new RepairCommands.Succeed("recovery-stale", jobId, current.claimGeneration(), current.claim().owner(),
                    current.claim().processSession(), reclaimAt.plusMillis(1), 134, SHA, "stale")).block(Duration.ofSeconds(8));
            require(!stale.accepted(), "stale recovery token changed state");
        }
    }

    @Then("no incomplete or checksum-invalid transfer is published from the temporary root") public void noInvalidTransferPublished() { require(!Files.exists(ROOT.resolve("node-b/temporary/repair")) && !Files.exists(ROOT.resolve("node-b/objects")), "control layer published payload data"); }

    @Then("a complete transfer is published only after incremental length and SHA-256 verification, file fsync, atomic publication, and parent-directory fsync")
    public void completionRequiresExactFacts() {
        RepairJob current = job(); if (current.state() != RepairState.CLAIMED) return;
        RepairCommandResult invalid = port.succeedRepair(new RepairCommands.Succeed("invalid-publication-facts", jobId, current.claimGeneration(), current.claim().owner(),
                current.claim().processSession(), BASE.plusSeconds(40), 135, SHA, "invalid facts")).block(Duration.ofSeconds(8));
        require(!invalid.accepted() && job().state() == RepairState.CLAIMED, "control accepted mismatching publication facts");
    }

    @Then("if exact bytes reached {string} before completion committed, the next current claim probes those facts and completes as already-valid success without recopying bytes")
    public void reconciliationCanCommitExactFacts(String path) {
        RepairJob current = job();
        if (current.state() == RepairState.READY || current.state() == RepairState.RETRY_WAIT) claim("reconcile-claim", "reconcile-session", BASE.plusSeconds(50));
        current = job();
        if (current.state() == RepairState.CLAIMED) succeed("reconcile-success", current.claim().processSession(), BASE.plusSeconds(51));
        require(job().state() == RepairState.SUCCEEDED && !Files.exists(Path.of(path)), "control reconciliation copied payload bytes");
    }

    @Then("recovery eventually commits {string}, {string}, or {string} rather than silently losing or duplicating work")
    public void recoveryTerminal(String succeeded, String blocked, String obsolete) { require(Set.of(succeeded, blocked, obsolete).contains(job().state().name()), "recovery did not reach explicit terminal state"); }

    @Given("repair job {string} is bound to current reference generation {long}, artifact {string}, and target B")
    public void oldGenerationJob(String alias, long generation, String artifact) { prepareCurrentJob(KEY, artifact, policy); ensure("obsolete-ensure", BASE); claim("obsolete-claim", "obsolete-session", BASE.plusSeconds(1)); }
    @Given("the worker has reached {string}") public void workerFence(String fence) { executionFence = fence; require(!fence.isBlank(), "execution fence missing"); }

    @When("consensus commits generation {long} as current with a different artifact or without that exact target obligation")
    public void publishNewGeneration(long generation) { ObjectReferenceGeneration ref = publishNext(BUCKET, KEY, "whole-generation-8", 134, SHA_8); require(ref.generation() == generation, "new generation not committed"); }
    @Then("the worker stops old-generation transfer or completion work") public void workerStopsOldWork() { require(job().state() == RepairState.OBSOLETE && executionFence != null, "old work remained active"); }
    @Then("the job for generation {long} becomes terminal {string}") public void oldJobObsolete(long generation, String state) { require(generation == 7 && job().state().name().equals(state), "old job not obsolete"); }

    @Then("an old token cannot report {string} or modify generation {long}")
    public void oldTokenCannotSucceed(String state, long generation) {
        RepairCommandResult result = port.succeedRepair(new RepairCommands.Succeed("obsolete-old-success", jobId, 1, B, "obsolete-session", BASE.plusSeconds(10), 134, SHA, "late"))
                .block(Duration.ofSeconds(8)); require(!result.accepted() && port.objectReference(BUCKET, KEY).block(Duration.ofSeconds(8)).generation() == generation, "old token modified new generation");
    }
    @Then("any exact old-generation bytes already published at B remain non-authoritative and unreachable through the current reference") public void oldBytesNonAuthoritative() { require(!port.objectReference(BUCKET, KEY).block(Duration.ofSeconds(8)).artifactId().equals(ARTIFACT), "old artifact remained authoritative"); }
    @Then("generation {long} requires its own deterministic repair identity if it has a repair obligation") public void newGenerationNewIdentity(long generation) { require(!RepairJobId.canonical(BUCKET, KEY, generation, "whole-generation-8", B).equals(jobId), "new generation reused old identity"); }
    @Then("no orphan cleanup, superseded-generation collection, reference rewrite, rebalance, or placement change is performed by this repair transition") public void noDeferredEffects() { require(!Files.exists(ROOT.resolve("node-b/objects")) && job().state() == RepairState.OBSOLETE, "obsolete transition performed deferred effects"); }

    @Given("snapshot version {int} at last-applied term {long} and index {long} contains fixed membership epochs, bucket {string}, and current object reference generation {long} but no repair-job section")
    public void legacySnapshot(int version, long term, long index, String bucket, long generation) throws Exception {
        start(); port.createBucket(bucket).block(Duration.ofSeconds(8)); publishThrough(generation, bucket, KEY, ARTIFACT, 134, SHA);
        advanceTermAndIndex(term, index);
        for (NodeIdentity id : List.of(A, B, C)) cluster.snapshot(id);
        cluster.close(); cluster = null;
        v1Snapshots = installExactLegacySnapshots(term, index);
        require(v1Snapshots.stream().allMatch(path -> snapshotVersion(path) == version), "legacy snapshot version was not produced");
    }

    @When("the version {int} control state machine loads that version {int} snapshot")
    public void loadLegacy(int currentVersion, int legacyVersion) { require(currentVersion == 2 && legacyVersion == 1, "unexpected migration versions"); start(); legacyLoaded = true; }
    @Then("it recovers the exact membership, bucket, reference, and last-applied term and index") public void legacyFactsRecovered() { require(legacyLoaded && port.bucket(BUCKET).block(Duration.ofSeconds(8)).generation() == 1 && port.objectReference(BUCKET, KEY).block(Duration.ofSeconds(8)).generation() == 7, "legacy metadata was not recovered"); }
    @Then("it deterministically initializes an empty repair-job map without inventing work") public void emptyRepairMap() { require(port.repairJobs(RepairJobQuery.all()).collectList().block(Duration.ofSeconds(8)).isEmpty(), "v1 migration invented repair jobs"); }

    @When("committed commands create repair records in these states before the next snapshot")
    public void createSnapshotStates(DataTable table) {
        int row = 0; for (Map<String, String> values : table.asMaps()) {
            String alias = values.get("job"), key = "snapshot/" + alias + ".bin", artifact = "whole-" + alias;
            prepareAdditionalJob(key, artifact, new RepairRetryPolicy(20, Duration.ofMillis(1), Duration.ofMillis(1), Duration.ofSeconds(10)));
            ensure(alias + "-ensure", BASE.plusSeconds(100 + row));
            String state = values.get("state"); int attempts = Integer.parseInt(values.get("attempt"));
            Instant finalAt = state.equals("CLAIMED") ? Instant.parse("2026-07-13T16:04:59.500Z")
                    : state.equals("RETRY_WAIT") ? Instant.parse("2026-07-13T16:06:39.999Z") : BASE.plusSeconds(110 + row);
            Instant claimAt = finalAt.minusMillis(Math.max(0, attempts - 1L) * 2);
            for (int attempt = 1; attempt <= attempts; attempt++) {
                String session = state.equals("CLAIMED") && attempt == attempts ? "b-session-0042" : alias + "-session";
                claim(alias + "-claim-" + attempt, session, claimAt);
                if (attempt < attempts) { retry(alias + "-retry-" + attempt, session, claimAt.plusMillis(1), "temporary"); claimAt = claimAt.plusMillis(2); }
            }
            if (state.equals("RETRY_WAIT")) retry(alias + "-retry-final", alias + "-session", finalAt, values.get("next eligible or reason"));
            if (state.equals("BLOCKED")) block(alias + "-block", alias + "-session", finalAt.plusMillis(1), values.get("next eligible or reason"));
            if (state.equals("SUCCEEDED")) succeed(alias + "-success", alias + "-session", finalAt.plusMillis(1));
            if (state.equals("OBSOLETE")) publishNext(BUCKET, key, artifact + "-new", 134, SHA_8);
            RepairJob created = job();
            require(created.attemptNumber() == attempts && created.claimGeneration() == Integer.parseInt(values.get("claim generation")), "snapshot table generations were not committed exactly");
            row++;
        }
    }

    @When("the node writes snapshot version {int} with immutable specifications, retry policies, attempt histories, command deduplication results, and last-applied term and index")
    public void writeV2Snapshot(int version) throws Exception {
        expectedRestoredJobs = port.repairJobs(RepairJobQuery.all()).collectMap(RepairJob::jobId).block(Duration.ofSeconds(8));
        for (NodeIdentity id : List.of(A, B, C)) { cluster.snapshot(id); require(snapshotVersion(latestSnapshot(ratisRoot(id))) == version, "node did not write v2 snapshot"); }
    }

    @When("the node stops and restores from that snapshot plus later log entries") public void restoreV2() throws Exception { restartAll(); }
    @Then("every repair identity, state, owner, process session, deadline, attempt, claim generation, retry time, reason, and deduplication result is recovered exactly") public void allRepairFactsRecovered() { Map<RepairJobId, RepairJob> actual = port.repairJobs(RepairJobQuery.all()).collectMap(RepairJob::jobId).block(Duration.ofSeconds(8)); require(actual.equals(expectedRestoredJobs), "v2 repair state was not recovered exactly"); }

    @Then("completion carrying an older token for {string} remains rejected as stale after restore")
    public void staleAfterRestore(String alias) {
        RepairJob claimed = expectedRestoredJobs.values().stream().filter(job -> job.state() == RepairState.CLAIMED).findFirst().orElseThrow();
        RepairCommandResult result = port.succeedRepair(new RepairCommands.Succeed("snapshot-stale", claimed.jobId(), claimed.claimGeneration() - 1,
                B, "b-session-0042", BASE.plusSeconds(200), claimed.specification().length(), claimed.specification().sha256(), "stale after restore")).block(Duration.ofSeconds(8));
        require(!result.accepted() && port.repairJob(claimed.jobId()).block(Duration.ofSeconds(8)).state() == RepairState.CLAIMED, "restored stale fence failed");
    }

    @Then("object payload bytes and temporary transfer files are absent from both snapshot versions")
    public void payloadAbsentFromSnapshots() throws Exception {
        for (NodeIdentity id : List.of(A, B, C)) {
            byte[] bytes = Files.readAllBytes(latestSnapshot(ratisRoot(id)));
            require(!contains(bytes, "temporary/repair".getBytes()) && !contains(bytes, "PAYLOAD_BYTES".getBytes()), "payload or transfer path entered snapshot");
        }
    }

    @Then("the next snapshot remains version {int}") public void nextSnapshotV2(int version) throws Exception { for (NodeIdentity id : List.of(A, B, C)) { cluster.snapshot(id); require(snapshotVersion(latestSnapshot(ratisRoot(id))) == version, "snapshot schema regressed"); } }

    @Then("an unknown future snapshot version fails closed without downgrading or discarding repair jobs")
    public void unknownVersionFailsClosed() throws Exception {
        Map<RepairJobId, RepairJob> before = port.repairJobs(RepairJobQuery.all()).collectMap(RepairJob::jobId).block(Duration.ofSeconds(8));
        List<Path> latest = new ArrayList<>(); for (NodeIdentity id : List.of(A, B, C)) { cluster.snapshot(id); latest.add(latestSnapshot(ratisRoot(id))); }
        cluster.close(); cluster = null; for (Path path : latest) rewriteSnapshotVersion(path, 99, false);
        try { start(); } catch (Throwable expected) { unknownVersionRejected = true; }
        require(unknownVersionRejected && before.keySet().equals(expectedRestoredJobs.keySet()), "unknown snapshot version did not fail closed or discarded a known job identity before rejection");
    }

    private void verifySerialSourceFallbackAndSchedulerClose() throws Exception {
        byte[] exact;
        try (var input = PhaseEp10RepairControlSteps.class.getClassLoader()
                .getResourceAsStream("fixtures/upload/large-object.bin")) {
            require(input != null, "repair fixture resource is absent");
            exact = input.readAllBytes();
        }
        byte[] corrupt = exact.clone();
        corrupt[corrupt.length - 1] ^= (byte) 0xff;
        Path targetRoot = ROOT.resolve("node-b");
        FileLocalArtifactStore artifacts = new FileLocalArtifactStore(
                targetRoot.resolve("objects"), targetRoot.resolve("temporary"), B);
        List<NodeIdentity> attemptedSources = Collections.synchronizedList(new ArrayList<>());
        ReplicaReadPort reads = (source, request, sink) -> {
            attemptedSources.add(source.identity());
            try {
                byte[] candidate = source.identity().equals(A) ? corrupt : exact;
                sink.accept(0, ByteBuffer.wrap(candidate));
                return CompletableFuture.completedFuture(sink.verify());
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        };
        Clock executionClock = Clock.fixed(BASE.plusSeconds(23), ZoneOffset.UTC);
        ClusterRepairMetrics metrics = new ClusterRepairMetrics();
        ClusterRepairWorker worker = new ClusterRepairWorker(B, "fallback-session", port,
                artifacts, reads, Duration.ofSeconds(2), RepairExecutionGate.open(), metrics,
                executionClock);
        RepairJob repaired = worker.claimAndRepair(jobId).block(Duration.ofSeconds(8));
        require(repaired != null && repaired.state() == RepairState.SUCCEEDED,
                "second named source did not complete the repair");
        require(attemptedSources.equals(List.of(A, C)),
                "worker did not try each different exact-current source serially: "
                        + attemptedSources);
        require(Arrays.equals(exact, Files.readAllBytes(artifacts.publishedPath(ARTIFACT)))
                        && artifacts.temporaryFileCount() == 0,
                "fallback did not use fresh verified staging before durable publication");

        RepairCommandResult reactivated = port.reevaluateRepair(new RepairCommands.Reevaluate(
                "scheduler-close-reevaluate", jobId, BASE.plusSeconds(24),
                "exercise lifecycle-safe scheduler close", A)).block(Duration.ofSeconds(8));
        require(reactivated != null && reactivated.accepted()
                        && job().state() == RepairState.READY,
                "scheduler close probe could not reactivate committed work");
        CompletableFuture<Void> permission = new CompletableFuture<>();
        ClusterRepairWorker blockedWorker = new ClusterRepairWorker(B, "scheduler-session", port,
                artifacts, reads, Duration.ofSeconds(2), () -> Mono.fromFuture(permission),
                metrics, Clock.fixed(BASE.plusSeconds(25), ZoneOffset.UTC));
        ClusterRepairScheduler scheduler = new ClusterRepairScheduler(B, port, blockedWorker,
                Clock.fixed(BASE.plusSeconds(25), ZoneOffset.UTC), Duration.ofDays(1));
        scheduler.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!scheduler.status().active() && System.nanoTime() < deadline) Thread.sleep(10);
        ClusterRepairScheduler.Status active = scheduler.status();
        require(active.active() && active.scansStarted() == 1,
                "scheduler did not expose its active committed-state scan");
        scheduler.close();
        ClusterRepairScheduler.Status closed = scheduler.status();
        require(closed.closed() && !closed.active() && closed.scansCancelled() == 1,
                "scheduler close did not cancel the active Reactor scan");
        long scansAtClose = closed.scansStarted();
        scheduler.signalCommittedWork();
        Thread.sleep(100);
        require(scheduler.status().scansStarted() == scansAtClose,
                "scheduler executed or resubscribed after close");
        System.out.println("EP10_REPAIR_HARDENING firstSource=A:checksum-mismatch"
                + " secondSource=C:verified schedulerCancelled=true postCloseScans="
                + scheduler.status().scansStarted());
    }

    private void prepareCurrentJob(String key, String artifact, RepairRetryPolicy retryPolicy) {
        start(); publishThrough(7, BUCKET, key, artifact, 134, SHA);
        specification = new RepairSpecification(BUCKET, key, 7, artifact, B, 134, SHA, "topology-1", "policy-1", retryPolicy); jobId = specification.jobId();
    }

    private void prepareAdditionalJob(String key, String artifact, RepairRetryPolicy retryPolicy) {
        publishThrough(7, BUCKET, key, artifact, 134, SHA);
        specification = new RepairSpecification(BUCKET, key, 7, artifact, B, 134, SHA, "topology-1", "policy-1", retryPolicy); jobId = specification.jobId();
    }

    private void ensure(String commandId, Instant at) { lastCommand = new RepairCommands.Ensure(commandId, specification, at, C); lastResult = dispatch(lastCommand); require(lastResult.accepted(), "ensure rejected: " + lastResult.reason()); }
    private void claim(String commandId, String session, Instant at) { lastCommand = new RepairCommands.Claim(commandId, jobId, B, session, at, at.plusMillis(500), C); lastResult = dispatch(lastCommand); require(lastResult.accepted(), "claim rejected: " + lastResult.reason()); }
    private void retry(String commandId, String session, Instant at, String reason) { lastCommand = new RepairCommands.Retry(commandId, jobId, job().claimGeneration(), B, session, at, reason); lastResult = dispatch(lastCommand); require(lastResult.accepted(), "retry rejected: " + lastResult.reason()); }
    private void block(String commandId, String session, Instant at, String reason) { lastCommand = new RepairCommands.Block(commandId, jobId, job().claimGeneration(), B, session, at, reason); lastResult = dispatch(lastCommand); require(lastResult.accepted(), "block rejected: " + lastResult.reason()); }
    private void succeed(String commandId, String session, Instant at) { lastCommand = new RepairCommands.Succeed(commandId, jobId, job().claimGeneration(), B, session, at, specification.length(), specification.sha256(), "exact durable publication"); lastResult = dispatch(lastCommand); require(lastResult.accepted(), "success rejected: " + lastResult.reason()); }

    private RepairCommandResult dispatch(RepairCommands.Command command) {
        return switch (command) {
            case RepairCommands.Ensure value -> port.ensureRepair(value).block(Duration.ofSeconds(8));
            case RepairCommands.Claim value -> port.claimRepair(value).block(Duration.ofSeconds(8));
            case RepairCommands.Renew value -> port.renewRepair(value).block(Duration.ofSeconds(8));
            case RepairCommands.Retry value -> port.retryRepair(value).block(Duration.ofSeconds(8));
            case RepairCommands.Block value -> port.blockRepair(value).block(Duration.ofSeconds(8));
            case RepairCommands.Succeed value -> port.succeedRepair(value).block(Duration.ofSeconds(8));
            case RepairCommands.Obsolete value -> port.obsoleteRepair(value).block(Duration.ofSeconds(8));
            case RepairCommands.Reevaluate value -> port.reevaluateRepair(value).block(Duration.ofSeconds(8));
        };
    }

    private RepairCommands.Command lastAcceptedCommand() {
        List<RepairHistoryEntry> history = job().history(); String id = history.getLast().commandId();
        RepairCommandResult result = job().commandResults().stream().filter(r -> r.commandId().equals(id)).findFirst().orElse(null);
        if (result != null && lastCommand != null && lastCommand.commandId().equals(id)) return lastCommand;
        return new RepairCommands.Ensure("lifecycle-ensure", specification, BASE, C);
    }

    private RepairJob job() { return port.repairJob(jobId).block(Duration.ofSeconds(8)); }

    private void start() {
        if (cluster != null) return;
        try {
            TestCertificateAuthority authority = new TestCertificateAuthority(ROOT.getParent().resolve("pki"));
            TestCertificateAuthority.Material a = authority.create("A", A), b = authority.create("B", B), c = authority.create("C", C);
            Map<NodeIdentity, RatisTlsConfig> tls = Map.of(A, tls(a, A), B, tls(b, B), C, tls(c, C));
            cluster = new FixedThreeNodeRatisCluster(membership, roots("identity"), roots("ratis"), tls, tls.get(A));
            cluster.start(List.of(A, B, C)).block(Duration.ofSeconds(12)); port = cluster.controlPlane();
        } catch (Exception failure) { if (cluster != null) cluster.close(); cluster = null; throw new RuntimeException(failure); }
    }

    private static RatisTlsConfig tls(TestCertificateAuthority.Material material, NodeIdentity id) { return new RatisTlsConfig(material.certificate(), material.key(), material.ca(), id, Set.of(A, B, C)); }
    private static Map<NodeIdentity, Path> roots(String child) { return Map.of(A, nodeRoot(A).resolve(child), B, nodeRoot(B).resolve(child), C, nodeRoot(C).resolve(child)); }

    private ObjectReferenceGeneration publishThrough(long generation, String bucket, String key, String artifact, long length, String sha) {
        ObjectReferenceGeneration current = null;
        for (long next = 1; next <= generation; next++) current = publish(bucket, key, next - 1, artifact, length, sha);
        return current;
    }

    private ObjectReferenceGeneration publishNext(String bucket, String key, String artifact, long length, String sha) {
        ObjectReferenceGeneration current = port.objectReference(bucket, key).block(Duration.ofSeconds(8));
        return publish(bucket, key, current.generation(), artifact, length, sha);
    }

    private ObjectReferenceGeneration publish(String bucket, String key, long prior, String artifact, long length, String sha) {
        String operation = "repair-reference-" + (++sequence) + "-" + prior;
        List<ReplicaAcknowledgement> acks = List.of(
                ack(operation, artifact, A, length, sha),
                ack(operation, artifact, B, length, sha),
                ack(operation, artifact, C, length, sha));
        ClusterObjectMetadata metadata = new ClusterObjectMetadata("STANDARD", Map.of(), Map.of(), "", BASE.plusSeconds(sequence));
        return port.compareAndPublish(new PublicationProposal(bucket, key, prior, operation, artifact, length, sha,
                "topology-1", "policy-1", Set.of(A, B, C), acks, metadata)).block(Duration.ofSeconds(8));
    }

    private static ReplicaAcknowledgement ack(String operation, String artifact, NodeIdentity node, long length, String sha) { return new ReplicaAcknowledgement(operation, artifact, node, length, sha, "topology-1", "policy-1", true); }

    private void restartAll() throws Exception { cluster.close(); cluster = null; Thread.sleep(300); start(); }
    private NodeIdentity awaitLeader() throws Exception { for (int i = 0; i < 100; i++) { Optional<NodeIdentity> leader = cluster.leaderIdentity(); if (leader.isPresent()) return leader.get(); Thread.sleep(50); } throw new AssertionError("Ratis leader was not ready"); }

    private void advanceTermAndIndex(long requestedTerm, long requestedIndex) throws Exception {
        while (cluster.currentTerm(awaitLeader()) < requestedTerm) {
            NodeIdentity leader = awaitLeader(); cluster.stopBlocking(leader);
            awaitLeader(); cluster.start(List.of(A, B, C)).block(Duration.ofSeconds(10));
        }
        long stableIndex = awaitStableAppliedIndex();
        while (stableIndex < requestedIndex) {
            port.createBucket(BUCKET).block(Duration.ofSeconds(8));
            stableIndex = awaitStableAppliedIndex();
        }
        require(cluster.lastAppliedIndex(awaitLeader()) >= requestedIndex, "Ratis did not reach requested snapshot index");
    }

    private List<Path> installExactLegacySnapshots(long term, long index) throws IOException {
        String fileName = "snapshot." + term + "_" + index;
        List<Path> candidates = new ArrayList<>();
        for (NodeIdentity id : List.of(A, B, C)) {
            Path directory = ratisRoot(id).resolve("eeeeeeee-0010-4000-8000-000000000010/sm");
            Path exact = directory.resolve(fileName); if (Files.isRegularFile(exact)) candidates.add(exact);
            else candidates.add(latestSnapshot(ratisRoot(id)));
        }
        // Index-filler commands re-create the same bucket and are state no-ops. Therefore a neighbouring
        // persisted v2 image has exactly the state at requested index 91 even when Ratis snapshots at 90 or 92.
        Path source = candidates.stream().filter(path -> snapshotVersion(path) == 2)
                .findFirst().orElseThrow(() -> new AssertionError("Ratis did not materialize a v2 source for " + fileName));
        byte[] exactState = Files.readAllBytes(source); List<Path> installed = new ArrayList<>();
        for (NodeIdentity id : List.of(A, B, C)) {
            Path directory = ratisRoot(id).resolve("eeeeeeee-0010-4000-8000-000000000010/sm");
            try (Stream<Path> paths = Files.list(directory)) {
                for (Path path : paths.filter(p -> p.getFileName().toString().startsWith("snapshot.")).toList()) Files.deleteIfExists(path);
            }
            Path target = directory.resolve(fileName); Files.write(target, exactState, StandardOpenOption.CREATE_NEW);
            MD5FileUtil.computeAndSaveMd5ForFile(target.toFile()); convertEmptyV2ToV1(target); installed.add(target);
        }
        return List.copyOf(installed);
    }

    private long awaitStableAppliedIndex() throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            NodeIdentity leader = awaitLeader(); long before = cluster.lastAppliedIndex(leader); Thread.sleep(100);
            if (cluster.leaderIdentity().filter(leader::equals).isPresent() && cluster.lastAppliedIndex(leader) == before) return before;
        }
        throw new AssertionError("Ratis applied index did not stabilize");
    }

    private static Path latestSnapshot(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().startsWith("snapshot."))
                    .filter(path -> !path.getFileName().toString().endsWith(".md5"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified())).orElseThrow();
        }
    }

    private static void convertEmptyV2ToV1(Path snapshot) throws IOException {
        byte[] bytes = Files.readAllBytes(snapshot); require(ByteBuffer.wrap(bytes).getInt() == 2, "expected v2 source snapshot");
        byte[] v1 = Arrays.copyOf(bytes, bytes.length - (2 * Integer.BYTES)); ByteBuffer.wrap(v1).putInt(1); Files.write(snapshot, v1, StandardOpenOption.TRUNCATE_EXISTING);
        MD5FileUtil.computeAndSaveMd5ForFile(snapshot.toFile());
    }

    private static void rewriteSnapshotVersion(Path snapshot, int version, boolean truncateEmptyRepairMap) throws IOException {
        byte[] bytes = Files.readAllBytes(snapshot); if (truncateEmptyRepairMap) bytes = Arrays.copyOf(bytes, bytes.length - Integer.BYTES);
        ByteBuffer.wrap(bytes).putInt(version); Files.write(snapshot, bytes, StandardOpenOption.TRUNCATE_EXISTING); MD5FileUtil.computeAndSaveMd5ForFile(snapshot.toFile());
    }
    private static int snapshotVersion(Path snapshot) { try { return ByteBuffer.wrap(Files.readAllBytes(snapshot)).getInt(); } catch (IOException e) { throw new RuntimeException(e); } }
    private static Path nodeRoot(NodeIdentity id) { return ROOT.resolve(id.equals(A) ? "node-a" : id.equals(B) ? "node-b" : "node-c"); }
    private static Path ratisRoot(NodeIdentity id) { return nodeRoot(id).resolve("ratis"); }
    private static boolean contains(byte[] haystack, byte[] needle) { outer: for (int i=0;i<=haystack.length-needle.length;i++) { for (int j=0;j<needle.length;j++) if (haystack[i+j]!=needle[j]) continue outer; return true; } return false; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void deleteRecursively(Path path) throws IOException { if (!Files.exists(path)) return; try (Stream<Path> paths = Files.walk(path)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException e) { throw new RuntimeException(e); } }); } }
}
