# ADR 0027 — Authoritative cluster control plane and direct quorum data path

Date: 2026-07-13

## Status

Accepted — architectural authority for the cluster control/data-plane split, approved by the product owner for EP-8 EARLY and implementation-informed by the bounded EP-10 slices.

The repository now implements the fixed A/B/C Ratis and direct whole-object baseline from ADR 0028, the bounded current-generation repair refinement in ADR 0029, and bounded periodic current-reference discovery under ADR 0031. This does not complete ADR 0027's wider target: dynamic membership, broader namespace/topology lifecycle, clustered multipart/conditional/versioned/chunked or erasure-coded data paths, anti-entropy beyond fixed A/B/C current-reference obligations, rebalance, orphan cleanup, rolling upgrades, broader partitions, and production deployment proof remain absent or unvalidated. EP-8 supply-chain status is tracked separately. No production-readiness or general distributed-support claim follows.

## Context

Magrathea is intended to become a real multi-node S3 cluster. PA-6 already supplies unit-validated pure-domain models for deterministic placement, quorum decisions, anti-entropy/healing plans, rebalance plans, and honest readiness reporting. Those models do not provide network transport, authoritative membership, durable distributed metadata, replica execution, or multi-node validation. EP-10 must execute the modeled policy without turning route presence or architecture acceptance into a distributed-readiness claim.

The cluster must preserve the existing boundaries:

- S3-compatible endpoints remain the only external object and bucket API.
- The Admin API may expose operational status and topology/configuration concerns that have no S3 equivalent, but it must not become an object API.
- Inter-node protocols are internal infrastructure behind the object-store/storage-engine boundary and are not a third public facade.
- PA-6 planners and reporters remain a pure policy core. Transport, consensus, persistence, retries, and job execution adapt their inputs and outputs rather than entering that core.

Distributed object storage has two different consistency and traffic needs. Small but authoritative metadata requires one ordered history and fencing. Immutable whole-object, chunk, replica, and erasure-coding shard bytes require bounded streaming at data volume and must not be copied through a consensus log. A simple Dynamo-style eventually consistent metadata model would leave generation selection, membership, topology changes, and repair ownership ambiguous under partitions.

The current YAML catalogs describe storage policies, devices, disk sets, and a limited `RACK`/`HOST`/`DISK` failure-domain level. They do not yet describe the concrete parent-linked topology identities needed for cluster placement. EP-10 therefore needs an additive topology model rather than replacement of the PA-6 policy model.

EP-8 also defines the supply-chain evidence strategy. The repository already contains an opt-in OWASP Dependency-Check configuration that monitors dependencies, fails on scan errors, and fails at CVSS 7.0 or above, plus existing single-node non-root container evidence. The user decision for this EARLY slice is to preserve and report that monitoring/fail-closed evidence without performing dependency remediation.

## Decision

### Internal transport and node identity

Use internal-only protobuf gRPC over HTTP/2 for cluster control RPCs and direct node-to-node data streams.

- Protobuf contracts are versioned for backward-compatible rolling evolution.
- Reactor-facing adapters provide the backpressure bridge. Blocking gRPC stubs and blocking work are prohibited on Reactor event-loop threads.
- Streaming adapters propagate demand through bounded queues; every queue, in-flight message window, retry budget, and message size has a configured upper bound. Unbounded buffering is prohibited.
- Every RPC and stream has a deadline. Downstream cancellation propagates across the bridge, stops production and I/O promptly, and releases buffers. Deadline expiry and cancellation are explicit non-success outcomes and never imply durable acknowledgement.
- Node-to-node transport requires mutual TLS. Both peers authenticate; plaintext and server-authentication-only cluster modes are prohibited.
- Each node owns a stable persisted UUID created once for its storage identity. Hostname, address, process ID, certificate serial number, and seed-list position are not node identity. Certificate rotation and address changes retain the UUID; cloning one UUID into two live nodes is rejected or fenced.

