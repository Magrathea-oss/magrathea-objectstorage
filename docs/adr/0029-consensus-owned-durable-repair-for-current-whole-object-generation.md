# ADR 0029 — Consensus-owned durable repair for the current whole-object generation

Date: 2026-07-13

## Status

Accepted — bounded architectural decision; implementation evidence is current through 2026-07-14 for the EP-10 current-generation whole-object repair slice.

The exact status is deliberately narrower than broad healing. `REQ-CLUSTER-019` through `REQ-CLUSTER-026`, including `REQ-CLUSTER-024`, are `@implemented-and-validated`. Subsequent ADR 0031 makes `REQ-CLUSTER-027` `@implemented-and-validated` for bounded periodic current-reference discovery on fixed A/B/C, while broad `REQ-CLUSTER-017` remains `@partial` because wider healing/topology coverage, rebalance, and automated orphan cleanup remain absent. That separate discovery decision reuses but does not rewrite ADR 0029's repair authority, identity, fencing, or data path. `REQ-CLUSTER-014` is `@implemented-and-validated` only for its repository-rooted internal source/build architecture mode (**1 scenario / 17 steps**). Its validation comes from that separate architecture gate, not from ADR 0029 repair tests, and does not broaden this ADR's repair evidence, S3 behavior, runtime side effects, broad healing, or production readiness. At this ADR's repair checkpoint, `REQ-CLUSTER-006`, `REQ-CLUSTER-007`, `REQ-CLUSTER-015`, `REQ-CLUSTER-016`, and `REQ-CLUSTER-018` were `@not-implemented`; subsequent ADR 0033 upgrades only fixed internal EC 4+2 `REQ-CLUSTER-015`. This acceptance is not a production-readiness or general distributed-support claim.

## Context

ADRs 0027 and 0028 establish a fixed A/B/C cluster baseline with consensus-selected `ObjectReferenceGeneration` records and direct whole-object replica transfer. The bounded ADR 0028 baseline could publish and read immutable whole-object replicas but originally had no consensus-owned repair-job lifecycle. This implementation adds the bounded lifecycle decided here. PA-6 can detect modeled missing or corrupt replicas and produce `HealingTask` planning output; it still does not own durable work, copy bytes, fence attempts, or prove repair completion.

This decision adds only durable repair for a replica already promised by the current committed whole-object reference. It does not fill a new placement, change the reference, or expand the fixed membership. Repair must survive worker crashes, process restarts, snapshots, and Ratis leader changes without allowing stale workers to report completion.

The existing GET integrity contract also constrains this slice. GET does not completely read a present local payload as a preflight and then read it again for the response. A present local payload is consumed once while length and checksum are calculated. Consequently, corruption discovered near the end of that stream is too late for a transparent alternate-replica retry because response bytes may already have been emitted. Missing local data is different: absence can be established before the payload stream starts, so a verified remote replica may be fetched before beginning the response.

The non-normative wiki domain analysis in `/home/paperboy/.llm-wiki/wiki/analyses/ep-10-fixed-cluster-durable-repair-and-orphan-cleanup-slice.md` was reviewed on 2026-07-13 as research/recommendation input, not as requirement, ADR, or implementation evidence. It covers both repair and cleanup; this ADR independently decides only the repair model.

## Decision

### Bounded repair scope and evidence gate

The slice applies only when all of the following are true:

- the deployment is the fixed A/B/C topology accepted by ADR 0028;
- the object has a current consensus-committed `ObjectReferenceGeneration`;
- that generation identifies one immutable `WHOLE_OBJECT` artifact with committed length and SHA-256 facts;
- the repair target is already named in that generation's replica list; and
- the target replica is observed missing or corrupt and a different named replica can be verified against the committed facts.

The executable requirements separate:

- a shared S3 `Business Need` for missing-local fallback and for the fail-current-request/repair-later behavior after single-pass corruption detection, reused by WebTestClient and AWS CLI validation where both modes apply; and
- internal `Ability` scenarios for deduplication, lifecycle transitions, fencing, direct verified transfer, snapshot migration, crash/restart recovery, and leader-change idempotence.

