# ADR 0028 — First three-node cluster implementation baseline

Date: 2026-07-13

Last evidence review: 2026-07-13

## Status

Accepted — implementation-informed baseline for the bounded first fixed three-node EP-10 slice.

Acceptance is limited to the evidenced A/B/C topology: one Ratis voter and one replica data server per JVM, stable UUID-backed roots, mutually authenticated internal transports, `N=3, W=2` direct whole-object replication without degraded writes, consensus publication of bucket and object-reference generations, one-coordinator failover, and complete A/B/C restart. It does not accept EP-10 as complete, claim production readiness, or establish broader distributed-storage support.

## Context

ADR 0027 accepts an authoritative consensus control plane, direct quorum data transfer, consensus-selected object-reference generations, `N=3, W=2` replication without degraded writes, and internal mTLS-protected protobuf gRPC. The implemented first EP-10 slice applies those decisions through a concrete, deliberately narrow fixed topology without implying support for the full planned cluster lifecycle.

The existing PA-6 models in `storage-engine-domain` are deterministic policy and domain logic. They are not network, consensus, or persistence implementations. The existing object-store storage-engine adapter is the integration boundary through which S3 behavior reaches the storage engine. Adding cluster infrastructure must not create another public object API, move S3 concerns into an internal protocol, or contaminate the pure domain with Ratis, gRPC, protobuf, Spring, filesystem, or lifecycle types.

The first slice maintains an explicit distinction between bootstrap simplification and the target architecture. A fixed three-node deployment is useful for proving consensus-selected metadata and direct replica availability after one coordinator failure. It is not the future membership model accepted by ADR 0027, in which admission, promotion, demotion, replacement, and removal are consensus-controlled operations using stable node identities.

Local tests use reproducible certificates, but test-local certificate generation and trust are not a relaxation of the production contract. Production inter-node communication requires mutually authenticated TLS and an operational identity, enrollment, trust, rotation, revocation, and recovery model.

## Decision

### Minimal behavioral slice

Use exactly three fixed co-located voter/storage nodes: A, B, and C. Each JVM owns one Ratis voter and one replica data server. Each node recovers its stable UUID from its own identity root and has independent consensus, object, temporary, and runtime storage roots.

The accepted externally observable scope is limited to:

- unconditional `CreateBucket`;
- unconditional, single-part, whole-object `PUT`;
- whole-object `GET`;
- consensus-ordered bucket generations;
- consensus-ordered object-reference generations that identify immutable whole-object artifacts and their verified replica locations;
- direct replica transfer with `N=3` placement and publication after `W=2` checksum-valid durable acknowledgements; and
- no degraded writes.

A write is successful only after at least two selected storage nodes have durably acknowledged the exact immutable artifact and the corresponding object-reference generation has been committed by the control quorum. Loss of the data acknowledgement threshold or control quorum fails the write; it does not publish a reduced-durability reference.

Dynamic membership, erasure coding, chunked-object transfer, multipart upload, conditional writes, S3 versioning, healing and rebalance execution, automated orphan cleanup, rolling upgrades, the broader partition suite, and broader S3 compatibility are outside this first slice. Their absence remains explicit in requirements and status reporting.

### Validated acceptance proof

The validated acceptance proof uses only the existing S3 integration boundary for bucket and object operations:

1. Start fixed nodes A, B, and C with separate roots.
2. Through node A's S3 endpoint, create a bucket and upload a non-trivial whole-object fixture.
3. Establish that the write was published through a committed object-reference generation after `W=2` durable acknowledgements.
4. Stop coordinator node A.
5. While B and C retain the two-voter control quorum, read the same bucket and key through node B's S3 endpoint.
6. Compare the returned bytes exactly with the uploaded fixture. Length, checksum, and S3 metadata such as the ETag may provide additional semantic evidence but cannot replace exact-byte comparison.

