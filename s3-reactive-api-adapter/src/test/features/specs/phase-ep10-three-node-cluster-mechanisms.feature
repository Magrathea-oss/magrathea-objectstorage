@spec @phase-ep10 @cluster @multi-node @internal-api
Ability: Execute a bounded and durable fixed three-node control and replica mechanism behind the S3 boundary
  Maintainers need a narrow internal mechanism for the first EP-10 slice so that A, B, and C can order
  bucket and object-reference generations, transfer whole-object replicas directly, and recover persisted
  state without contaminating the pure domain or creating a second object API.

  This feature specifies internal mechanism evidence. It complements, but cannot replace, the shared S3
  Business Need in "requirements/phase-ep10-three-node-s3-cluster.feature". No gRPC or Ratis endpoint is a
  supported client endpoint. The implemented-and-validated mechanism scope is the fixed first slice covered
  by REQ-CLUSTER-008 through REQ-CLUSTER-013; it does not mark the whole EP-10 mechanism backlog complete.

  Fixed node configuration:
    | node | stable UUID                          | Ratis address   | replica address | identity root                              | Ratis root                           | object root                            | temporary root                          | runtime root                          |
    | A    | 11111111-1111-4111-8111-111111111111 | 127.0.0.1:19801 | 127.0.0.1:19901 | target/ep10/three-node/node-a/identity     | target/ep10/three-node/node-a/ratis | target/ep10/three-node/node-a/objects | target/ep10/three-node/node-a/temporary | target/ep10/three-node/node-a/runtime |
    | B    | 22222222-2222-4222-8222-222222222222 | 127.0.0.1:19802 | 127.0.0.1:19902 | target/ep10/three-node/node-b/identity     | target/ep10/three-node/node-b/ratis | target/ep10/three-node/node-b/objects | target/ep10/three-node/node-b/temporary | target/ep10/three-node/node-b/runtime |
    | C    | 33333333-3333-4333-8333-333333333333 | 127.0.0.1:19803 | 127.0.0.1:19903 | target/ep10/three-node/node-c/identity     | target/ep10/three-node/node-c/ratis | target/ep10/three-node/node-c/objects | target/ep10/three-node/node-c/temporary | target/ep10/three-node/node-c/runtime |

  The internal transport test fixture "target/ep10/fixtures/grpc-three-frames.bin" is deterministically
  generated as 196625 bytes where byte at zero-based offset i is i modulo 251. Its SHA-256 is
  "4bb7439bc39bc2d0e3d6a915d7c81e38250a9c5bb320a94a19a95bba0d5fe40a".

  Rule: Fixed bootstrap creates three stable voters without pretending to support membership changes

    @REQ-CLUSTER-008 @functional-requirement @non-functional-requirement @bootstrap @node-identity @consensus @restart-safety @implemented-and-validated
    Scenario: A B and C recover one fixed three-voter group from stable identities
      Given each clean identity root is initialized once with its declared stable UUID
      And each node receives the identical bootstrap manifest containing A, B, and C with their UUIDs and Ratis addresses
      When all three nodes start the fixed cluster profile
      Then one Ratis group contains exactly A, B, and C as voters
      And seed order, hostname, process ID, and certificate serial number are not node identity
      And each node persists consensus log, snapshot, term, vote, and applied state beneath its own Ratis root
      When all nodes stop and restart from the same non-empty roots with seed order "C,A,B"
      Then their stable UUIDs, voter set, committed bucket generations, and committed object-reference generations are recovered
      And bootstrap configuration does not silently rewrite persisted Ratis or identity state

  Rule: The control log orders references while direct transfer owns data volume

    @REQ-CLUSTER-009 @functional-requirement @non-functional-requirement @consistency @durability @integrity @write-ordering @direct-transfer @implemented-and-validated
    Scenario: A whole-object reference is appended only after W=2 valid durable acknowledgements
      Given operation "put-ep10-first-quorum-object-001" resolves a committed membership, topology epoch "topology-1", policy epoch "policy-1", and current object generation
      And PA-6 selects A, B, and C exactly once for policy N=3/W=2
      And the 134-byte fixture has SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800"
      When coordinator A streams immutable bytes directly to the selected replica services
      Then each receiver stages an unpublished artifact beneath its temporary root
      And each durable acknowledgement is idempotently bound to operation ID, artifact ID, node UUID, length, checksum, topology epoch, and policy epoch
      And cancelled, expired, stale-epoch, checksum-invalid, length-invalid, or non-durable responses do not count toward W=2
      And the payload is absent from Ratis log entries and snapshots
      When 2 checksum-valid durable acknowledgements have been collected and fencing is revalidated
      Then exactly one object-reference generation naming only verified immutable artifacts is proposed and consensus committed
      And the S3 success signal is permitted only after that reference commit
      But before W=2, no object-reference proposal or S3 success signal is permitted

    @REQ-CLUSTER-010 @functional-requirement @non-functional-requirement @consistency @split-brain-safety @no-degraded-writes @failure-handling @implemented-and-validated
    Scenario Outline: Failure before reference commit leaves no authoritative generation
      Given operation "put-ep10-failure-001" uses fixed policy N=3/W=2
      And failure condition "<failure>" occurs
      When the write coordinator evaluates publication
      Then the operation ends as "write-failed-not-published"
      And no successful S3 signal or reduced-durability publication is permitted
      And no authoritative object-reference generation is committed
      And any already durable staged artifact remains unreachable and is not deleted by an unfenced in-memory task

      Examples:
        | failure |
        | only one checksum-valid durable acknowledgement is available |
        | two durable acknowledgements exist but only one Ratis voter is available |
        | a replica stream is cancelled before durable acknowledgement |
        | a replica deadline expires before durable acknowledgement |
        | a receiver computes a different length or SHA-256 |
        | coordinator A uses a stale topology or policy epoch |

  Rule: Direct replica streaming has finite demand and prompt terminal cleanup

    @REQ-CLUSTER-011 @functional-requirement @non-functional-requirement @streaming @backpressure @bounded-memory @grpc @resource-cleanup @implemented-and-validated
    Scenario: Manual gRPC flow control transfers a multi-frame fixture without unbounded buffering
      Given direct replica transport uses grpc-java manual inbound and outbound flow control
      And the maximum payload frame is 65536 bytes
      And queue capacity, in-flight frame count, message size, retry count, RPC deadline, and idle timeout each have explicit finite configured limits
      And automatic inbound demand is disabled where manual flow control requires it
      And fixture "target/ep10/fixtures/grpc-three-frames.bin" has length 196625 and SHA-256 "4bb7439bc39bc2d0e3d6a915d7c81e38250a9c5bb320a94a19a95bba0d5fe40a"
      When a slow receiver accepts the fixture from A in 65536-byte-or-smaller payload frames
      Then the sender emits only when gRPC readiness and downstream demand permit
      And the receiver requests additional input only after bounded downstream acceptance
      And no payload frame exceeds 65536 bytes
      And queued plus in-flight frames never exceed the configured finite bounds
      And the durably published artifact has exact length 196625 and the expected SHA-256
      And no blocking gRPC stub or blocking filesystem work executes on a Reactor event-loop thread

    @REQ-CLUSTER-012 @functional-requirement @non-functional-requirement @streaming @cancellation @deadline @resource-cleanup @failure-handling @implemented-and-validated
    Scenario Outline: Cancellation and deadline expiry release every staged streaming resource
      Given a replica receive stream has allocated bounded buffers, one temporary artifact, and an RPC deadline
      And terminal condition "<terminal_condition>" occurs before durable acknowledgement
      When the Reactor-to-gRPC bridge propagates the terminal signal
      Then upstream production and downstream I/O stop promptly
      And the gRPC call terminates with explicit non-success status
      And no durable acknowledgement is emitted or counted toward W=2
      And every owned buffer is released
      And the temporary file handle, channel demand, timer, and in-flight accounting are released
      And the partial artifact is removed or remains explicitly unpublished and unreachable for fenced cleanup

      Examples:
        | terminal_condition |
        | downstream cancellation |
        | RPC deadline expiry |

  Rule: Cluster transports are mutually authenticated internal infrastructure

    @REQ-CLUSTER-013 @functional-requirement @non-functional-requirement @security @mtls @internal-api @boundary @implemented-and-validated
    Scenario: Only an expected mutually authenticated node identity can use either cluster transport
      Given the test-local CA is generated under "target/ep10/pki/ca"
      And node certificates are mounted under "target/ep10/pki/nodes/A", "target/ep10/pki/nodes/B", and "target/ep10/pki/nodes/C"
      And each certificate identity is bound to its declared stable node UUID
      When a peer opens a Ratis control connection or replica-data connection
      Then both peers present and validate certificate chains against the configured cluster trust
      And the authenticated peer identity must match the expected stable UUID
      And plaintext, anonymous, server-authentication-only, wrong-CA, expired, and UUID-mismatched peers are rejected before cluster messages are accepted
      And Ratis control and replica data use separate ports, servers, executors, limits, metrics, and lifecycle ownership
      And neither listener exposes CreateBucket, PutObject, GetObject, multipart, list, delete, tagging, ACL, or metadata operations
      And only the existing S3 adapter can initiate external bucket and object behavior

  Rule: Mechanism boundaries keep infrastructure out of policy and S3 concerns out of cluster protocols

    @REQ-CLUSTER-014 @non-functional-requirement @architecture @boundary @pure-domain @internal-api @partial
    Scenario: Cluster modules preserve the application protocol infrastructure and domain boundaries
      Given the first-slice decomposition includes "storage-engine-cluster-application", "cluster-protocol", "cluster-control-ratis-infrastructure", and "cluster-data-grpc-infrastructure"
      When dependency and protocol boundaries are inspected
      Then "storage-engine-cluster-application" exposes transport-neutral cluster use cases and ports without Ratis, protobuf, generated-stub, or gRPC dependencies
      And "cluster-protocol" contains versioned internal protobuf contracts without S3, Spring application, storage-policy, or domain decision logic
      And Ratis types remain in "cluster-control-ratis-infrastructure"
      And grpc-java and generated replica stubs remain in "cluster-data-grpc-infrastructure"
      And Ratis, grpc-java, protobuf, Testcontainers, Spring, filesystem, certificate, retry, clock, and network types do not enter "storage-engine-domain"
      And the existing object-store storage-engine adapter remains the only S3-to-storage-engine integration boundary
      And existing PA-6 policy models remain partial planning support beyond these implemented first-slice network mechanisms

  Rule: Capabilities beyond the first fixed three-node slice remain explicit backlog

    @REQ-CLUSTER-015 @functional-requirement @non-functional-requirement @erasure-coding @direct-transfer @later-slice @not-implemented
    Scenario: Erasure-coded shard transfer and reconstruction are not implemented by replicated whole-object transfer
      Given the first slice transfers whole-object replicas using N=3/W=2
      When cluster capability status is reported
      Then EC encoding, data and parity shard transfer, policy-specific acknowledgement thresholds, and reconstruction are "not implemented"
      And no N=3/W=2 replica result is cited as EC evidence

    @REQ-CLUSTER-016 @functional-requirement @non-functional-requirement @membership @certificate-rotation @rolling-upgrade @later-slice @not-implemented
    Scenario: Dynamic lifecycle operations remain outside fixed bootstrap
      Given A, B, and C are declared by one static first-slice bootstrap manifest
      When lifecycle capability status is reported
      Then admission, catch-up, promotion, demotion, replacement, removal, and safe configuration changes are "not implemented"
      And certificate enrollment, rotation, revocation, expiry handling, and recovery are "not implemented"
      And protobuf and Ratis rolling-upgrade compatibility, leader transfer during upgrade, and mixed-version operation are "not implemented"
      And edits to seeds, address changes, health suspicion, or certificate replacement cannot be presented as those capabilities

    @REQ-CLUSTER-017 @functional-requirement @non-functional-requirement @healing @rebalance @orphan-cleanup @later-slice @not-implemented
    Scenario: Planned repair and movement do not imply durable job execution
      Given PA-6 can model healing and rebalance plans
      When cluster execution status is reported
      Then healing execution, anti-entropy execution, rebalance execution, and automated orphan cleanup are "not implemented"
      And no in-memory scheduler is reported as authoritative durable job ownership
      And planning output is not reported as copied, repaired, rebalanced, or reclaimed data

    @REQ-CLUSTER-018 @functional-requirement @non-functional-requirement @partition @fault-injection @later-slice @not-implemented
    Scenario: Partition behavior beyond the exercised one-node stop remains unproven
      Given the first acceptance fault stops coordinator A while B and C remain mutually reachable
      When cluster resilience status is reported
      Then asymmetric partitions, delayed and reordered messages, split control and data paths, stale leaders, duplicate identities, and repeated coordinator changes are "not implemented" or "not validated"
      And the one-node-stop proof is not described as general partition tolerance, two-node-loss tolerance, or distributed production readiness