`REQ-CLUSTER-017` is the broad capability statement and remains `@partial`: bounded current-generation repair is `@implemented-and-validated` under `REQ-CLUSTER-019` through `REQ-CLUSTER-026`, including the seven-point `REQ-CLUSTER-024` interruption catalogue. ADR 0031 separately validates bounded periodic current-reference discovery under `REQ-CLUSTER-027`; wider healing/topology coverage, rebalance, and automated orphan cleanup remain absent. Planner unit tests, route presence, job records without execution, or status-only checks cannot upgrade repair status.

### PA-6 planning and execution boundary

PA-6 `AntiEntropyPlanner` remains deterministic, side-effect-free policy. `ReplicaObservation`, `AntiEntropyFinding`, `HealingPlan`, and `HealingTask` describe observed evidence and a proposed action only. A `HealingTask` identifier, status, or source selection is not durable ownership and is not repair completion.

Cluster application code must:

1. resolve the current committed reference;
2. collect bounded observations;
3. invoke `AntiEntropyPlanner` where policy planning is needed;
4. revalidate that the same committed generation is current;
5. map a still-applicable missing/corrupt finding to an idempotent repair-job proposal; and
6. let an application worker claim and execute the committed job through infrastructure ports.

Ratis persistence, clocks, retries, claims, gRPC, and filesystem effects remain outside `storage-engine-domain`. `RebalancePlanner` is not involved: this repair restores an already promised target and does not select a new target or move placement.

### Repair identity and immutable specification

The canonical semantic identity is:

`(REPAIR, bucket, objectKey, referenceGeneration, artifactId, targetNodeId)`

An ensure command derives a stable `jobId` from this typed key and returns the existing job when the key is already present. It must not create one job per observation, coordinator, leader, worker, request, or candidate source.

The immutable specification records the key plus committed artifact length, SHA-256, topology epoch, policy epoch, and the target's stable node UUID. A candidate source is only an attempt hint. It is excluded from identity and may be replaced by another different, named, verified source without creating another logical job.

A fresh missing/corrupt observation for an exact job that previously reached `SUCCEEDED` reactivates that same logical job only after the reference is confirmed still current. This preserves deduplication while allowing later damage to the same promised replica. Attempt history and claim generation remain monotonic across reactivation.

### Consensus-owned lifecycle

Ratis owns these states:

- `READY`: committed, currently applicable, unclaimed, and eligible now;
- `CLAIMED`: assigned to one stable node/process session until a committed claim deadline;
- `RETRY_WAIT`: a retryable attempt failed and the committed next-eligible time has not arrived;
- `SUCCEEDED`: the target was durably published with the exact committed length/checksum and completion for the current fencing token was committed;
- `BLOCKED`: safe automatic progress is unavailable, for example no verified source remains, an integrity conflict exists, or the bounded retry policy is exhausted; and
- `OBSOLETE`: the bound reference generation is no longer current or no longer names the exact target/artifact obligation.

The normal transitions are:

- ensure applicable work: absent → `READY`;
- claim due work: `READY` or due `RETRY_WAIT` → `CLAIMED`;
- retryable failure: `CLAIMED` → `RETRY_WAIT`;
- non-retryable or exhausted failure: `CLAIMED` → `BLOCKED`;
- durable exact publication: `CLAIMED` → `SUCCEEDED`;
- generation/reference mismatch: any non-obsolete state → `OBSOLETE`; and
- explicit re-evaluation with fresh evidence: `BLOCKED` or `SUCCEEDED` → `READY` only while the same reference remains current.

`OBSOLETE` is terminal. A newer reference requires its own deterministic key; an old job is never rewritten to point at the newer generation.

### Claims, process sessions, retries, and stale tokens

Every accepted claim or reclaim increments both the attempt number and `claimGeneration`. The safety token is exactly:

`(jobId, claimGeneration)`

Ratis term is not a fencing token because multiple attempts can occur in one term and attempts can span leader changes.

A claim records the worker's persisted stable node UUID and a process-session ID generated once at process startup and held constant for that process lifetime. A restarted process has a new process-session ID and cannot renew, fail, or complete its predecessor's claim. Reassignment occurs only through a committed claim/reclaim transition; registering a replacement process session may make the old session reclaimable, but the reclaim must still increment `claimGeneration`.

