# ADR 0030 — Deterministic storage-pipeline fault injection

Date: 2026-07-14

Amended: 2026-07-14

## Status

Accepted — the requesting user explicitly selected this target architecture and amended its sequencing on 2026-07-14.

Acceptance records the architectural decision only. Production implementation, shared executable fault requirements, Cucumber fault coverage, and semantic fault validation remain planned and need validation. The amendment splits delivery into two scopes: now that ADR 0033 validates fixed distributed EC 4+2 shard placement/transfer, an early bounded no-op-default plan/evidence kernel plus exact committed-shard unavailable/corruption actions may be implemented to support EC self-healing acceptance; generalized pipeline/cluster chaos remains final, after normal-path closure and its semantic/load/leak/cancellation gates. No fault-injection implementation, regression evidence, self-healing completion, or production-readiness claim follows from this status.

## Context

Storage integrity and cluster publication cross several distinct semantic boundaries: source checksum calculation, transforms, temporary persistence, persisted-byte verification, durability synchronization, atomic local publication, direct replica transfer, durable acknowledgement, and consensus publication of the object reference. A useful injected fault must identify the exact boundary and the side effects that have or have not happened there. A generic failure callback or a percentage-based random decision cannot establish that distinction.

The current mechanisms are separate and have different scopes:

- `FileSystemWriteFaultInjector` provides no-op-by-default filesystem hooks after selected temporary writes;
- `PropertyControlledFileSystemWriteFaultInjector` maps properties and a write-attempt counter to filesystem interruption, truncation, or ENOSPC behavior;
- `FaultInjectingStorageCluster` and `ChaosStrategy` provide broad random corruption, offline, and delay behavior; and
- `ReplicaTransferFaultPlan` provides a narrow replica-server interception seam.

A point-in-time planner and source audit at repository HEAD `2fa762c79e561dfae288836ae2c91ed005956fe6` on 2026-07-14 found these mechanisms fragmented and, where they use unseeded random percentage draws, unsuitable for replayable regression evidence. This is a static architecture observation, not a test result. Static occurrence counts are neither defect evidence nor implementation evidence.

The target must preserve the established storage contracts:

- ADR 0025's typed artifact representations and transform-aware integrity scopes;
- ADR 0027's authoritative control plane, direct data path, and publication only after checksum-valid durable acknowledgements;
- ADR 0028's fixed A/B/C baseline, `N=3`, `W=2`, no degraded writes, bounded framing, and payload exclusion from Ratis; and
- ADR 0029's current-generation fencing, durable exact-artifact publication, single-pass integrity behavior, and bounded scheduling; and
- ADR 0032's schema-3 EC stripe/shard identity, verified output-only local decoder, one-stripe memory bound, and explicit prohibition on repair publication.

Fault injection must also preserve single-pass streaming, explicit reactive-buffer ownership, cancellation cleanup, and finite scheduler, queue, frame, rule, evidence, target, and blast-radius budgets.

## Decision

### Application-owned fault-injection port

Introduce one transport-neutral application port named `StorageFaultInjectionPort`. Its contract and value types are owned by the application layer and are shared by the local storage pipeline and cluster application path without making either application depend on filesystem, gRPC, Ratis, Spring WebFlux, or protocol implementation types.

Conceptually, the port:

1. evaluates an immutable semantic event and returns either a typed no-fault decision or a typed fire decision with a stable decision ID;
2. never receives payload bytes;
3. lets the boundary adapter apply the returned action to bytes or side effects that the adapter exclusively owns; and
4. receives a sanitized typed outcome for evidence correlation.

The contract contains no `DataBuffer`, Netty buffer, protobuf message, gRPC observer, Ratis object, `Path`, file channel, HTTP request, or other transport/filesystem type. Boundary adapters map those details to opaque operation, artifact, representation, node, frame, offset, and semantic-point values. If physical module extraction is needed to share these pure application types, the extracted module remains an application contract rather than a domain, protocol, or infrastructure module.

`StorageFaultInjectionPort.disabled()` is the mandatory default. It has no plan, counter, scheduler, payload mutation, filesystem/network side effect, or evidence queue and always returns no fault. Ordinary production composition uses this no-op implementation.