The gRPC surface is limited to cluster concerns such as membership/control coordination, replica or EC-shard transfer, verification, health evidence, and execution of durable recovery jobs. It cannot expose public object/bucket semantics, bypass S3 authorization, or be used by external clients as an alternate object API.

### Authoritative membership and consensus control log

Static seed addresses are bootstrap hints only. They help an unjoined or restarting node contact the cluster, but they are neither the membership database nor an availability oracle.

Membership is authoritative only when committed through consensus. Liveness suspicion may make a node temporarily ineligible for new placement, but suspicion, missed heartbeats, RPC timeout, or seed removal never auto-evicts a member. Admission, promotion, demotion, replacement, and removal are explicit consensus-controlled transitions using safe configuration changes and stable node UUIDs.

Start with three metadata voters, each co-located with one of the initial storage-node deployments while remaining a logically separate control-plane role. The topology should place those voters across the best available independent failure domains. A three-voter deployment tolerates one unavailable voter; it does not tolerate loss of two voters and must not be described otherwise.

Use embedded Apache Ratis as the Raft implementation. The bounded fixed A/B/C integration now validates persisted control state, consensus bucket/reference publication, repair-job lifecycle/snapshot migration, mTLS, restart, and selected leader-change behavior. Before any production claim, the remaining spike obligations still require membership changes, rolling/mixed-version upgrade and leader-transfer coverage, bounded resource evidence, and a broader partition/fault matrix. Failure of those wider gates can reopen the implementation-library choice without changing the decision to use an authoritative consensus control plane. Current evidence is bounded and does not prove production Ratis or Raft behavior.

The consensus-controlled log owns the authoritative ordering and generations for:

- cluster membership and node incarnation/fencing state;
- bucket and bucket-configuration namespace state;
- object-reference and manifest generations, including which immutable artifacts form the committed object version;
- topology and storage-policy epochs used for placement decisions;
- fencing records and tombstones that prevent stale writers or deleted generations from reappearing;
- durable healing, anti-entropy, rebalance, garbage-collection, and other cluster job state.

Large object bytes, replicas, chunks, EC stripes, and EC data/parity shards are not stored in the control log. Consensus entries contain immutable references, checksums, lengths, placement/encoding facts, and generation/epoch data required to validate those bytes.

### Topology and PA-6 policy boundary

Extend the YAML-backed topology catalog to describe concrete, parent-linked identities in this hierarchy:

`zone → rack → host → disk-set → device`

The model records the node UUID and storage roots/devices attached to the appropriate host and disk-set. Configuration validation rejects duplicate identities, broken parent links, impossible policy constraints, and conflicting device ownership. Topology and policy snapshots receive consensus-controlled epochs so a writer is fenced from publishing a placement calculated from stale configuration.

PA-6 `DistributedPlacementPlanner`, `QuorumPolicy`, `AntiEntropyPlanner`, `RebalancePlanner`, and `DistributedReadinessReporter` remain deterministic, side-effect-free policy components. Cluster application services construct authoritative snapshots for them and execute their decisions. Network clients, Ratis types, protobuf messages, clocks, persistence APIs, and retry loops do not enter the pure policy core.

The initial replicated-data policy is `N=3, W=2`. Degraded writes are disabled by default: if the committed topology/policy cannot select the required independent targets, or fewer than `W` selected replicas durably acknowledge checksum-valid bytes, the write fails and no object-reference generation is published. Erasure-coded policies use the same publication rule with the policy-defined selected shard set and required durable acknowledgement threshold; their exact threshold must be explicit in the policy rather than inferred from `N=3, W=2`.

### Ordered write semantics

A successful object-generation publication follows this order:

1. Resolve a consensus-committed membership, topology epoch, policy epoch, and current namespace/object generation. Reject or redirect stale/fenced coordinators.
2. Invoke the pure placement policy against that snapshot and select the exact replica or EC-shard targets.
3. Stream immutable data directly from the coordinating node to the selected storage nodes over backpressure-aware gRPC. The payload does not transit the Raft leader or control log unless that node is independently a selected data target.
4. Each target writes to an unpublished generation, validates length and checksum, durably publishes the local immutable artifact, and returns an idempotent acknowledgement bound to node UUID, artifact ID, checksum, and epochs.
5. Require the policy threshold—initially `W=2` for `N=3` replication—of valid durable acknowledgements. Missing, cancelled, timed-out, stale-epoch, or checksum-failing acknowledgements do not count.
6. Revalidate fencing and append the object-reference/manifest generation to the consensus log. That entry names only verified immutable artifacts and carries the relevant topology/policy epochs.
7. Acknowledge the S3 write only after the reference publication is consensus committed. Failed publication leaves unpublished immutable artifacts as safe orphans for a durable, fenced cleanup job; it must not expose them as an object generation.

Retries use stable operation and artifact identifiers. A coordinator or leader change may resume or repeat idempotent work, but it may not publish two conflicting generations for one conditional operation. Concurrent and conditional S3 writes are serialized by the committed namespace/object generation and fencing rules.

### Ordered read semantics

A read first resolves the consensus-committed object-reference/manifest generation. It never chooses a generation by comparing divergent replicas and does not serve an uncommitted or locally newer manifest.

The reader then fetches immutable bytes from the referenced replicas or reconstructs them from the referenced EC shards. It validates checksum and length while streaming through bounded buffers. The configurable replica read quorum defaults to one checksum-valid replica. ADR 0029 refines fallback for the implemented single-pass whole-object path: known local absence before response streaming may be repaired from a different named verified replica, but corruption discovered while the response stream is consumed fails that request and never transparently retries after bytes may have been emitted. Durable repair may benefit only a later GET. Future EC reconstruction requires separate evidence. If the configured valid-data quorum or reconstruction threshold cannot be met, the read fails with an integrity/availability outcome rather than returning unverified bytes.

This is strongly ordered, consensus-selected metadata with checksum-validated data reads. It is not Dynamo-style eventual metadata, read-repair generation selection, or last-write-wins reconciliation. ADR 0029 is the accepted bounded authority for current-generation whole-object repair and single-pass corruption semantics.

### Failure, partition, and cancellation semantics

- Loss of control-plane quorum prevents membership changes and new reference publication. The cluster fails writes rather than accepting split-brain or degraded writes.
- A partitioned node cannot use an old topology/policy epoch or lease/incarnation to publish references. Fencing and tombstones survive restart.
- Health suspicion can exclude a target from a new placement proposal, trigger investigation, or schedule repair, but cannot alter authoritative membership by itself.
- Deadline expiry, cancellation, stream failure, checksum mismatch, or insufficient durable acknowledgements aborts publication. Any already durable unpublished artifacts remain unreachable and are reclaimed only by fenced durable job execution.
- Read fallback and repair operate only on the consensus-committed generation. Repair cannot resurrect a tombstoned generation.
- Healing, anti-entropy, rebalance, and cleanup jobs are durable control-log state with idempotent attempts and epoch fencing; an in-memory scheduler is not authoritative job ownership.

### Supply-chain evidence strategy

EP-8 will produce and validate the following artifacts and gates:

- CycloneDX SBOMs in both JSON and XML for the released application and its resolved components;
- an SPDX-normalized dependency license inventory, preserving package identity, version, concluded/detected license, source, and unresolved/manual-review findings;
- retained OWASP Dependency-Check monitoring with machine-readable evidence and fail-closed behavior for scan errors and the configured vulnerability threshold;
- runtime container-hardening validation, including non-root execution, dropped unnecessary capabilities, no privilege escalation, a read-only root filesystem except explicit writable data/runtime mounts, and verification that required S3/Admin startup and persistence behavior still works under those restrictions.

For this user-approved EARLY decision, dependency remediation is explicitly outside scope. Existing OWASP findings and scan-operability evidence are to be reported truthfully, without suppressing, upgrading, or changing dependencies merely to claim EP-8 completion. This does not waive vulnerabilities or make a failing gate acceptable for a later release; it separates evidence collection from remediation work.