Renew, fail, block, succeed, and mark-obsolete commands compare the exact current token. A command carrying an older generation, a different session, or a token for a non-`CLAIMED` state is rejected as stale and has no state effect. Duplicate delivery of an already accepted command returns the committed result without duplicating transitions or attempt records.

Claim deadlines govern liveness, not side-effect safety. A deadline does not authorize the old worker to complete. After a committed reclaim, the old token is stale even if the old process resumes. Token-specific staging paths and exact-content publication make a late stale transfer harmless; only the current token can change job state.

Retry classification is explicit:

- bounded transport failure, deadline expiry, temporary source unavailability, temporary target I/O failure, or uncertain completion submission is retryable;
- a candidate source with wrong length/checksum is excluded and another named source may be tried within the same bounded attempt;
- no remaining verified source, conflicting target identity/content that cannot be safely replaced, or exhausted attempts moves the job to `BLOCKED` and emits an integrity/repair alert; and
- a changed current reference moves the job to `OBSOLETE`, never to retry wait.

The job snapshots a finite retry policy. Retry delay uses deterministic capped exponential backoff from the attempt number; no state-machine-local randomness or wall-clock read is permitted. Command timestamps, claim deadlines, and next-eligible times are leader-proposed command data validated by deterministic state transitions. An explicit current-generation re-evaluation or operator action may release `BLOCKED`; it creates neither a new identity nor a reset claim generation.

After an uncertain command result, a worker queries committed state before acting again. It stops if the job is succeeded, obsolete, blocked, or owned by a newer token; it may resubmit an idempotent command only while its exact claim remains current.

### Current-generation and obsolete-generation fencing

Job creation and execution use the consensus-selected reference, not local manifests or replica comparison, as generation authority.

- Ensure verifies that the proposed `referenceGeneration` is current and that its artifact and target facts exactly match the current reference.
- A worker rechecks currentness before choosing a source, immediately before target publication, and before proposing success.
- A source must be a different node named by that same reference. It must verify the complete committed length and SHA-256 during transfer.
- A missing, corrupt, unavailable, or checksum-mismatching replica is never a source.
- If the reference changes, disappears, or stops naming the target/artifact, work stops and the old job becomes `OBSOLETE`.

Repair does not modify or republish object-reference metadata. If a generation change races with an already-started exact publication, those bytes cannot make the old generation authoritative: success is rejected by the current-generation check and the old job becomes obsolete. Reclamation of any resulting unreachable artifact is outside this ADR.

### Direct verified repair data path

Consensus stores job metadata and lifecycle only. Whole-object bytes transfer directly over the existing bounded, mTLS-authenticated replica data path and never through the Ratis log or snapshot.

The worker stages bytes under the fencing token, calculates length and SHA-256 incrementally, and publishes nothing until the complete source matches the committed facts. Target publication requires file fsync, atomic replacement/publication, and parent-directory fsync. A corrupt target is atomically replaced only by the exact verified artifact; unverified bytes are neither returned nor copied. If the target already contains the exact valid artifact, the attempt is idempotent success.

Only after durable local publication may the worker submit success for the current token. A crash after publication but before the success commit is recovered by probing the exact target facts and completing the retry as already-valid success. A stale worker can at worst publish the same immutable verified bytes; it cannot commit completion or overwrite the job state owned by a newer token.

### GET behavior

The repair slice preserves the established single-pass GET contract:

- A present local payload is not completely preflight-read and then opened a second time. It is read once for the response while length and checksum are calculated incrementally.
- If the local artifact is missing before response streaming starts, the reader durably ensures/deduplicates the repair job and may claim it inline. It may fetch a different current-reference replica, fully verify and durably publish that transfer, and then begin the response from the repaired artifact with one filesystem read. Completion submission may be retried independently if its result is uncertain.
- If corruption is discovered while the present local payload is being consumed in the single response pass, that GET fails with an integrity outcome and durably ensures the repair job. The server does not transparently retry the corrupt read from another replica because response bytes may already have been emitted.
- A later GET may succeed after a worker has durably repaired the target. No current request is reported as successful merely because repair was scheduled.
- If the durable ensure cannot be committed, the corrupt GET still fails and emits an explicit unscheduled-repair alert; the system must not report that durable repair was scheduled.

This distinction permits transparent handling of known absence before a response while prohibiting transparent corrupt-stream retry. It also avoids whole-payload aggregation and a second filesystem read.