### Versioned, closed plan and action model

The initial serialized contract is schema version `1`. It uses closed enums or sealed types for semantic points, selectors, decisions, outcomes, and actions. Free-form stage names such as `StorageStage.name()`, class names, exception messages, and adapter method names are not plan selectors.

An immutable version-1 plan contains at least:

- a validated opaque `planId`;
- a SHA-256 hash of the canonical plan representation;
- a finite list of rules with stable rule IDs;
- a finite operation scope or finite operation budget;
- a finite evaluation/evidence-record budget;
- an exact typed semantic point and named byte representation;
- an exact opaque operation identity, or a bounded binding that resolves to one before evaluation;
- an applicable one-based scoped occurrence, absolute byte offset, frame index and in-frame offset, or a closed combination of those coordinates;
- an optional finite target scope such as a replica node or artifact identity;
- a positive finite maximum fire count; and
- exactly one version-1 action that is valid at that point.

The deterministic match key is derived from the plan hash, rule ID, stable operation identity, typed semantic point, target scope, and logical coordinate. An occurrence is scoped to that plan rule, operation, point, and target; it is never a process-global request or callback count. Stream offsets and frame indexes are assigned from logical byte order before concurrent hand-off. Retries retain the stable operation identity and receive explicit attempt/occurrence coordinates. Re-evaluating the same event ID is idempotent and does not consume another fire count.

No regression decision uses process-global randomness, an unseeded percentage, or a shared seeded random-number generator. A shared seeded generator remains schedule-dependent when concurrent callbacks consume draws in a different order. Probabilistic chaos may remain a separately labelled exploratory mode, but its outcomes are not deterministic regression evidence.

### Initial version-1 actions

Version 1 enables only these three point/action combinations:

| Action | Allowed semantic point | Exact semantics |
|---|---|---|
| `FLIP_SOURCE_BIT` | `AFTER_SOURCE_CHECKSUM_BEFORE_PERSISTENCE` | After the selected source byte has contributed exactly once to the named source checksum and length, but before that selected byte is consumed by a transform, temporary writer, or replica persistence path, flip one selected bit at one bounded absolute offset. Do not recompute the source checksum. The adapter mutates only exclusively owned bytes or an explicit copy. |
| `CORRUPT_UNPUBLISHED_TEMP_BYTE` | `AFTER_TEMP_WRITE_BEFORE_PERSISTED_BYTE_VERIFICATION` | After the complete intended representation has been written to its unpublished temporary artifact, but before the persisted-byte verifier reads any byte, apply one bounded non-zero XOR mutation at one absolute file offset. Await completion of the mutation before verification starts. The temporary artifact remains unpublished. |
| `MUTATE_REPLICA_FRAME_BYTE` | `AFTER_REPLICA_FRAME_RECEIVE_BEFORE_SINK_VERIFICATION` | After the receiver has accepted and mapped the selected frame and fixed its operation ID, frame index, and byte offset, but before the sink updates length/checksum state or writes that frame, mutate one selected bit in an exclusively owned frame or copy. The sink must verify the mutated stream against the original expected facts. |

Offsets, frame indexes, in-frame offsets, bit indexes, XOR masks, occurrences, rule counts, operation budgets, evaluation budgets, and maximum fire counts are finite and validated. A runtime coordinate that is valid in the plan but absent from the actual finite representation produces a typed skipped outcome; it does not choose another byte. A rule cannot silently broaden itself to a different operation, frame, offset, target, stage, or action.

These version-1 actions inject corrupt data only before the relevant publication or acknowledgement gate. They do not bypass checksum comparison, create an acknowledgement, alter quorum policy, or write an object reference.

### Planned early committed-EC-shard extension

EC self-healing needs a later at-rest boundary that version 1 deliberately cannot express. With `REQ-CLUSTER-015` now supplying authoritative fixed EC 4+2 shard placement and direct transfer, a planned schema version `2` may add only these two action combinations before EC self-healing is implemented:

| Action | Allowed semantic point | Exact semantics |
|---|---|---|
| `MAKE_COMMITTED_EC_SHARD_UNAVAILABLE` | `AFTER_EC_MANIFEST_COMMIT_BEFORE_SHARD_HEALTH_PROBE` | Resolve one exact schema-3 committed shard identity and atomically quarantine its data plus integrity sidecar inside the experiment-owned quarantine root so the selected location is observably absent. Preserve a restorable pristine copy and await directory durability before the health probe. Do not delete or alter the manifest/reference. |
| `CORRUPT_COMMITTED_EC_SHARD_BYTE` | `AFTER_EC_MANIFEST_COMMIT_BEFORE_SHARD_HEALTH_PROBE` | Resolve one exact schema-3 committed shard identity, preserve a restorable pristine copy, apply one bounded non-zero XOR at one validated offset in the selected committed shard, fsync the mutation and directory as required, and await completion before the health probe. Do not update the committed checksum, manifest, reference, or location. |

A version-2 target is not a raw path or wildcard. It binds the canonical plan/rule and stable experiment operation to the committed manifest/generation identity, stripe index, shard index, artifact ID, transport-neutral node/device location, stored length, and SHA-256. The owning adapter resolves that tuple under its configured storage root and rejects path escape, symlinks outside the root, a changed generation/layout/checksum, an arbitrary filename, or more targets than the finite plan declares.

The ordinary recoverable blast radius is at most `m` shards in one stripe. A separately declared `EXPECT_BLOCKED` experiment may target exactly `m+1` shards only to prove insufficient-survivor detection; it forbids repair publication, remains limited to one stripe, and must restore every target during lifecycle-owned teardown. No plan may target metadata, object references, unrelated stripes/objects, an unbounded set, or a production profile. Quarantine/restoration outcomes are deterministic, fsync-aware, idempotent, and represented by closed redacted evidence codes. A failed mutation, quarantine, evidence write, or restoration fails the experiment visibly and never broadens target selection.

This early subset provides test tooling, not the self-healing scanner, consensus job lifecycle, transfer, or shard publication behavior being tested. Requirements for the kernel/actions must be written or refreshed before implementation. General delays, process/network partitions, cancellation, exceptions, short I/O, arbitrary filesystem mutation, acknowledgement faults, Ratis faults, and probabilistic chaos remain later generalized work.

### Planned semantic-point catalog

The following names and side-effect meanings are the target catalog. A point is selectable only when its schema version explicitly permits an action there. Version 1 permits only the three combinations above; the remaining points reserve precise architecture vocabulary and do not enable a latent generic action.

For every `BEFORE_*` point, the named side effect has not started. For every `AFTER_*` point, the named effect completed successfully and cannot be treated as rolled back merely because a later injected action fails. An `AFTER_*` event is not emitted when the named effect itself failed. Adapter hand-off, remote receipt, durability, quorum acceptance, and consensus commit are separate effects and must not be conflated.

