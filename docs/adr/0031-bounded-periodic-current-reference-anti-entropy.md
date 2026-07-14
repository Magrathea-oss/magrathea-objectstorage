# ADR 0031 — Bounded periodic current-reference anti-entropy

Date: 2026-07-14

## Status

Accepted — implementation-informed for the bounded fixed A/B/C current-reference discovery scope in `REQ-CLUSTER-027`.

Both `REQ-CLUSTER-027` scenarios are `@implemented-and-validated`; the focused `ReqCluster027CucumberTest` passes **2 scenarios / 36 steps**. Broad `REQ-CLUSTER-017` remains `@partial`: this decision implements periodic inspection and repair only for current whole-object replica obligations already named on fixed A/B/C. Rebalance, automated orphan cleanup, wider healing/topology coverage, dynamic membership, erasure coding, broader partitions, production scale, and production readiness remain absent or unvalidated. `REQ-CLUSTER-006`, `REQ-CLUSTER-007`, `REQ-CLUSTER-015`, `REQ-CLUSTER-016`, and `REQ-CLUSTER-018` remain `@not-implemented`.

## Context

ADR 0028 establishes the fixed A/B/C Ratis control plane and direct whole-object replica data path. ADR 0029 adds one consensus-owned, deduplicated, fenced repair lifecycle for a missing or corrupt target already promised by the current committed `ObjectReferenceGeneration`. Those decisions did not define how a running node periodically discovers damaged obligations outside an S3 request.

The discovery mechanism must remain smaller than broad healing. It must not materialize every reference, create a second repair authority, persist a scan cursor as consensus state, treat PA-6 planner output as executed work, copy payload bytes through Ratis, or imply rebalance and cleanup. It also must tolerate query, local-probe, ensure, repair, restart, and reference-race failures without skipping a failed page or allowing stale work to publish.

## Decision

### Bounded current-reference pages from Ratis

Add a transport-neutral `currentReferences(ReferencePageQuery)` read port and implement it as a read-only Ratis query over the state machine's current `ObjectReferenceGeneration` map.

- Results contain only the current reference for each namespace key and are ordered canonically by bucket and object key; the cursor also carries the observed generation as exact last-seen identity.
- The optional cursor is exclusive. Every non-empty page returns a cursor equal to its last reference, and the next page contains only namespace keys strictly after it.
- The caller supplies a positive finite page limit. The application and codec reject limits above the hard maximum of 256, malformed cursors or encodings, trailing command/page input, oversized decoded pages, inconsistent page cursors, and non-terminal empty pages.
- A page is terminal only when no later current reference remains. No cycle materializes the complete reference set.
- Paging is a sequence of bounded reads of applied current state, not a consensus snapshot spanning all pages. Concurrent reference changes are handled by currentness fences and subsequent first-page cycles rather than by claiming one globally frozen scan.

The cursor is query input only. It is never appended to the Ratis log, stored in a Ratis snapshot, or treated as durable cluster progress. Object payload bytes likewise remain absent from Ratis.

### Process-local periodic traversal

Each fixed node owns one `ClusterAntiEntropyScheduler`, started and stopped by `ClusterNodeRuntime` with the node process. The implemented defaults are a 30-second fixed delay and page size 16; configuration must retain a positive interval and a page size from 1 through 256. These defaults are bounded implementation values, not production sizing guidance.

A newly constructed scheduler starts before the first page. It keeps only one process-local exclusive cursor and advances it only after every record in the returned page has completed inspection and every required repair ensure has completed durably. A terminal page resets the cursor so the next periodic cycle starts from the first page. Closing and reconstructing the scheduler also starts from the first page; prior cursor memory is neither persisted nor trusted.

This deliberate restart-from-first behavior may repeat observations. Repetition is safe because ADR 0029's canonical consensus repair identity and command deduplication remain authoritative.

### Per-node named-obligation probing

For each page, a node inspects only references whose committed replica UUID set names that local node. A reference that does not name the node is neither probed nor repaired by that node's scheduler.

The local artifact probe checks existence and, when present, the complete committed length and SHA-256. Blocking filesystem probes run on Reactor `boundedElastic`, not on a caller or Reactor event-loop thread. An exact target requires no ensure, claim, replica read, or replacement publication. A missing or invalid target is passed to the existing `ClusterRepairCoordinator`, which revalidates the current exact reference before durably ensuring the canonical target-specific repair job.