### Versioned Ratis snapshots and migration

The implementation advances the control-state snapshot from the reference-only schema version 1 to schema version 2.

A version 2 snapshot contains the existing membership epoch checks, buckets, and object references plus every repair job's immutable specification, state, attempt history required for audit and command deduplication, current claim owner/session/deadline, attempt number, claim generation, retry policy, next-eligible time, terminal or blocked reason, and command-supplied timestamps. Ratis snapshot metadata retains the last-applied term/index used for replay safety. Payload bytes and temporary transfer files are never snapshot content.

The version 2 loader accepts version 1 by loading its buckets and references and deterministically initializing an empty repair-job map. It writes version 2 on the next snapshot. Unknown future versions fail closed; a node must not silently downgrade or discard jobs. Command codecs and snapshots must be explicitly versioned, and migration/restart requirements must cover a version 1 snapshot, a version 2 active claim, retry wait, blocked work, succeeded work, obsolete work, and stale-token rejection after restore.

This is a bounded persisted-state migration, not a claim of rolling mixed-version cluster support.

### Crash, restart, and leader-change idempotence

Leader-ready and leader-change notifications may wake a scheduler, but they neither own work nor execute filesystem or network effects inside `applyTransaction`. The replicated state machine performs deterministic state transitions only; it does not read clocks, generate random values, call gRPC, touch the filesystem repair target, sleep, or retry.

After process restart or leader change, a scheduler queries committed `READY`, due `RETRY_WAIT`, and expired/superseded-session `CLAIMED` work and proposes claims. Replayed ensure/claim/result commands remain idempotent. Snapshot plus log replay preserves job identity, attempt history, claim generation, and stale-token fences.

The seven-point interruption catalogue covers interruption after ensure, after claim, during transfer, after durable publication but before success commit, after success commit, during snapshot, and across leader change. The bounded `REQ-CLUSTER-024` harness now exercises every point with real filesystem and gRPC repair side effects. B runs as an independently crashable and restarted JVM with distinct process IDs while retaining its original identity, Ratis, and filesystem roots, all non-empty across restart; the A/C voters and source-C real grpc-java server remain in the parent Cucumber JVM. The scenarios perform actual B-to-C grpc-java reads with UUID-bound mTLS, token-specific `FileLocalArtifactStore` staging and publication, filesystem byte and hash inspection, exact-target no-recopy reconciliation, stale-token fencing, interrupted version-2 snapshot installation with last-valid-snapshot-plus-log recovery, completion committed with its reply withheld, and live A-to-C leadership transfer. `REQ-CLUSTER-024` is therefore `@implemented-and-validated` for this bounded validation mode. No general chaos, broad partition tolerance, rolling upgrade, dynamic membership, anti-entropy, rebalance, orphan cleanup, or production-readiness claim follows.

### Explicitly deferred scope

This ADR does not decide or authorize:

- prepared-artifact intents or retirement tombstones;
- orphan cleanup, legacy artifact sweeping, or superseded-generation garbage collection;
- rebalance or `RebalancePlanner` execution;
- anti-entropy beyond bounded fixed A/B/C current-reference scans, including production-scale scheduling/fairness;
- adding an unacknowledged third placement or changing a reference's replica set;
- dynamic membership, node replacement, or broader node-incarnation policy;
- erasure coding, shards, chunk repair, multipart, conditional writes, or S3 versioning; or
- broad, asymmetric, or split control/data-plane partitions.

Those capabilities require separate requirements and decisions. This repair slice makes no partition-tolerance or distributed-production-readiness claim.

## Consequences