| Semantic point | Side-effect state at evaluation |
|---|---|
| `BEFORE_SOURCE_CHECKSUM` | The selected source frame and logical coordinates exist, but none of its selected bytes has updated the named checksum/length state or reached a downstream consumer. |
| `AFTER_SOURCE_CHECKSUM_BEFORE_PERSISTENCE` | The original selected bytes have updated the named source checksum/length exactly once; the selected bytes have not yet been consumed by the next transform or persistence boundary. |
| `BEFORE_TRANSFORM` | The typed transform ID, ordinal, input representation, and frame coordinate are fixed; the transform has not consumed the selected input or emitted corresponding output. |
| `AFTER_TRANSFORM` | The transform successfully produced the selected named output representation and its ownership disposition is known; no later checksum or persistence stage has consumed that output at this boundary. |
| `BEFORE_TEMP_WRITE` | The unpublished staging identity may exist, but the selected payload write has not been issued to it. |
| `AFTER_TEMP_WRITE_BEFORE_PERSISTED_BYTE_VERIFICATION` | All intended bytes were written successfully to the unpublished temporary artifact; no persisted-byte verification, file fsync, atomic publication, or durable acknowledgement has started. |
| `BEFORE_PERSISTED_BYTE_VERIFICATION` | Temporary writes and any selected pre-verification mutation have completed; the verifier has not read the temporary representation. |
| `AFTER_PERSISTED_BYTE_VERIFICATION` | A complete reread verified the expected representation length and checksum; file fsync and publication have not yet started. |
| `BEFORE_FILE_FSYNC` | Exact verified temporary bytes exist, but file `force`/fsync has not been invoked. |
| `AFTER_FILE_FSYNC` | File `force`/fsync returned successfully for the verified temporary artifact; the final publication move has not started. |
| `BEFORE_ATOMIC_PUBLICATION` | The exact verified and file-synchronized temporary artifact remains unpublished; the atomic move/replacement has not been invoked. |
| `AFTER_ATOMIC_PUBLICATION` | The atomic move/replacement returned successfully and only the complete verified artifact can occupy the final local path; parent-directory fsync and durable acknowledgement have not completed. |
| `BEFORE_PARENT_DIRECTORY_FSYNC` | Atomic local publication completed, but synchronization of the containing directory entry has not been invoked. |
| `AFTER_PARENT_DIRECTORY_FSYNC` | Synchronization of the published directory entry returned successfully; no durable acknowledgement is implied until its separate gate. |
| `BEFORE_REPLICA_FRAME_SEND` | The immutable application frame has a stable operation ID, frame index, offset, and bounded length; it has not been handed to the transport adapter. |
| `AFTER_REPLICA_FRAME_SEND` | The transport adapter accepted ownership of the frame; remote receipt, sink verification, local durability, and acknowledgement are not implied. |
| `AFTER_REPLICA_FRAME_RECEIVE_BEFORE_SINK_VERIFICATION` | The receiving adapter accepted, bounded, and mapped the frame; sink digest/length update and staging write have not consumed it. |
| `AFTER_REPLICA_FRAME_SINK_ACCEPTANCE` | The sink consumed the selected frame into incremental verification/staging; successful verification or durability of the complete artifact is not yet implied. |
| `BEFORE_DURABLE_ACK_EMIT` | The target has exact checksum-valid bytes, successful persisted-byte verification, file fsync, atomic publication, and required directory fsync; it has not emitted an acknowledgement. |
| `AFTER_DURABLE_ACK_EMIT_BEFORE_ACCEPTANCE` | The target handed the acknowledgement to its return transport; the coordinator has not necessarily received, validated, deduplicated, or counted it. |
| `AFTER_DURABLE_ACK_ACCEPTANCE` | The coordinator validated and counted the acknowledgement exactly once for the stable operation and current epochs; reaching `W=2` and reference publication remain separate facts. |
| `BEFORE_RATIS_REFERENCE_PROPOSAL` | Exactly three targets were selected, at least two matching durable acknowledgements are available, and fencing/epochs are current; no reference command has been submitted. |
| `AFTER_RATIS_REFERENCE_PROPOSAL_BEFORE_COMMIT` | The command was submitted/accepted for consensus processing, but commit is not established. A timeout or cancellation here is an uncertain result that requires a committed-state query. |
| `AFTER_RATIS_REFERENCE_COMMIT` | The new reference generation is consensus-committed and authoritative. A later local failure cannot truthfully reclassify it as uncommitted. S3 response completion is a separate boundary. |
| `AFTER_EC_MANIFEST_COMMIT_BEFORE_SHARD_HEALTH_PROBE` | A schema-3 EC manifest/generation and its exact shard facts are committed and authoritative; the selected at-rest shard has not yet been evaluated by the bound self-healing health probe. This point is selectable only by schema version 2's two committed-shard actions. |

Checksum points always name the representation being hashed, such as logical source bytes, transformed stored bytes, ciphertext, whole object, shard, or replica artifact. This preserves ADR 0025's artifact taxonomy and prevents a source-stream checksum from being presented as persisted-byte or at-rest evidence.

### Explicit activation and fail-closed plan loading

An active implementation requires both:

1. a dedicated Spring profile explicitly classified as non-production fault-injection use; and
2. a separate opt-in enable property set to true.

The logical condition is an AND. With no explicit activation, composition selects the no-op port. Supplying a plan or requesting activation without both guards is rejected rather than silently accepted. A production profile cannot coexist with the active injector; startup fails before serving requests if that combination is attempted.

