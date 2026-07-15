# ADR 0033 — Fixed distributed EC 4+2 placement and transfer

Date: 2026-07-14

## Status

Accepted — implementation-informed for the bounded internal `REQ-CLUSTER-015` mechanism.

The focused `ReqCluster015DistributedEcCucumberTest` passes **5 scenarios / 40 steps**. This decision implements fixed A/B/C placement, bounded grpc-java/mTLS shard transfer, complete-shard acknowledgement, and Ratis-owned schema-3 EC reference publication and restart recovery. It does not implement clustered S3 reads, EC repair or replacement, monitoring/self-healing, rebalance, cleanup, dynamic membership, parameterized EC, or generalized chaos. EP-10 remains partial.

## Context

ADR 0032 established physical local EC shards, explicit schema-3 reconstruction facts, and output-only local reconstruction. It deliberately granted no distributed location or publication authority. The next prerequisite is an authoritative mapping from every committed EC shard to one arranged node, with payload transfer remaining outside the Ratis log.

The earlier policy model allowed wider `k+m<=32` values, but only EC 4+2 has exhaustive codec evidence. The raw parity matrix is not a validated MDS construction for every wider geometry. Accepting arbitrary values before a separate parameterized design would create a false durability promise.

The fixed EP-10 topology has three arranged failure domains, A, B, and C. EC 4+2 can tolerate the two shard losses caused by one complete node loss if every stripe places exactly two shards on each node.

## Decision

### Fixed geometry only

The supported erasure-coding configuration is now exactly:

- four 1 MiB data shards;
- two 1 MiB parity shards;
- six stored shards per stripe;
- a 4 MiB maximum logical stripe; and
- reconstruction from any four checksum-valid committed survivors.

`ErasureCodingConfig` rejects every geometry other than `4+2`. The local decoder also rejects otherwise internally consistent metadata with `UNSUPPORTED_GEOMETRY`. `EcShardLayout` remains capable of parsing bounded metadata so unsupported or future layouts can fail explicitly rather than being inferred.

Parameterized EC requires a later requirement and ADR covering a genuinely systematic MDS codec, placement for arbitrary failure-domain counts, acknowledgement policy, resource budgets, migration, and exhaustive survivor validation.

### Deterministic A/B/C placement

`FixedEc42Placement` sorts the three committed stable node identities and assigns each shard by `shardIndex mod 3`:

- A: shard indexes 0 and 3;
- B: shard indexes 1 and 4;
- C: shard indexes 2 and 5.

The committed membership must contain exactly three distinct failure domains. Every node owns exactly two shard obligations per stripe; removing any one arranged node leaves four authoritative shard identities.

This is fixed-topology placement, not dynamic membership, capacity-aware placement, rebalance, or general PA-6 execution.

### Transport-neutral coordinator and complete acknowledgement

`ClusterEcWriteCoordinator` consumes already encoded, durable local shard artifacts. Before transfer it incrementally verifies:

- each stored shard length and SHA-256;
- fixed schema-3 stripe/shard identity and logical lengths;
- the logical object length assembled from data-shard lengths; and
- the object SHA-256 streamed across logical data-shard bytes without whole-object materialization.

The bounded slice accepts exactly one stripe (six shard obligations) per publication. The coordinator retains the two shards assigned to its local node and transfers the other four through `ReplicaTransferPort`. Existing grpc-java infrastructure provides readiness-gated frames of at most 65,536 bytes, finite demand, deadlines, UUID-bound mutual TLS, temporary staging, checksum verification, fsync, and atomic publication.

Every stripe requires all six exact durable acknowledgements before publication. Each acknowledgement binds operation ID, artifact ID, target node, stored length, SHA-256, topology epoch, and policy epoch. There is no reduced-shard success and no whole-object fallback.

The encoder currently prepares all six shards on the coordinator. Copies not named at the coordinator by the authoritative layout are non-authoritative source artifacts. Their reclamation belongs to the later fenced cleanup requirement; this slice performs no destructive deletion.

### Consensus-owned EC reference

