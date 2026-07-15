# ADR 0032 — Bounded local erasure-coded reconstruction

Date: 2026-07-14

## Status

Accepted — implementation-informed for the local output-only reconstruction scope in `REQ-PIPELINE-017`.

All five `REQ-PIPELINE-017` scenarios are `@implemented-and-validated`; the focused `ReqPipeline017EcReconstructionCucumberTest` passes **5 scenarios / 46 steps**. This decision establishes a transport-neutral decoder for one committed EC stripe and schema-3 reconstruction metadata. It does not itself implement repair publication, a scanner or self-healing daemon, distributed shard placement or transfer, Ratis repair ownership, rebalance, orphan cleanup, ADR 0030 fault injection, or generalized chaos. Subsequent ADR 0033 implements fixed A/B/C EC 4+2 placement/transfer under `REQ-CLUSTER-015`; EP-10 remains `@partial`.

## Context

`REQ-PIPELINE-015` physically writes local EC 4+2 data and parity shards, but reconstruction needs more than an ordered list of typed artifacts. A decoder must know the committed stripe index, shard index, `k`, `m`, parity role, logical stripe length, stored length, stored checksum, and transport-neutral location for every shard. Inferring these facts from artifact order or a local filesystem path would make reconstruction ambiguous and would not extend safely to later distributed placement.

Reconstruction also needs an explicit application boundary. Combining GF(256) decoding with filesystem replacement, manifest mutation, cluster transfer, repair scheduling, or consensus ownership would make a local algorithm appear to be self-healing and would collapse several independently testable failure boundaries into one component.

The decoder must remain object-size independent. It may operate on one bounded stripe and produce verified local output, but it must not retain prior stripes or materialize the complete object. CPU-intensive GF(256) work must not execute on a Reactor caller thread.

## Decision

### Schema 3 binds reconstruction facts

New EC writes use typed manifest schema version `3`. Every `EC_DATA_SHARD` or `EC_PARITY_SHARD` reference carries an explicit `EcShardLayout` containing:

- zero-based stripe and shard indexes;
- the selected data and parity counts (`k=4`, `m=2` for `EC_4_2`);
- a parity flag consistent with artifact kind and shard index;
- the exact logical byte length represented by the stripe;
- the original logical bytes represented by each data shard;
- the exact stored shard length;
- a lowercase SHA-256 of the committed stored bytes; and
- at least one ordered transport-neutral node or device identity.

The local validated composition binds the location identity `node-001`. Filesystem inspection resolves that identity separately to chunk-id files; filesystem paths are not stored as transport-neutral locations.

Schema versions 0 and 1 remain readable through their legacy whole-object/chunk compatibility paths. Schema-2 typed artifacts remain readable by their existing path, but schema-2 EC metadata without explicit layout is rejected for reconstruction. The decoder does not infer stripe or shard identity from artifact order. Unknown future schemas fail closed.

### One-stripe application port

`BoundedEcReconstructionPort` is the application-owned boundary. One request contains the manifest schema, one stripe index, all committed artifact descriptors for that stripe, exactly `k` selected checksum-valid survivors, and the one or two unavailable shard indexes.

The result contains only:

- verified logical bytes for that stripe;
- the exact regenerated shard indexes and bytes;
- committed SHA-256 values for regenerated shards; and
- bounded workspace measurements.

The port has no repository, filesystem, manifest, object-reference, replacement-location, cluster, transport, scheduler-daemon, or Ratis publication operation. Caller-provided survivor snapshots and defensive result snapshots are boundary ownership, not hidden decoder workspace.

### Stateless GF(256) infrastructure adapter

`BoundedEcReconstructionAdapter` implements the port using the same `GaloisField256Codec` shared with local EC encoding. It is stateless and receives already resolved survivor bytes. Before accepting output it validates:

- schema 3 and complete explicit layout;
- exactly `k+m` unique committed shard descriptors;
- consistent stripe, `k`, `m`, parity, logical-length, stored-length, checksum, and location facts;
- exactly `k` unique in-range survivors that are not marked unavailable;
- each survivor's committed length and SHA-256;
- one or two unavailable indexes for EC 4+2; and
- every regenerated shard's committed length and SHA-256.

Fewer than `k` valid survivors, duplicate or out-of-range indexes, contradictory metadata, wrong lengths or checksums, unsupported schemas, and ambiguous layouts fail closed with no accepted reconstruction output.

### Bounded memory and scheduling

One invocation owns at most one 4 MiB logical stripe, six 1 MiB shard buffers, and fixed EC 4+2 matrix/index workspace. It retains zero bytes from earlier stripes and zero whole-object bytes. A short final stripe returns its exact logical length without exposing padding.

The Spring composition injects a dedicated bounded scheduler with one worker and a queue of 16 tasks. The adapter subscribes GF(256) work on that scheduler rather than running it on the Reactor caller thread. These values are the validated local boundary, not a distributed repair throughput or production-sizing claim.

### Explicit non-publication boundary

Reconstruction is output-only. Successful output does not:

- replace or publish a shard;
- write a manifest or object reference;
- add or change a shard location;
- create a scanner, monitor, or self-healing daemon;
- create, claim, retry, or complete a Ratis repair job;
- transfer a shard between nodes;
- rebalance placement or clean an orphan; or
- inject a deterministic or probabilistic fault.