Active plans are immutable for one process run. For a multi-node experiment, every participating node must load the same canonical plan ID/hash before the operation begins, and the stable operation identity must propagate across application and adapter boundaries. A missing or mismatched active plan fails the experiment closed; it does not silently run a different rule set. This metadata does not put payload bytes into Ratis.

Plan loading fails closed on:

- an unknown schema version;
- an unknown semantic point, stage, boundary, action, outcome, or representation;
- a point/action combination not allowed by that schema version;
- a duplicate plan/rule ID or non-canonical plan hash;
- a missing, ambiguous, negative, overflowing, wildcard-unbounded, or otherwise malformed selector;
- a zero, negative, overflowing, or unbounded fire/evaluation/operation/evidence limit; or
- a parameter outside the action's finite range.

Failure means rejecting activation/startup or the explicitly bound experiment before payload processing. It never means falling back from a requested active plan to no-op behavior.

### Evidence contract and data minimization

Each evaluated event emits a bounded structured record, or a correlated append-only decision/outcome pair, containing:

- evidence schema version;
- plan ID and canonical plan hash;
- rule and decision IDs;
- typed semantic point, named representation, and action;
- opaque stable operation identity and optional opaque target identity;
- scoped occurrence, byte offset, frame index, and in-frame offset when applicable;
- configured maximum fire count and the bounded fire ordinal/count;
- `FIRED` or `SKIPPED` with a closed reason code; and
- typed action and pipeline outcomes, including whether the action was applied, detected, rejected, cancelled, failed, or followed by a committed/non-committed result when that fact is actually known.

The replay oracle uses logical IDs, coordinates, decisions, and outcome codes, not wall-clock order. A timestamp may be supplemental diagnostic metadata but cannot participate in matching.

Evidence contains no payload bytes or byte excerpts, bucket/object keys, filesystem paths, HTTP headers, credentials, tokens, certificates, encryption/signing keys, secrets, or free-form exception messages. Opaque identifiers must not be derived by copying those values. Evidence persistence uses a finite record budget and bounded queue. In active mode, inability to preserve a required fired/outcome record fails the experiment explicitly rather than dropping the record and claiming deterministic evidence.

### Consolidation of existing seams

Implementation will evolve the current mechanisms behind `StorageFaultInjectionPort`:

- filesystem adapters will map `FileSystemWriteFaultInjector` semantic contexts to the application contract, and `PropertyControlledFileSystemWriteFaultInjector` compatibility settings may translate only to equivalent finite typed rules;
- `ReplicaTransferFaultPlan` will become an adapter at the replica boundary rather than an independent source of fault decisions;
- `FaultInjectingStorageCluster` and `ChaosStrategy` will either delegate through an explicitly exploratory adapter or be retired; and
- after migration, no legacy class may independently select, randomize, apply, or report a fault outside the application plan and evidence contract.

Broad unseeded probabilistic corruption, offline, and delay behavior is exploratory scaffolding. It may support separately labelled chaos exploration, but it cannot satisfy a deterministic requirement, replay gate, or regression-evidence claim. It is not automatically translated into a version-1 deterministic plan.

### Safety, integrity, ownership, and scheduling invariants

Fault injection does not weaken the normal write protocol:

1. the placement policy still selects exactly `N=3` targets and degraded writes remain disabled;
2. only checksum-valid, durably published target results can become acknowledgements;
3. the coordinator still requires `W=2` distinct matching durable acknowledgements under current epochs;
4. no object-reference proposal precedes those acknowledgements and fencing revalidation;
5. object payload bytes remain outside the Ratis log and snapshots; and
6. S3 success still follows consensus commit of the reference generation.

A corrupted replica never contributes a false durable acknowledgement. One rejected replica may still leave two valid acknowledgements and permit the existing `W=2` policy; if fewer than two valid acknowledgements remain, publication fails. This is the fixed quorum policy, not degraded-write fallback.

Injected failure or future injected cancellation must not expose a partial final artifact, manufacture a durable acknowledgement, or make an unpublished artifact reachable through a committed reference. Temporary partial bytes remain unreachable and are cleaned or quarantined according to the normal pipeline contract. A failure after successful atomic publication may leave a complete checksum-valid orphan, as already allowed by ADRs 0027 and 0028, but never a reachable partial artifact or a reference lacking the required durable acknowledgements.