At acceptance time, CycloneDX generation, SPDX normalization, CI wiring, and expanded runtime-hardening validation were planned/absent. Subsequent EP-8 evidence is tracked in `docs/test-report.md`: the historical clean packet remains revision-bound, current complete-reactor `REQ-SUPPLY-001` is implemented-not-e2e-validated, OWASP status is unknown/error, and no publication or compliance claim follows. This supply-chain status does not alter the cluster authority decided here.

## Consequences

- Cluster metadata has one consensus-committed generation history; data volume remains off the consensus path.
- An S3 write is unavailable when the data acknowledgement threshold or control-plane quorum is unavailable. This sacrifices degraded-write availability to avoid acknowledged-but-unreferenced or split-brain writes.
- The default data read quorum of one favors read availability and latency after authoritative generation resolution; checksum verification, alternate-replica retry, and durable repair mitigate corruption. Operators may configure a higher valid-replica quorum at additional latency and bandwidth cost.
- Three initial metadata voters tolerate one voter failure but add quorum latency and operational requirements for safe membership change, backup/snapshot handling, and upgrade sequencing.
- Co-locating voter and storage roles reduces initial deployment count but couples resource and host failures. Failure-domain placement, bounded resources, and later dedicated-voter deployments remain necessary operational options.
- Static seeds remain simple and predictable but require operator-managed bootstrap addresses. They cannot silently redefine membership.
- Mandatory mTLS and persisted UUIDs create certificate, trust, enrollment, rotation, duplicate-identity detection, and disaster-recovery responsibilities.
- Topology catalogs become richer and require migration/validation of existing YAML. PA-6 policy semantics remain reusable instead of being replaced by transport or consensus code.
- Ratis reduces the need to implement Raft directly but introduces library fit, lifecycle, storage, upgrade, and reactive-integration risks that must pass the spike and fault-injection gates.
- Direct data transfer avoids routing large payloads through the leader, while requiring idempotent staging, orphan cleanup, checksummed acknowledgements, and careful coordinator failover.
- The bounded ADR 0028/0029/0031 implementation evidence upgrades only their exact fixed-cluster requirements. EP-10 remains partial, current complete-reactor supply-chain evidence remains separately qualified, and production readiness remains unclaimed pending the wider membership, healing/topology, rebalance, cleanup, fault, upgrade, operation, and artifact-validation evidence.

## Alternatives Considered

### RSocket for all inter-node traffic

RSocket offers native reactive streams and backpressure semantics and is attractive for bidirectional streaming. It was not selected because protobuf gRPC has a broader Java operational ecosystem, explicit service contracts, mature HTTP/2 tooling, and familiar deadline/mTLS behavior. The decision accepts the cost of a carefully tested Reactor-to-gRPC backpressure and cancellation bridge rather than relying on transport-native Reactive Streams semantics.

### Plain HTTP/2 with custom streaming contracts

Plain HTTP/2 could reuse Reactor Netty and avoid a gRPC runtime. It was not selected because Magrathea would have to define framing, schema evolution, status mapping, deadlines, streaming conventions, interoperability tooling, and generated clients itself. That control does not outweigh protobuf/gRPC standardization for the internal cluster protocol.

### Gossip as authoritative membership and metadata convergence

Gossip is useful for disseminating observations and failure suspicion, but it was rejected as the authority for membership or object-reference generations. Suspicion is not proof of removal, and eventually convergent metadata permits conflicting generations and unsafe placement during partitions. Gossip may later supplement observability or hint dissemination only; it cannot commit membership, epochs, fencing, or namespace state.

### Static membership from configuration

Treating the seed/YAML list as membership is operationally simple but unsafe for replacement, address changes, fencing, and partition recovery. Static configuration remains bootstrap input only.

### PA-6 quorum decisions without a consensus metadata plane