Inspection of node roots, Ratis state, or internal messages is supplementary mechanism evidence only. Calling the internal replica service directly is not an acceptable S3 behavior proof. The focused WebTestClient and AWS CLI executions validate this flow, the two no-publication failure paths, and complete restart for the narrow slice. They cover one-node coordinator loss while B and C remain mutually reachable; they do not establish dynamic membership, general partition tolerance, healing, rebalance, upgrades, or production readiness.

### Dependency and protocol version baseline

Use the following implemented version baseline:

- Apache Ratis **3.2.2** with `ratis-grpc` for the embedded consensus control plane;
- grpc-java **1.82.2** with `grpc-netty-shaded` for the separate direct replica data plane;
- protobuf runtime and `protoc` **3.25.8** for application-owned protocol contracts; and
- Testcontainers **2.0.2** as the pinned isolation-tool baseline; the accepted S3 proof itself starts three real child JVM processes rather than using Testcontainers.

Ratis control traffic and application-owned replica traffic use separate servers, ports, executors, limits, metrics, and lifecycle ownership. Application protocol code must not rely on or expose Ratis-relocated implementation classes. Focused gates exercise the selected Ratis, grpc-java, protobuf, lifecycle, mTLS, persistence, flow-control, and failure mechanisms only within the bounded first slice. They are not general compatibility, vulnerability, upgrade, operational, or production-readiness evidence; the Testcontainers pin is not exercised by the real-process S3 gate.

Do not introduce reactive-grpc initially. Bridge Reactor-facing application ports to grpc-java manual inbound and outbound flow-control APIs. Use **64 KiB payload frames** and explicit finite limits for message size, queue capacity, in-flight frames, deadlines, idle timeouts, and retries. Disable automatic inbound flow control where required and request more input only after bounded downstream acceptance. Outbound production must honor gRPC readiness. Cancellation and deadline expiry propagate in both directions, close resources, and never count as durable acknowledgement. Unbounded buffering is prohibited.

### Implemented module decomposition

Keep the implemented cluster responsibilities in these modules:

- `storage-engine-cluster-application`: cluster use cases and transport-neutral ports for authoritative metadata, local artifacts, replica transfer, write coordination, and read coordination. It may expose Reactor-facing application APIs but has no Ratis, protobuf, generated-stub, or gRPC dependencies.
- `cluster-protocol`: versioned protobuf contracts and generated messages/stubs for internal cluster communication. It contains no S3, Spring application, storage policy, or domain decision logic.
- `cluster-control-ratis-infrastructure`: Ratis lifecycle, the deterministic control state machine, fixed-bootstrap wiring, persistence and snapshots, control-plane mTLS, and mapping at the infrastructure boundary.
- `cluster-data-grpc-infrastructure`: the independent grpc-java replica server and clients, peer authentication, bounded manual flow-control bridge, and durable receive/send adapters.

`storage-engine-domain` remains the pure PA-6 domain. Ratis, grpc-java, protobuf, Testcontainers, Spring, network, filesystem, certificate, retry, and runtime lifecycle types must not enter it. Any added domain type must express transport-free domain meaning rather than mirror an infrastructure schema.

The existing object-store storage-engine adapter remains the only S3-to-storage-engine integration boundary. For the fixed cluster profile it delegates through cluster application ports instead of directly treating a node-local manifest as authoritative. No cluster module exposes a public bucket/object facade, and no S3 route binds directly to Ratis or replica-transfer infrastructure.

### Fixed bootstrap versus future membership

For this first slice, configure an explicit and identical static manifest for nodes A, B, and C, including their stable node UUIDs and control/data addresses. This manifest bootstraps one known three-voter group and makes the initial test topology deterministic. Restarted non-empty nodes recover persisted consensus state; configuration or seed order must not silently rewrite that state.

This static three-voter manifest is a temporary first-slice constraint, not the authoritative long-term membership design. It does not support adding, promoting, demoting, replacing, or removing nodes, and it must not be advertised as dynamic cluster administration. A later slice will implement ADR 0027's consensus-controlled membership transitions, including safe catch-up and promotion, explicit removal and fencing, and stable persisted identities. Health suspicion, timeout, address changes, or edits to bootstrap configuration never constitute membership changes.