The planned committed-shard actions occur only after authoritative EC manifest commit and therefore model at-rest loss/corruption; they do not retroactively invalidate the original durable acknowledgement or authorize a replacement acknowledgement. Detection, consensus-owned repair work, verified transfer, atomic shard publication, and manifest/currentness fencing remain separate self-healing behavior. Experiment teardown restores the exact pre-fault bytes/location independently of whether the product repair path succeeded, failed, or reported `BLOCKED`.

Fault evaluation is finite, deterministic, non-blocking, and does not sleep or perform file/network I/O. Blocking mutation and bounded evidence persistence run only at the owning infrastructure boundary on an explicitly finite blocking scheduler/queue, never on a Reactor event-loop thread. Queue saturation is an explicit active-experiment failure.

The source mutation does not add a second subscription or aggregate the payload. The existing persisted-byte verification reread remains a distinct integrity gate rather than being mislabeled as another source pass. Transform and checksum events carry an explicit representation scope.

A `DataBuffer`, Netty buffer, or replica frame may be mutated only while exclusively owned. If ownership is shared, pooled, sliced, retained elsewhere, or uncertain, the adapter copies the bounded selected frame before mutation. Every success, error, cancellation, discard, skipped action, and evidence-failure path must release or transfer each buffer reference exactly once. No action may retain payload buffers in the plan evaluator or evidence recorder.

### Actions deferred beyond the bounded schemas

Version 1 does not enable generic delay, cancellation, exception, short-I/O, fsync-failure, atomic-publication-failure, acknowledgement-failure, or Ratis-publication actions. Planned version 2 adds only the two exact committed-EC-shard actions above; it does not inherit a generic mutation or failure callback.

Each other future action requires separate bounded semantics and validation before a later contract version permits it. In particular:

- delay requires a finite maximum duration, deadline interaction, cancellation awareness, and bounded queued bytes/tasks;
- cancellation requires an exact propagation boundary, ownership cleanup, and acknowledgement/publication fencing;
- exception injection requires a closed typed error catalog and an exact before/after side-effect point;
- fsync failure must distinguish file from parent-directory synchronization and whether the call was not invoked, failed, or completed;
- atomic publication failure must distinguish no move, completed move, and uncertain completion; and
- Ratis publication failure must distinguish no proposal, submitted/unknown, rejected, and committed reference state.

No generic action is silently available merely because a semantic point is present in the planned catalog.

## Consequences

- The same plan and logical operation/event sequence can be replayed without relying on process-global random draw order or thread scheduling.
- Fault reports can state exactly which representation and side-effect boundary fired, was skipped, and produced an observed outcome.
- Local filesystem, reactive pipeline, replica gRPC, and consensus-adjacent seams gain one application-owned vocabulary while retaining adapter-specific byte and resource handling.
- Stable operation identity, plan propagation, canonical hashing, rule validation, bounded evidence, and compatibility adapters add implementation complexity.
- Pipeline refactoring must preserve a point's documented semantics or introduce a new versioned point; moving a hook without changing its contract is not permitted.
- The no-op production path remains the default, while attempted production activation or malformed active plans fail before request processing.
- Existing broad random chaos remains useful only as explicitly exploratory scaffolding and cannot upgrade deterministic requirement status.
- The early committed-shard subset enables deterministic self-healing tests without making generalized chaos an implementation prerequisite for every normal cluster path.
- Version-2 target identity, quarantine/restoration, and blast-radius rules add infrastructure and lifecycle complexity and remain planned.
- The reserved point catalog creates no current authorization to inject delays, cancellation, exceptions, fsync failures, publication failures, or arbitrary at-rest mutations.
- Until the planned fault requirements and gates pass, this amendment changes architectural authority and sequencing only and does not change any fault-injection or self-healing requirement implementation status.

## Alternatives Considered

### Keep independent filesystem, cluster decorator, and replica-transfer hooks

Not selected. Independent hooks can remain temporary compatibility adapters, but they do not provide one versioned selector model, stable operation scope, point semantics, fire budget, or correlated evidence record across the complete pipeline.

### Use unseeded percentages or one shared seeded random generator