- Repair ownership, deduplication, retries, and completion are consensus facts rather than in-memory scheduler state.
- A source can change between attempts without producing duplicate logical jobs.
- Stable claim-generation fencing prevents stale workers from changing lifecycle state after reclaim, while immutable exact-content publication keeps repeated side effects safe.
- Repair remains bound to authoritative current metadata and cannot resurrect an old generation or silently change placement.
- Ratis snapshots grow with job and attempt metadata. Snapshot-history growth and retention/compaction remain operational risks even after the bounded interrupted-version-2 recovery evidence; object payload volume remains outside consensus.
- Missing-local GET can incur a full verified remote transfer before response latency. Corrupt-local GET intentionally fails the current request; availability improves only for a later request after repair.
- `BLOCKED` makes unrecoverable or exhausted work visible instead of retrying forever or copying unverified bytes.
- Deferring cleanup means a narrow race with generation change can leave unreachable exact artifacts. They are non-authoritative and must remain until a separately fenced cleanup design exists.
- `REQ-CLUSTER-017` remains only `@partial`: this ADR's bounded repair under `REQ-CLUSTER-019` through `REQ-CLUSTER-026` and ADR 0031's bounded periodic current-reference discovery under `REQ-CLUSTER-027` leave wider healing/topologies, rebalance, and cleanup absent.

## Alternatives Considered

### Use PA-6 `HealingTask` as the durable job record

Not selected. The task is deterministic planning output and its source choice is a hint. It lacks consensus ownership, committed retries, process-session claims, attempt fencing, snapshot migration, and durable completion semantics.

### Use an in-memory scheduler with best-effort retries

Not selected. Work and ownership would be lost or duplicated across crashes and leader changes, and a stale process could report completion after reassignment.

### Include source node in repair identity

Not selected. Source availability is attempt-specific. Including it would create duplicate jobs for one target obligation and impede safe failover to another verified source.

### Fence attempts with Ratis term or a claim deadline only

Not selected. Several attempts can occur in one term, and an expired worker can continue running. Monotonic per-job claim generation is the safety fence; deadlines provide liveness only.

### Put replica bytes in the consensus log

Not selected. It would put data-volume traffic into Ratis logs and snapshots, increasing quorum bandwidth, recovery time, and snapshot size. Consensus owns references and work state; direct gRPC owns bytes.

### Preflight every local payload and read it again for GET

Not selected. It violates the established single-pass streaming contract and doubles filesystem I/O. A missing artifact can be repaired before response; corruption discovered during the one response pass fails that request.

### Transparently retry another replica after local stream corruption

Not selected. Detection may occur after client bytes have been emitted, so transparent fallback could concatenate or replace an already-started response. The request fails, repair is durably scheduled, and only a later GET may benefit.

### Combine repair, prepared-artifact tracking, and orphan cleanup in one slice

Not selected. Safe orphan cleanup requires publication intents and irreversible retirement fencing that are independent of restoring a replica already named by the current reference. Keeping this ADR repair-only gives the first durable-job slice a smaller safety proof.

### Run broad periodic anti-entropy or rebalance with the same worker

Not selected by this repair decision. Broad discovery, fairness, topology movement, and placement changes add separate policy and operational risks. This slice consumes bounded missing/corrupt evidence only for an already promised target. ADR 0031 later accepts only bounded periodic current-reference discovery on fixed A/B/C and delegates every repair to this ADR; it still excludes broader discovery/topologies, rebalance, and cleanup.

## Evidence

Implementation-informed evidence was reviewed on 2026-07-13 in a dirty working tree based on repository commit `3eb2a508317a`; it is point-in-time evidence, not release or production-readiness evidence:

- The Java 21 real-process shared EP-10 runner passed **14 scenarios / 188 steps** for `REQ-CLUSTER-001..005`, `019`, and `020` across WebTestClient and AWS CLI modes.
- The focused repair-only real-process run for `REQ-CLUSTER-019/020` passed **4 scenarios / 80 steps**.
- The focused repair-control run for `REQ-CLUSTER-021..026` passed **22 scenarios / 210 steps**. This remains the exact historical result for that 2026-07-13 run.
- The cluster data-plane regression passed after repair integration.
- `ClusterControlStateMachine` and `ControlPlaneCodec` persist version-2 repair identity, lifecycle, deduplication, fencing, retry, history, and snapshot migration state. `ClusterRepairScheduler` stores no authoritative job data: startup and committed-work signals only trigger bounded queries of consensus-owned work. `ClusterNodeRuntime` owns starting and stopping that process-local scheduler alongside the voter and replica server.
- `ClusterRepairWorker` executes direct verified transfer and filesystem publication outside the replicated state machine, rechecks the current reference around side effects, and reports results only through the current claim-generation/process-session token.
- `REQ-CLUSTER-019/020` preserve the single-pass GET distinction: known absence is repaired before response streaming, while corruption discovered during the one response pass fails that request without transparent fallback; only a later GET may succeed after durable repair.
- `docs/adr/0027-authoritative-cluster-control-plane-and-direct-quorum-data-path.md` remains the wider architectural authority, while this ADR provides the accepted bounded refinement for current-generation repair and the single-pass corruption constraint.
- Wiki domain analysis `/home/paperboy/.llm-wiki/wiki/analyses/ep-10-fixed-cluster-durable-repair-and-orphan-cleanup-slice.md` remains non-normative research/recommendation input, not requirement or implementation evidence.