This is application orchestration over committed facts. PA-6 `AntiEntropyPlanner` findings, plans, and `HealingTask` values remain side-effect-free planner output and are not counted as an ensure, claim, transfer, publication, repair success, or execution result.

### Serial and bounded processing

One scheduler cycle owns at most one active page query. Records in that page are processed with serial concat-style continuation, and one node has at most one active local-target probe in the discovery scheduler. The existing repair scheduler separately performs bounded serial scans and dispatches consensus-owned work. Overlap attempts are rejected and counted rather than increasing concurrency silently.

The page-size bound, one-page cycle, serial record processing, one active target, finite repair scans, fixed-delay scheduling, and lifecycle cancellation are the implemented backpressure boundary. They do not establish fleet-scale fairness, throughput, memory sizing, or production capacity.

### Reuse the consensus repair authority and data path

Discovery creates no second job schema or repair executor. Missing/corrupt observations delegate to ADR 0029 unchanged:

- one logical job is deduplicated by `(REPAIR, bucket, objectKey, referenceGeneration, artifactId, targetNodeId)`;
- Ratis owns `READY`, `CLAIMED`, `RETRY_WAIT`, `SUCCEEDED`, `BLOCKED`, and `OBSOLETE` lifecycle state plus command deduplication;
- stable node UUID, process session, and monotonic `claimGeneration` fence each attempt;
- workers recheck the exact current reference before source selection, target publication, and success;
- a different named verified replica supplies bytes over the existing grpc-java data path with UUID-bound mutual TLS; and
- the target is published only after incremental length/SHA-256 verification, file fsync, atomic publication/replacement, and parent-directory fsync.

Neither discovery nor repair rewrites the current reference. A changed generation rejects a stale ensure or makes already-ensured work `OBSOLETE`; a later first-page cycle discovers the new current obligation under its distinct canonical identity.

### Failure and restart semantics

- A page-query, local-probe, or repair-ensure failure aborts that page before cursor advancement. The same page range is eligible for a later periodic retry.
- A repair execution failure remains explicit consensus-owned retryable or blocked repair state. Discovery does not count that failure as repair success and does not bypass the repair scheduler.
- A terminal page resets traversal to the first page; it does not poll forever after a terminal cursor.
- Shutdown cancels an active delayed page cycle and leaves no active page or target probe. Scheduler reconstruction starts from the first page.
- A reference change before ensure is rejected as stale. A change after transfer but before publication is caught by the existing publication fence; stale bytes cannot become authoritative.
- Duplicate first-page observations after restart are expected and deduplicate through consensus.

These semantics favor safe retry over durable scan progress. They do not promise exactly-once observation or a transactionally consistent multi-page sweep.

### Observability

Expose bounded process-local counters for cycles, completed cycles, pages, named records, exact/missing/corrupt targets, ensures, query/probe/ensure/repair failures, retries, cursor resets, stale references, deduplications, and overlap attempts, plus active-page, active-target, closed, and last-failure-stage status.

Status carries no object payload, credential, certificate, repair specification, or cursor value. Failure reporting must not log object bytes or credentials. Counters are operational evidence only; they are not durable job ownership and do not replace Ratis repair state.

### Security and external-boundary constraints

This decision adds no public or Admin object endpoint. Current-reference paging stays on the existing internal Ratis control connection protected by the fixed-cluster mTLS and stable-UUID checks. Repair bytes stay on the separate existing grpc-java replica connection with UUID-bound mutual TLS. Local probes access only the node's configured artifact store. No credential, key, certificate, token, or payload is exposed in scheduler status.

Test-local certificates prove only the focused environment. Production enrollment, key custody, trust distribution, rotation, revocation, expiry monitoring, and recovery remain outside this decision.

### Explicit exclusions

This decision does not implement or claim:

- a consensus-owned or persisted anti-entropy cursor;
- one immutable consensus snapshot spanning a complete multi-page scan;
- object payload storage or transfer through Ratis;
- PA-6 `AntiEntropyPlanner` execution or planner-derived completion evidence;
- placement changes, rebalance, reference rewriting, or adding a new replica obligation;
- automated orphan cleanup, prepared-artifact retirement, or superseded-generation collection;
- dynamic membership, node replacement, wider topology discovery, or production-scale scheduling/fairness;
- erasure-coded shards, chunks, multipart, conditional/versioned cluster operations, or broad partitions;
- ADR 0030 deterministic storage-pipeline fault injection or general chaos; or
- distributed production readiness.