### Production mTLS and test certificate infrastructure

Mutual TLS remains mandatory for all production control-plane and replica-data connections. Both peers must authenticate against configured trust, and the authenticated identity must be bound to the expected stable cluster node identity. Plaintext and server-authentication-only production modes are prohibited.

Tests create a test-local certificate authority and per-node certificates and use test-only trust stores to make the three-process proof reproducible. Those certificates and keys are ephemeral test fixtures. They do not define production enrollment, key custody, trust distribution, rotation, revocation, expiry monitoring, certificate-profile policy, or disaster recovery. A passing test with a local CA demonstrates that the mTLS path was exercised in that environment only; it is not production PKI evidence.

## Consequences

- The bounded implementation has a small, reviewable failure proof: after A coordinates a successful write and stops, B can resolve the consensus-committed reference with B and C's quorum and return exact bytes from an acknowledged replica.
- Fixed bootstrap avoids introducing membership-change complexity before metadata and replica publication are proven, but the resulting cluster cannot elastically add, replace, or remove members.
- Co-locating voter and storage roles minimizes initial deployment count while coupling their resource and host failures. It remains a first-slice topology, not a universal production recommendation.
- Consensus contains bucket and object-reference generations, not object payload bytes. Replica traffic therefore avoids the control log but requires durable staging, checksum verification, idempotent acknowledgements, and orphan handling in later work.
- `N=3, W=2` without degraded writes deliberately reduces write availability when either the data threshold or control quorum is unavailable.
- Separate Ratis and grpc-java transports isolate their dependency and lifecycle concerns at the cost of two internal ports and two security/resource configurations per node.
- A hand-written bounded flow-control bridge avoids an initially mismatched reactive-grpc dependency, but it creates implementation and testing responsibility for demand, readiness, cancellation, deadlines, buffer ownership, and slow-consumer behavior.
- Version pinning makes the bounded build and focused gates reproducible. It also creates an explicit upgrade obligation after compatibility, vulnerability, and operational evidence is gathered.
- Keeping S3 integration in the existing adapter and PA-6 in the pure domain prevents the initial cluster mechanism from becoming a second object API or an infrastructure-shaped domain model.
- Acceptance upgrades only the bounded fixed A/B/C baseline. EP-10 remains partial because the later transfer, compatibility, membership, repair, and partition capabilities listed below remain partial, not implemented, or absent.

## Alternatives Considered

### Implement dynamic membership in the first slice

This would exercise the complete target lifecycle earlier, but it combines admission, catch-up, promotion, replacement, fencing, and removal risks with the first control-state and replica-transfer implementation. Fixed three-voter bootstrap is selected to isolate the earliest authoritative metadata and failover proof. Dynamic membership remains required by ADR 0027 and is deferred, not rejected.

### Use one gRPC runtime and port for Ratis and replica transfer

A shared server appears operationally simpler, but it couples application protocol ownership, resource limits, TLS setup, lifecycle, and dependency behavior to Ratis internals. Separate Ratis control and grpc-java data services preserve boundaries and allow independent flow-control and failure testing.

### Introduce reactive-grpc generated stubs immediately

Generated reactive stubs could reduce adapter code, but the researched reactive-grpc baseline is not aligned with the selected grpc-java, protobuf, and Reactor versions. A bounded manual bridge has a smaller initial dependency surface. Reactive stub generation may be reconsidered after a dedicated compatibility and cancellation/backpressure spike.

### Send whole-object bytes through the Ratis log

This would couple reference ordering and byte replication, but it would put data-volume streams into the consensus log and amplify log, snapshot, leader, and recovery costs. The control state stores generations and immutable references only; selected storage nodes receive bytes directly.

### Expose an internal cluster object endpoint for acceptance testing

Directly testing the replica service would be easier but would bypass the product's S3 boundary and risk creating a parallel object API. Acceptance uses the existing S3 endpoint through the object-store storage-engine adapter; internal inspection remains supplementary.