Focused completion evidence was reviewed separately on 2026-07-14 for the bounded `REQ-CLUSTER-024` gate:

- On **2026-07-14**, focused `ReqCluster024CucumberTest` passed **7 scenarios / 168 steps**. B ran as an independently crashable and restarted JVM with distinct process IDs while retaining its original identity, Ratis, and filesystem roots, all non-empty across restart; the A/C voters and source-C real grpc-java server remained in the parent Cucumber JVM. The scenarios performed actual B-to-C grpc-java reads with UUID-bound mTLS, token-specific `FileLocalArtifactStore` staging and publication, filesystem byte and hash inspection, exact-target no-recopy reconciliation, stale-token fencing, interrupted version-2 snapshot installation with last-valid-snapshot-plus-log recovery, completion committed with its reply withheld, and live A-to-C leadership transfer.
- In the same 2026-07-14 validation set, the existing repair-control suite passed **22 scenarios / 294 steps**, the data-plane suite passed **4 scenarios / 40 steps**, and the control and TLS regressions passed.
- On 2026-07-14, module compilation, layering, source hygiene, and diff checks passed.

This is bounded evidence only. It is not evidence of general chaos, broad partition tolerance, rolling upgrades, dynamic membership, broad anti-entropy, rebalance, orphan cleanup, or production readiness.

## Related Requirements

- `REQ-CLUSTER-006`, `REQ-CLUSTER-007`, `REQ-CLUSTER-016`, and `REQ-CLUSTER-018` — remain `@not-implemented`; this ADR does not upgrade them.
- `REQ-CLUSTER-015` — subsequently implemented and validated for fixed internal EC 4+2 by ADR 0033, not by this repair ADR.
- `REQ-CLUSTER-014` — `@implemented-and-validated` only for its repository-rooted internal source/build architecture mode (**1 scenario / 17 steps**); its separate architecture gate, not ADR 0029 repair tests, supplies that bounded validation and does not broaden this ADR's repair evidence, S3 behavior, runtime side effects, broad healing, or production readiness.
- `REQ-CLUSTER-017` — remains `@partial`: bounded current-generation repair and ADR 0031's bounded periodic current-generation fixed A/B/C discovery exist, while wider healing/topology coverage, rebalance, and automated orphan cleanup remain absent.
- `REQ-CLUSTER-027` — separately `@implemented-and-validated` under ADR 0031; it reuses this ADR's canonical job, fencing, retry, and data path without changing this decision.
- `REQ-DIST-004-A..C` — PA-6 modeled missing/corrupt-replica findings and healing plans; planning evidence only.
- `REQ-UPLOAD-006` — committed integrity metadata and single-pass GET corruption detection.
- `REQ-CLUSTER-019` and `REQ-CLUSTER-020` — implemented-and-validated S3 repair Business Need scenarios for real-process WebTestClient and AWS CLI modes.
- `REQ-CLUSTER-021`, `REQ-CLUSTER-022`, `REQ-CLUSTER-023`, `REQ-CLUSTER-025`, and `REQ-CLUSTER-026` — implemented-and-validated Ratis control, fencing, currentness, and snapshot Ability scenarios.
- `REQ-CLUSTER-024` — `@implemented-and-validated` crash/restart Ability for the complete seven-point real-filesystem and gRPC interruption matrix in the bounded independent-B-JVM validation mode.

## Related ADRs

- ADR 0014 — Storage Engine bounded context.
- ADR 0025 — Conditional chunking and storage-artifact taxonomy.
- ADR 0027 — Authoritative cluster control plane and direct quorum data path.
- ADR 0028 — First three-node cluster implementation baseline.
- ADR 0031 — Bounded periodic current-reference anti-entropy.