Those effects require separate requirement-first application and infrastructure slices with their own authority, fencing, publication, failure, and observability contracts.

### Ordered continuation after local reconstruction

The owner-selected EP-10 continuation is:

1. implement authoritative fixed EC 4+2 shard placement and direct transfer under `REQ-CLUSTER-015` (completed by ADR 0033);
2. introduce the bounded ADR 0030 plan/evidence kernel and only the committed-shard unavailable/corruption actions needed for deterministic EC self-healing tests;
3. implement EC monitoring and self-healing with process-local detection but consensus-owned repair jobs, claims, retries, fencing, and results;
4. implement shard rebalance as a separate placement-changing slice;
5. implement fenced orphan cleanup as a separate destructive slice; and
6. retain generalized ADR 0030 chaos as final work after the normal paths and their semantic/resource gates are stable.

The early ADR 0030 subset remains planned. It must be no-op by default, dual-guarded for non-production use, deterministic, finite, fail closed, payload/secret free in evidence, and limited to exact committed shards after manifest commit. It is not authorization for generic delay, cancellation, arbitrary path mutation, pre-publication action expansion, or generalized chaos.

Existing whole-object `N=3/W=2` replication remains a supported architecture for deployments below the arranged distributed-EC minimum, currently three nodes/failure domains for the intended EC 4+2 layout. It is not removed by the EC priority. Broader whole-object convergence may later support more complex inter-cluster replication, but inter-cluster and NAS-backed replication remain separate, later work whose meaningful validation depends on suitable NAS infrastructure.

## Consequences

- Local EC reconstruction now has unambiguous committed metadata and a transport-neutral application boundary.
- Every four-of-six survivor combination can reconstruct the two omitted EC 4+2 shards, including data/data, data/parity, and parity/parity losses.
- Short stripes and failure cases preserve exact logical size, bounded workspace, and fail-closed integrity behavior.
- Schema 3 is required for new reconstructable EC manifests; prior schemas remain readable without inventing layout facts.
- Later distributed placement and self-healing can reuse verified local decoding without granting the decoder publication or job authority.
- Manifest schema evolution and defensive boundary snapshots add explicit metadata and copy costs.
- No distributed EC, automated healing, repair publication, rebalance, cleanup, chaos, or production-readiness claim follows from this decision.

## Alternatives Considered

### Infer shard identity from schema-2 artifact order

Rejected. Artifact order is not a durable reconstruction contract and cannot safely express contradictory, missing, or distributed location facts.

### Let the decoder read and replace filesystem shards directly

Rejected. It would couple the application boundary to local paths, hide publication and fencing decisions inside an algorithm adapter, and make successful decoding appear to be repair completion.

### Put reconstruction in a self-healing daemon immediately

Rejected. Distributed authoritative placement/transfer and durable cluster repair ownership do not yet exist. A process-local daemon must not invent in-memory-only ownership or publication semantics.

### Reconstruct the complete object in one invocation

Rejected. Object-sized assembly would violate the one-stripe memory boundary and make decoder retention scale with object size.

### Execute GF(256) work on the caller thread or an unspecified shared pool

Rejected for the validated composition. CPU work uses an explicitly supplied bounded scheduler whose worker and queue limits are acceptance evidence.

## Evidence

Evidence was observed and rechecked locally on 2026-07-14 in an uncommitted working tree:

- `ReqPipeline017EcReconstructionCucumberTest` passed **5 scenarios / 46 steps**.
- The EC 4+2 matrix exercised **all 15 four-of-six survivor combinations** and reproduced both omitted shards byte-for-byte across data/data, data/parity, and parity/parity losses.
- The focused gate covered schema-3 facts, schema 0/1/2 compatibility boundaries, short final-stripe length, bounded decoder and boundary snapshots, dedicated scheduler execution, invalid metadata/survivor rejection, and no publication.
- `Phase3PipelineUnitSpecsCucumberTest` remained green, including the existing `REQ-PIPELINE-014` whole-object and `REQ-PIPELINE-015` physical EC-shard regressions.

The focused CI job is wired as a non-publishing evidence gate, but CI execution is not claimed. Ordinary root Maven success is supporting integration evidence only and is not substituted for the focused 5-scenario/46-step result.

## Related Requirements

- `REQ-PIPELINE-017` — implemented and validated for exactly the five local bounded reconstruction scenarios.
- `REQ-PIPELINE-014` — existing whole-object storage-unit behavior; unchanged.
- `REQ-PIPELINE-015` — existing local physical EC 4+2 shard persistence/readback; unchanged.
- `REQ-CLUSTER-015` — fixed A/B/C EC 4+2 shard placement and direct transfer; subsequently implemented and validated by ADR 0033.
- `REQ-CLUSTER-017` — broad healing remains partial.

## Related ADRs

- ADR 0025 — Conditional chunking and storage-artifact taxonomy.
- ADR 0027 — Authoritative cluster control plane and direct quorum data path.
- ADR 0028 — First three-node cluster implementation baseline.
- ADR 0029 — Consensus-owned durable repair for the current whole-object generation.
- ADR 0030 — Deterministic storage-pipeline fault injection, amended to permit a planned early bounded committed-shard test subset while retaining generalized chaos as final work.
- ADR 0031 — Bounded periodic current-reference anti-entropy for existing whole-object obligations.
- ADR 0033 — Fixed distributed EC 4+2 placement and transfer.