Applying read/write quorums independently to metadata replicas would resemble Dynamo-style eventual metadata and require conflict reconciliation. It was rejected because bucket namespace, manifest generation, topology epoch, job ownership, and tombstones require a single ordered authority. PA-6 quorum policy remains applicable to immutable data acknowledgements and checksum-valid reads after consensus resolves the generation.

### External consensus service

An external etcd, Consul, ZooKeeper, or separately operated Raft service could provide mature consensus and separate control-plane failure resources. It was not selected for the initial architecture because it adds a mandatory independently deployed system, version matrix, credentials/trust boundary, backup procedure, and operational dependency. Embedded Ratis keeps the initial product self-contained. If the Ratis spike fails or operational evidence shows unacceptable coupling, an external consensus adapter may be reconsidered while preserving the control-log ownership and data-path semantics in this ADR.

### Store object data in Raft or proxy all data through the leader

Rejected because large immutable streams would inflate the log, snapshots, leader bandwidth, recovery time, and quorum latency. Consensus publishes references only after direct target acknowledgements.

### Allow degraded writes below the configured placement or write quorum

Rejected by default because availability would be purchased with an acknowledged durability reduction and ambiguous repair obligations. A future explicitly named policy could revisit this only through a separate decision and user-visible durability semantics; it is not an implicit fallback.

## Evidence

Architecture inputs observed before this decision include:

- At decision time, `PLAN.md` classified PA-6 as modeled and unit-validated but not distributed-production-ready and EP-8/EP-10 implementation as absent; that is historical context superseded for bounded slices by ADRs 0028/0029/0031 and current evidence documents.
- `storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/distributed/` contains pure placement, quorum, anti-entropy, rebalance, and readiness models. `DistributedReadinessReporter` explicitly retains missing networked-membership, real-replication-execution, and multi-node-e2e capabilities.
- `storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/valueobject/FailureDomain.java` currently models only rack, host, and disk levels; the YAML device/disk-set catalogs do not yet express the complete concrete parent-linked cluster topology.
- `pom.xml` and `scripts/run-dependency-check.sh` contain the existing opt-in OWASP Dependency-Check monitoring/fail-closed configuration and machine-readable analysis path.
- `Dockerfile` runs the JVM image as the `magrathea` user, and `scripts/validate-jvm-container-replacement.sh` checks non-root execution for the existing single-node replacement scenario.
- Domain research observation: `/home/paperboy/.llm-wiki/wiki/sources/obs-2026-07-12-ep-8-authoritative-cluster-and-supply-chain-research-complet.md`.

These were the original decision inputs. Subsequent ADR 0028 evidence implements the fixed A/B/C Ratis/direct whole-object baseline, ADR 0029 evidence implements bounded current-generation repair, and ADR 0031 evidence implements bounded periodic current-reference discovery for fixed A/B/C. They still do not prove expanded topology, dynamic membership, wider data semantics, anti-entropy beyond current named obligations, rebalance, cleanup, production cluster operation, or a current complete-reactor SBOM/license/hardening packet.

## Related Requirements

- PA-6 `REQ-DIST-001..006`: modeled distributed placement, quorum, healing, rebalance, and readiness policy.
- Planned EP-8 `REQ-HA-*` and `REQ-SUPPLY-*`: architecture and supply-chain evidence.
- EP-10 `REQ-CLUSTER-*`: bounded `001..005`, `008..015`, and `019..027`, including `024` and `027`, are implemented and validated in their explicit modes; `014` is architecture-only and subsequent ADR 0033 bounds `015` to fixed internal EC 4+2 placement/transfer. Broad `017` remains partial; `006/007/016/018` remain not implemented.
- ADR 0014: Storage Engine bounded context.
- ADR 0019: MIT license and dependency-license compatibility obligation.
- ADR 0025: conditional chunking and immutable storage-artifact taxonomy.
- ADR 0031: bounded periodic current-reference anti-entropy; accepted without broadening this ADR's wider target or production-readiness status.