`ClusterControlPlanePort.compareAndPublishEc` and the Ratis command codec publish one `ObjectReferenceGeneration` with layout `EC_4_2` only after application validation. The Ratis state machine independently validates:

- current prior generation;
- topology and policy epochs;
- complete contiguous fixed EC 4+2 stripe facts;
- exactly two locations on each of three committed voters; and
- the deterministic shard-index-to-node mapping.

The reference records object length/SHA-256 and all schema-3 shard facts, including transport-neutral node locations. Object and shard payload bytes remain outside Ratis commands, logs, and snapshots. Existing snapshot version 2 stores the extended reference encoding and remains backward compatible with whole-object references.

After a complete voter restart, the same generation and shard locations are recovered exactly.

### Explicit read and healing boundary

Whole-object anti-entropy and repair skip EC references. Request-facing whole-object repair fails explicitly if asked to process an EC reference. This prevents the existing replica repair path from treating the EC reference's virtual manifest identity as a whole-object payload.

Clustered S3 reads, survivor collection, reconstruction, replacement publication, scanner/daemon behavior, consensus-owned shard repair jobs, and degraded-state reporting require the next self-healing/read integration slices.

## Consequences

- Distributed EC 4+2 shard placement and transfer are now real bounded mechanisms on fixed A/B/C.
- One node loss leaves four authoritative shard identities, but this slice does not yet execute a clustered S3 read after that loss.
- Publication is stricter than the reconstruction minimum: all six shards must be durable before a new generation is authoritative.
- Existing whole-object N=3/W=2 behavior remains supported and separate.
- One authoritative publication is capped at one stripe and six shard references; payload bytes never enter Ratis.
- Source-side non-authoritative shard copies require later fenced cleanup.
- Generalized or configurable erasure coding remains rejected until separately designed and validated.

## Alternatives Considered

### Keep accepting arbitrary k and m

Rejected. Only EC 4+2 has exhaustive encoding/reconstruction evidence, and wider raw matrix combinations are not proven MDS.

### Publish after any four shard acknowledgements

Rejected for this slice. It would make a newly written object authoritative in a degraded state and weaken the declared arranged placement without a separate policy and recovery contract.

### Put shard bytes in Ratis

Rejected. Ratis owns ordering and authoritative metadata; bounded direct transfer owns payload volume.

### Reuse whole-object N=3/W=2 references

Rejected. Whole-object replica evidence does not identify stripe/shard geometry and cannot establish EC durability.

### Add S3 degraded reads and self-healing in the same slice

Rejected. Read selection, survivor transfer, reconstruction, replacement fencing, repair jobs, and failure observability are independent semantic boundaries.

## Evidence

Local evidence in the implementation working tree:

- `ReqCluster015DistributedEcCucumberTest`: **5 scenarios / 40 steps**;
- real grpc-java/mTLS transfer of four 1 MiB shards from A to B/C;
- exact filesystem placement A=`0,3`, B=`1,4`, C=`2,5`;
- six-acknowledgement Ratis publication and complete-voter restart recovery;
- fail-closed missing acknowledgement, checksum mismatch, and stale topology cases;
- `ReqPipeline017EcReconstructionCucumberTest`: **5 scenarios / 46 steps**, including unsupported-geometry rejection and all 15 EC 4+2 survivor combinations; and
- existing Ratis repair, `REQ-CLUSTER-024`, `REQ-CLUSTER-027`, and data-plane regression gates remain green.

CI is wired to retain the focused JSON, Surefire, and log evidence, but remote CI execution is not claimed.

## Related Requirements and ADRs

- `REQ-CLUSTER-015` — implemented and validated for the bounded fixed A/B/C placement/transfer/publication mechanism.
- `REQ-PIPELINE-015` — local physical EC 4+2 shards.
- `REQ-PIPELINE-017` — local schema-3 reconstruction and fixed-geometry rejection.
- ADR 0027 — authoritative control plane and direct data path.
- ADR 0030 — deterministic fault injection; bounded committed-shard actions remain next.
- ADR 0032 — bounded local EC reconstruction.