Not selected for regression evidence. Unseeded draws are not replayable, and one shared seeded generator is still schedule-dependent when concurrent operations consume draws in a different order. Such mechanisms may remain only in separately labelled exploratory chaos.

### Put `DataBuffer`, gRPC, filesystem, or Ratis types in the plan contract

Not selected. It would couple application semantics to adapters, impede reuse across local and clustered paths, and make buffer ownership and protocol evolution part of the plan schema. Adapters instead map transport details to closed semantic values.

### Provide a generic callback or arbitrary script at every point

Not selected. An unrestricted callback/script cannot be validated as a closed action set, bounded for blast radius and scheduling, safely redacted, or given stable before/after side-effect semantics.

### Rely only on external network, process, or filesystem chaos

Not selected as the deterministic semantic mechanism. External chaos remains useful for a separate realistic validation layer, but it cannot reliably target boundaries such as post-source-checksum/pre-persistence mutation or pre-acknowledgement sink verification.

## Evidence

Planning evidence only was reviewed on 2026-07-14 at repository HEAD `2fa762c79e561dfae288836ae2c91ed005956fe6`:

- `git rev-parse HEAD` identified that revision;
- static source inspection located `FileSystemWriteFaultInjector`, `PropertyControlledFileSystemWriteFaultInjector`, `FaultInjectingStorageCluster`, `ChaosStrategy`, and `ReplicaTransferFaultPlan` with the separate responsibilities summarized in Context; and
- the existing ADRs establish the integrity, fixed-quorum, direct-transfer, Ratis-reference, single-pass, and bounded-execution constraints preserved here.

No Maven, Cucumber, WebTestClient, AWS CLI, multi-node, replay, fault-action, buffer-leak, scheduler-saturation, or production-profile validation was run for this ADR. The source audit is not semantic implementation evidence.

Every fault-injection validation gate is currently **planned / needs-validation**, including:

- fail-closed schema, selector, activation, profile, and cross-node plan-hash validation;
- exact committed schema-3 target binding with no raw-path/wildcard selection, path escape, generation drift, or metadata mutation;
- recoverable `m`-shard and explicit one-stripe `EXPECT_BLOCKED` blast-radius enforcement;
- deterministic committed-shard quarantine, corruption, fsync, restoration, idempotence, teardown-failure, and redacted evidence outcomes;
- identical decision traces for repeated plans and logical event sequences;
- isolation of parallel operations, retries, occurrences, frames, offsets, targets, and fire budgets;
- positive fired/skipped evidence for all three version-1 actions;
- source-mutation and temporary-corruption rejection before local publication;
- replica-frame corruption exclusion from durable acknowledgement and publication refusal below `W=2`;
- preservation of exact `N=3/W=2` ordering, payload exclusion from Ratis, and no reachable partial artifact;
- single-subscription behavior, representation-correct integrity, and pooled-buffer ownership on success, error, cancellation, discard, and skipped paths;
- finite scheduler/queue behavior and explicit saturation outcomes;
- no-op default equivalence and rejection of production activation; and
- evidence completeness, boundedness, canonical plan hashing, and secret/payload redaction.

Shared Cucumber requirements/specifications will be written or refreshed before implementation, following the repository's requirement-first policy. Externally observable S3 behavior will use shared `Business Need` scenarios and the agreed WebTestClient/AWS CLI modes where applicable; internal plan, point, buffer, filesystem, replica, consensus-boundary, committed-shard, and restoration mechanisms will use `Ability` specifications. No new fault-injection requirement ID or validated fault status is assigned by this ADR.

ADR 0032 and `REQ-PIPELINE-017` provide validated prerequisite identity/decoder evidence—5 scenarios / 46 steps, including all 15 four-of-six combinations—but they do not execute this port or either planned committed-shard action.

## Related ADRs

- ADR 0025 — Conditional chunking and storage-artifact taxonomy.
- ADR 0027 — Authoritative cluster control plane and direct quorum data path.
- ADR 0028 — First three-node cluster implementation baseline.
- ADR 0029 — Consensus-owned durable repair for the current whole-object generation.
- ADR 0032 — Bounded local erasure-coded reconstruction.