## Consequences

- Fixed A/B/C nodes now discover and repair missing or corrupt current named replicas without waiting for an S3 read.
- Read-only exclusive pages bound control-plane response size and scheduler work, while restart-from-first avoids a new durable cursor protocol.
- At-least-once observations and non-snapshot paging can repeat work, but consensus repair identity, command deduplication, generation checks, and claim fencing make repetition safe.
- A failed page intentionally makes no cursor progress, which prevents silent skipping but can delay later keys until the failing obligation recovers or is changed.
- Per-node scanning duplicates control queries across A/B/C. That is accepted for the fixed topology and is not production-scale evidence.
- Existing repair security, data transfer, publication, retry, and audit semantics remain centralized rather than duplicated in discovery.
- EP-10 and broad `REQ-CLUSTER-017` remain partial because wider healing/topologies, rebalance, and cleanup still require separate requirement-first slices and decisions.

## Alternatives Considered

### Persist traversal progress in consensus

Rejected for this slice. A consensus cursor would add replicated liveness state, ownership, failover, schema migration, compaction, and fairness semantics without improving repair safety. Repeated discovery already deduplicates through the authoritative repair job.

### Put reference payloads or object bytes in Ratis

Rejected. Ratis returns bounded reference metadata only. Whole-object bytes remain on the direct replica data path to avoid log, snapshot, quorum-bandwidth, and recovery amplification.

### Execute PA-6 `AntiEntropyPlanner` and report its plans as repairs

Rejected. Planner values are policy output, not durable ownership or side-effect evidence. Only accepted consensus commands and verified fenced publication can establish repair progress or success.

### Materialize all references and probe them in parallel

Rejected. It would remove the bounded page/serial-work proof, increase memory and filesystem/RPC concurrency, complicate cancellation, and make overlap/failure behavior harder to review.

### Add a separate anti-entropy repair state machine

Rejected. ADR 0029 already supplies canonical deduplication, lifecycle, fencing, retries, currentness checks, and verified publication. A second authority would create conflicting ownership and completion semantics.

### Implement rebalance and cleanup in the same slice

Rejected. Rebalance changes placement authority; cleanup irreversibly deletes unreachable bytes and requires separate retirement fences. Both require independent requirement-first scenarios and decisions.

## Evidence

Evidence was observed on 2026-07-14 in a dirty working tree and is bounded to the selected requirement:

- `ReqCluster027CucumberTest` passed **2 scenarios / 36 steps** with real three-voter Ratis current-reference paging, real filesystem targets, actual grpc-java repair reads protected by UUID-bound mTLS, consensus/filesystem inspection, and query/probe/ensure/repair/page/overlap/replica-read counters.
- The gate covers canonical exclusive paging, codec fail-closed behavior, fixed-node named-obligation filtering, serial bounds, lifecycle cancellation, process-local restart-from-first, query/probe/ensure/repair failures, deduplication, current-reference races, and exact target publication.
- Existing bounded regressions remained green in the implementation validation set: `ReqCluster024CucumberTest` **7 scenarios / 168 steps**, repair control **22 / 294**, and data plane **4 / 40**.

Ordinary root Maven success is supporting integration evidence only. None of this evidence is a production-scale, general-chaos, broad-partition, rebalance, cleanup, or production-readiness result.

## Related Requirements

- `REQ-CLUSTER-027` — `@implemented-and-validated` for the two bounded fixed A/B/C current-reference discovery scenarios.
- `REQ-CLUSTER-017` — remains `@partial`; bounded periodic current-generation fixed A/B/C anti-entropy is implemented, while rebalance, automated orphan cleanup, and wider healing/topology coverage remain absent.
- `REQ-CLUSTER-019..026` — existing implemented-and-validated current-generation repair and fencing requirements reused by this decision.
- `REQ-CLUSTER-006/007/015/016/018` — remain `@not-implemented`.
- `REQ-DIST-004` — PA-6 modeled anti-entropy findings/plans remain domain planning evidence only.

## Related ADRs

- ADR 0027 — Authoritative cluster control plane and direct quorum data path.
- ADR 0028 — First three-node cluster implementation baseline.
- ADR 0029 — Consensus-owned durable repair for the current whole-object generation.
- ADR 0030 — Deterministic storage-pipeline fault injection; accepted as final/deferred chaos work and explicitly not implemented by this decision.