### Allow `W=1` when a replica node is unavailable

This improves write availability but silently weakens the declared durability policy and conflicts with ADR 0027. The first slice fails writes rather than degrading below `W=2`.

## Evidence

Evidence was checked on 2026-07-13 against the current requirement text, production modules, focused reports, and ARC42 chapter 9.

### Focused EP-10 semantic gates

- Shared real-process S3 evidence for `REQ-CLUSTER-001` through `REQ-CLUSTER-005` passes in both WebTestClient and AWS CLI modes: **10 scenarios and 108 steps, all passed**. The total is the two `REQ-CLUSTER-001` scenarios and 16 steps recorded in `target/ep10-evidence/req-cluster-001-real-process.log`, plus the eight `REQ-CLUSTER-002..005` scenarios and 92 steps recorded by `target/ep10-story-focused-final.log` and `bootstrap-application/target/cucumber-json/ep10-create-bucket-real-process.json`. These gates start three real JVM processes and cover consensus bucket visibility, `N=3/W=2` whole-object publication, exact-byte failover read, refusal to publish below data or control quorum, stable UUID roots, and complete restart.
- Real Ratis control and mTLS mechanism evidence for `REQ-CLUSTER-008` and the control-plane portion of `REQ-CLUSTER-013` passes in `cluster-control-ratis-infrastructure/target/surefire-reports/TEST-com.example.magrathea.cluster.control.ratis.ep10.PhaseEp10ControlPlaneCucumberTest.xml` and `TEST-com.example.magrathea.cluster.control.ratis.ep10.PhaseEp10ControlPlaneTlsCucumberTest.xml`.
- Cross-module write-ordering and failure-publication evidence for `REQ-CLUSTER-009` and `REQ-CLUSTER-010` passes in `s3-reactive-api-adapter/target/surefire-reports/TEST-com.example.magrathea.s3api.cucumber.ep10.PhaseEp10ClusterCoordinationCucumberTest.xml`.
- Real grpc-java streaming, cancellation/deadline cleanup, and replica mTLS evidence for `REQ-CLUSTER-011`, `REQ-CLUSTER-012`, and the data-plane portion of `REQ-CLUSTER-013` passes in `cluster-data-grpc-infrastructure/target/surefire-reports/TEST-com.example.magrathea.cluster.data.grpc.ep10.PhaseEp10DataPlaneCucumberTest.xml`; `target/ep10-story-relevant-tests.log` records the combined focused mechanism reactor as successful.

These focused gates are the semantic basis for accepting this ADR's bounded baseline. Ordinary root Maven compilation, unit-test, dependency-convergence, layering, or reactor success is supporting integration evidence only: it does not substitute for these EP-10 gates and does not validate any later-slice capability.

### Explicitly unaccepted EP-10 scope

EP-10 remains partial. `REQ-CLUSTER-006` and `REQ-CLUSTER-007` are not implemented; `REQ-CLUSTER-014` is partial; and `REQ-CLUSTER-015` through `REQ-CLUSTER-018` are not implemented. Accordingly, multipart, conditional and versioned cluster writes, chunked-object transfer, erasure coding, dynamic membership and certificate lifecycle, rolling upgrades, healing, rebalance, automated orphan cleanup, and the broader partition/fault-injection suite remain absent or unvalidated. No evidence above supports a production-readiness or general distributed-storage claim.

## Related Requirements

- `REQ-CLUSTER-001..005` — implemented and validated fixed-cluster S3 behavior.
- `REQ-CLUSTER-008..013` — implemented and validated first-slice Ratis, direct-transfer, bounded-flow-control, failure, and mTLS mechanisms.
- `REQ-CLUSTER-006..007`, `REQ-CLUSTER-014..018` — partial or not implemented as stated above.
- PA-6 distributed placement and quorum policy requirements.

## Related ADRs

- ADR 0014 — Storage Engine bounded context.
- ADR 0025 — Conditional chunking and storage artifact taxonomy.
- ADR 0027 — Authoritative cluster control plane and direct quorum data path.
