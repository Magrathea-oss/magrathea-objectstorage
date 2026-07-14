@spec @phase-ep10 @cluster @multi-node @internal-api
Ability: Execute a bounded and durable fixed three-node control and replica mechanism behind the S3 boundary
  Maintainers need a narrow internal mechanism for the first EP-10 slice so that A, B, and C can order
  bucket and object-reference generations, transfer whole-object replicas directly, and recover persisted
  state without contaminating the pure domain or creating a second object API.

  This feature specifies internal mechanism evidence. It complements, but cannot replace, the shared S3
  Business Need in "requirements/phase-ep10-three-node-s3-cluster.feature". No gRPC or Ratis endpoint is a
  supported client endpoint. The implemented-and-validated mechanism scope includes the fixed first slice
  covered by REQ-CLUSTER-008 through REQ-CLUSTER-013, the repository-rooted architecture contract covered by
  REQ-CLUSTER-014, the bounded repair scopes covered by REQ-CLUSTER-021 through REQ-CLUSTER-026, and bounded
  periodic current-reference anti-entropy covered by REQ-CLUSTER-027. For
  REQ-CLUSTER-014 only, the focused internal Ability gate parses Maven dependencies, tracked production Java
  through JDK ASTs, and the versioned proto3 contract; executes "scripts/check-module-layering.sh"; and fails
  closed for missing, unreadable, empty, or malformed inputs with exact paths and protected-input hash
  preservation. This internal architecture validation mode is the requirement's only agreed mode;
  REQ-CLUSTER-014 is not S3 behavior and does not require WebTestClient or AWS CLI validation. It proves source
  and build boundaries without claiming runtime side effects or broad healing. For REQ-CLUSTER-024 only, the
  focused internal Ability gate validates
  all seven named real-data-path interruption points with independent B-JVM crash and restart from real
  persisted Ratis voter state, actual grpc-java mTLS reads from source C, FileLocalArtifactStore staging,
  publication, and filesystem inspection, snapshot-write interruption, withheld completion replies,
  exact-target no-recopy reconciliation, stale-token fencing, and live A-to-C leader transfer. That internal
  Ability validation mode is the requirement's only agreed mode; REQ-CLUSTER-024 is not S3 behavior and does
  not require WebTestClient or AWS CLI validation. These bounded repair and discovery gates do not mark the
  whole EP-10 mechanism backlog complete and exclude orphan cleanup, rebalance, anti-entropy beyond fixed A/B/C
  current-reference obligations, dynamic membership, and erasure coding.

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

    @REQ-CLUSTER-014 @non-functional-requirement @architecture @boundary @pure-domain @internal-api @implemented-and-validated
    Scenario: Repository-rooted source inspection preserves cluster boundaries without claiming execution
      Given validation mode "repository-rooted production Maven, Java package/import, protobuf, and layering-script inspection with isolated fail-closed probes" is selected for requirement "REQ-CLUSTER-014"
      And root reactor model "pom.xml" and executable layering guard "scripts/check-module-layering.sh" are architecture-contract inputs
      And the inspected production boundary evidence is:
        | responsibility                              | module POM                                                               | production source or protocol path                                                                 |
        | transport-neutral cluster application       | storage-engine-cluster-application/pom.xml                               | storage-engine-cluster-application/src/main/java                                                    |
        | versioned internal cluster protocol         | cluster-protocol/pom.xml                                                  | cluster-protocol/src/main/proto/magrathea/cluster/v1/replica_service.proto                          |
        | Ratis control infrastructure                | cluster-control-ratis-infrastructure/pom.xml                              | cluster-control-ratis-infrastructure/src/main/java                                                   |
        | gRPC replica infrastructure                 | cluster-data-grpc-infrastructure/pom.xml                                  | cluster-data-grpc-infrastructure/src/main/java                                                       |
        | pure PA-6 storage-engine domain             | storage-engine-domain/pom.xml                                             | storage-engine-domain/src/main/java                                                                 |
        | Object Store to Storage Engine adapter      | object-store-reactive-repository-storage-engine-infrastructure/pom.xml    | object-store-reactive-repository-storage-engine-infrastructure/src/main/java                         |
        | S3 API adapter                              | s3-reactive-api-adapter/pom.xml                                           | s3-reactive-api-adapter/src/main/java                                                               |
      And isolated missing and malformed input probes use "target/ep10/req-cluster-014/architecture-contract-probes" without modifying tracked inputs
      When the validation mode parses every module declared by "pom.xml", every non-test Maven dependency including profile-specific declarations, and every tracked production Java package, import, and referenced type across those modules
      And it parses every protobuf declaration under "cluster-protocol/src/main/proto" and executes "scripts/check-module-layering.sh" from the repository root
      Then "storage-engine-cluster-application" exposes only transport-neutral cluster use cases and ports, with no non-test dependency or production source reference to Ratis, protobuf, generated replica stubs, grpc-java, or infrastructure modules
      And "cluster-protocol/src/main/proto/magrathea/cluster/v1/replica_service.proto" is a proto3 contract with protobuf package "magrathea.cluster.v1" and Java package "com.example.magrathea.cluster.protocol.v1"
      And all "cluster-protocol" contracts are versioned internal node-to-node transport only, with no S3 or Spring API and no storage-policy, placement, quorum, anti-entropy, rebalance, or other domain decision logic
      And Apache Ratis library package and type references rooted at "org.apache.ratis" occur only under "cluster-control-ratis-infrastructure/src/main/java"
      And direct grpc-java and protobuf runtime package and type references rooted at "io.grpc" and "com.google.protobuf", and generated replica-stub references rooted at "com.example.magrathea.cluster.protocol.v1", occur only under "cluster-data-grpc-infrastructure/src/main/java"
      And "storage-engine-domain" has no non-test dependency or production package, import, or type reference to Ratis, grpc-java, protobuf, Testcontainers, Spring, filesystem I/O, certificate, retry-runtime, Clock, or network APIs
      And only production sources beneath "object-store-reactive-repository-storage-engine-infrastructure/src/main/java" bridge Object Store repository ports to "com.example.magrathea.storageengine.cluster.application"
      And "s3-reactive-api-adapter/pom.xml" has no non-test dependency on "storage-engine-cluster-application" or any "cluster-*" module, while "s3-reactive-api-adapter/src/main/java" has no cluster package import
      And PA-6 "DistributedPlacementPlanner", "AntiEntropyPlanner", and "RebalancePlanner" beneath "storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/distributed" remain side-effect-free planning models whose plans, findings, moves, and dry runs are never reported as copied, repaired, rebalanced, or reclaimed data
      And a missing, unreadable, empty, or unparseable contract input makes validation fail with its exact repository path instead of treating absent dependencies, declarations, imports, or types as compliant
      But this architecture evidence does not claim broad or periodic anti-entropy, rebalance execution, orphan cleanup, or production readiness

  Rule: Consensus owns one deterministic repair job for one current replica obligation

    @REQ-CLUSTER-021 @functional-requirement @non-functional-requirement @repair @consensus @idempotence @deduplication @implemented-and-validated
    Scenario: Repeated observations and changing source hints ensure one stable repair job
      Given current reference generation 7 for bucket "ep10-repair-archive" and key "evidence/2026/current-generation-repair.bin" names artifact "whole-7f351d76-50d8-4f48-9b86-6f94e777a101" on A, B, and C
      And the canonical repair identity is "REPAIR", that bucket, that object key, generation 7, that artifact, and target UUID "22222222-2222-4222-8222-222222222222"
      And its immutable specification records length 134, SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800", topology epoch "topology-1", and policy epoch "policy-1"
      When missing and corrupt observations from different requests, coordinators, leaders, and workers repeatedly ensure that exact obligation
      And source C is the first attempt hint but source A is a later attempt hint
      Then every ensure returns the same deterministically derived job ID and one consensus-owned logical job
      And observation ID, request ID, coordinator, leader, worker, process session, and candidate source are excluded from repair identity
      And a duplicate ensure command returns its committed result without another job, transition, or attempt record
      When fresh damage is observed after that exact job has reached "SUCCEEDED" and generation 7 remains current
      Then explicit re-evaluation reactivates the same logical job rather than creating another identity
      And attempt number and claim generation remain monotonic across reactivation

  Rule: The repair lifecycle is explicit deterministic consensus state

    @REQ-CLUSTER-022 @functional-requirement @non-functional-requirement @repair @consensus @state-machine @retry @auditability @implemented-and-validated
    Scenario Outline: Only an applicable committed command performs a legal repair transition
      Given one repair job is in state "<from_state>" with current reference and fencing facts matching the command unless the condition says otherwise
      When the leader proposes command data for "<condition>" and the Ratis state machine applies it
      Then the committed job state becomes "<to_state>"
      And the transition, command result, command-supplied timestamp, attempt history, and reason are deterministic and auditable
      And duplicate delivery returns the committed result without a second transition or attempt record
      And no filesystem, network, random, sleep, retry, or wall-clock side effect occurs in the state-machine transaction
      But "OBSOLETE" is terminal and is never rewritten for a newer reference generation

      Examples: Legal lifecycle transitions
        | from_state                                           | condition                                                               | to_state   |
        | absent                                               | ensure exact applicable current-generation work                         | READY      |
        | READY                                                | accept a due claim with the next claim generation                        | CLAIMED    |
        | RETRY_WAIT                                           | committed next-eligible time has arrived and a new claim is accepted     | CLAIMED    |
        | CLAIMED                                              | current-token bounded transport or temporary target failure              | RETRY_WAIT |
        | CLAIMED                                              | current-token source exhaustion, integrity conflict, or retry exhaustion | BLOCKED    |
        | CLAIMED                                              | current-token exact durable target publication                           | SUCCEEDED  |
        | READY, CLAIMED, RETRY_WAIT, BLOCKED, or SUCCEEDED     | bound reference is no longer the current exact target obligation         | OBSOLETE   |
        | BLOCKED                                              | explicit re-evaluation finds the same reference current and progress safe | READY      |
        | SUCCEEDED                                            | fresh damage is proved while the same reference remains current          | READY      |

  Rule: Claim generation and process session jointly reject stale workers

    @REQ-CLUSTER-023 @functional-requirement @non-functional-requirement @repair @consensus @fencing @process-session @split-brain-safety @implemented-and-validated
    Scenario: A restarted worker cannot complete its predecessor's reclaimed claim
      Given job "repair-current-7-b" is "CLAIMED" by stable node UUID "22222222-2222-4222-8222-222222222222" and process session "b-session-0041"
      And attempt 11 has safety token "repair-current-7-b,11" and a committed claim deadline
      When process B restarts as process session "b-session-0042"
      Then the new session cannot renew, fail, block, obsolete, or complete claim generation 11
      When Ratis commits a reclaim by session "b-session-0042"
      Then attempt number and claim generation both increase to 12
      And the only current safety token is "repair-current-7-b,12"
      When the old process resumes and submits durable-publication completion for token "repair-current-7-b,11"
      Then completion is rejected as stale with no job-state or attempt-history effect
      And expiry of the old deadline, the current Ratis term, and a leader change do not authorize the old token
      When session "b-session-0042" submits exact durable-publication completion for token "repair-current-7-b,12"
      Then the job becomes "SUCCEEDED" exactly once
      And duplicate delivery of that accepted completion returns the committed result without duplicate completion

  Rule: Repair remains idempotent across worker crashes process restarts snapshots and leader changes

    @REQ-CLUSTER-024 @functional-requirement @non-functional-requirement @repair @durability @restart-safety @leader-change @idempotence @fault-injection @grpc @mtls @filesystem @real-process-required @filesystem-inspection-required @implemented-and-validated
    Scenario Outline: A fixed-node repair survives interruption at a real data-path boundary without duplicate publication
      Given validation mode "fixed A/B/C real-process repair with Ratis persistence, gRPC/mTLS transfer, filesystem inspection, and replica-read attempt counting" is selected for requirement "REQ-CLUSTER-024"
      And current consensus-committed "WHOLE_OBJECT" reference generation 7 for bucket "ep10-repair-archive" and key "evidence/2026/current-generation-repair.bin" names artifact "whole-7f351d76-50d8-4f48-9b86-6f94e777a101" on source C and target B
      And fixture "s3-reactive-api-adapter/src/test/resources/fixtures/upload/large-object.bin" has length 134 and SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800"
      And source replica C path "target/ep10/three-node/node-c/objects/whole-7f351d76-50d8-4f48-9b86-6f94e777a101.artifact" is byte-for-byte equal to that fixture
      But target B path "target/ep10/three-node/node-b/objects/whole-7f351d76-50d8-4f48-9b86-6f94e777a101.artifact" is initially absent
      And the one canonical consensus-owned job "repair-e0e88640c5538c99201e0fa7201b08fdb6024ecef4fc739640e2d3ef3a1dcdd2" targets B and has next admissible claim generation 11
      And claim 11 may stage only at "target/ep10/three-node/node-b/temporary/repair/repair-e0e88640c5538c99201e0fa7201b08fdb6024ecef4fc739640e2d3ef3a1dcdd2/11/payload.part", while a post-restart claim 12 may stage only at "target/ep10/three-node/node-b/temporary/repair/repair-e0e88640c5538c99201e0fa7201b08fdb6024ecef4fc739640e2d3ef3a1dcdd2/12/payload.part"
      When the focused requirement gate reaches "<failure_point>"
      Then immediately before interruption, claim and staging inspection reports "<claim_and_staging_state>"
      And replica-read instrumentation reports "<transfer_state_at_interruption>"
      And target filesystem inspection reports "<target_state_at_interruption>"
      When validation performs "<interruption_action>" at that exact semantic point
      Then a crashed B process recovers from its original non-empty roots, or the named leader action completes while B remains alive
      And the scheduler recovers the one committed job before proposing work, with monotonic attempt and claim generations and no authority for a stale token
      And direct-repair evidence across interruption and recovery is "<expected_transfer_evidence>"
      And every expected transfer is an actual grpc-java replica-read RPC opened by B to source C at "127.0.0.1:19903" with B and C mutually authenticated by their UUID-bound certificates under "target/ep10/pki/nodes/B" and "target/ep10/pki/nodes/C"
      And no in-memory replica-read port, Ratis command, target pre-seeding, or test-file copy counts as replica transfer evidence
      And each received byte is written only to the current token's "payload.part" before incremental length and SHA-256 verification, file fsync, atomic target publication, and parent-directory fsync
      And at every observation the target path is either absent or the exact 134-byte fixture, never a partial or checksum-invalid publication
      And recovery reconciliation is "<expected_reconciliation>"
      And if the exact target was durable before interruption, recovery probes it in place without another replica-read RPC, another staging payload, or replacement of its bytes
      And the final target is byte-for-byte equal to the fixture with length 134 and SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800"
      And the one repair job commits or restores "SUCCEEDED" exactly once while reference generation 7, its artifact, the fixed A/B/C topology, and its named replica obligation remain unchanged
      But the focused semantic gate does not provide a general chaos or partition engine, periodic or broad anti-entropy, rebalance, orphan cleanup, superseded-generation collection, or reference rewriting

      Examples: Required real data-path interruption points
        | failure_point                                                                                     | claim_and_staging_state                                                                 | transfer_state_at_interruption                                             | target_state_at_interruption                                                                                 | interruption_action                                                                                                        | expected_transfer_evidence                                                                    | expected_reconciliation                                                                                          |
        | READY ensure is committed and before claim 11 is proposed                                        | the job is READY and neither the claim-11 directory nor payload.part exists             | zero replica-read RPCs                                                     | absent                                                                                                       | crash B, retain the A/C quorum, and restart B from its original roots                                                       | one post-restart claim-11 mTLS gRPC read from C and no other replica-read RPC                    | claim 11 publishes once and commits SUCCEEDED once                                                              |
        | claim 11 is committed and before B opens a replica read to C                                     | claim 11 is committed and claim-11 payload.part does not exist                           | zero replica-read RPCs                                                     | absent                                                                                                       | crash B, let its process session expire, then restart B and reclaim as claim 12                                             | one post-restart claim-12 mTLS gRPC read from C and no other replica-read RPC                    | stale claim 11 cannot publish; claim 12 publishes once and commits SUCCEEDED once                                  |
        | claim 11 has staged a non-empty strict prefix during the bounded replica read from C             | claim 11 is committed and claim-11 payload.part is a non-empty strict fixture prefix     | one live mTLS gRPC read from C has delivered fewer than 134 bytes          | absent while partial bytes exist only in claim-11 staging                                                     | crash B and terminate the in-flight RPC before another byte is staged, then restart B and reclaim as claim 12              | the interrupted claim-11 RPC plus one complete claim-12 mTLS gRPC read from C and no third RPC   | partial claim-11 staging is never published; claim 12 publishes once and commits SUCCEEDED once                    |
        | claim 11 has durably published the exact target and before completion is proposed                | claim-11 staging has been atomically removed after publication                           | one mTLS gRPC read from C delivered and verified exactly 134 bytes         | present with length 134 and SHA-256 46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800       | crash B after parent-directory fsync and before completion is proposed, then restart B and reclaim as claim 12             | the one completed claim-11 mTLS gRPC read from C and zero replica-read RPCs after restart       | claim 12 probes the already-exact target and commits SUCCEEDED without recopy                                   |
        | claim-11 completion is committed and before its acknowledgement reaches B                       | claim-11 staging is absent and the committed job is SUCCEEDED                            | one mTLS gRPC read from C delivered and verified exactly 134 bytes         | present with length 134 and SHA-256 46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800       | withhold only that committed completion reply, crash B, and restart B from its original roots                                 | the one completed claim-11 mTLS gRPC read from C and zero replica-read RPCs after restart       | restored SUCCEEDED and duplicate completion return the committed result without another claim or recopy          |
        | B writes snapshot version 2 after claim 11 commits and before transfer starts                    | claim 11 is committed and claim-11 payload.part does not exist                           | zero replica-read RPCs                                                     | absent                                                                                                       | crash B during its snapshot write, retain the A/C quorum, then restart B from the last valid snapshot plus its later log    | one post-restart claim-12 mTLS gRPC read from C and no other replica-read RPC                    | restored claim fencing rejects claim 11; claim 12 publishes once and commits SUCCEEDED once                       |
        | claim 11 is active before transfer while A is the Ratis leader                                   | live B owns claim 11 and claim-11 payload.part does not exist                             | zero replica-read RPCs                                                     | absent                                                                                                       | transfer Ratis leadership from A to C while claim 11 remains owned by the live B process                                    | one claim-11 mTLS gRPC read from C after leader transfer and no duplicate replica-read RPC      | the still-current claim publishes once and completion commits under the new leader without a replacement claim    |

  Rule: A repair job cannot outlive the exact current-reference obligation

    @REQ-CLUSTER-025 @functional-requirement @non-functional-requirement @repair @consistency @generation-fencing @obsolete @implemented-and-validated
    Scenario Outline: A generation change makes old repair work obsolete at every execution fence
      Given repair job "repair-current-7-b" is bound to current reference generation 7, artifact "whole-7f351d76-50d8-4f48-9b86-6f94e777a101", and target B
      And the worker has reached "<execution_fence>"
      When consensus commits generation 8 as current with a different artifact or without that exact target obligation
      Then the worker stops old-generation transfer or completion work
      And the job for generation 7 becomes terminal "OBSOLETE"
      And an old token cannot report "SUCCEEDED" or modify generation 8
      And any exact old-generation bytes already published at B remain non-authoritative and unreachable through the current reference
      And generation 8 requires its own deterministic repair identity if it has a repair obligation
      And no orphan cleanup, superseded-generation collection, reference rewrite, rebalance, or placement change is performed by this repair transition

      Examples: Required currentness fences
        | execution_fence                       |
        | before choosing a named source        |
        | immediately before target publication |
        | immediately before proposing success  |

  Rule: Snapshot version 2 recovers repair state and safely migrates version 1

    @REQ-CLUSTER-026 @functional-requirement @non-functional-requirement @repair @snapshot @migration @restart-safety @fencing @implemented-and-validated
    Scenario: A version 1 snapshot migrates to version 2 and restored jobs preserve their fences
      Given snapshot version 1 at last-applied term 4 and index 91 contains fixed membership epochs, bucket "ep10-repair-archive", and current object reference generation 7 but no repair-job section
      When the version 2 control state machine loads that version 1 snapshot
      Then it recovers the exact membership, bucket, reference, and last-applied term and index
      And it deterministically initializes an empty repair-job map without inventing work
      When committed commands create repair records in these states before the next snapshot
        | job                    | state       | claim owner and session | attempt | claim generation | next eligible or reason       |
        | repair-ready-b         | READY       | none                    | 0       | 0                | eligible now                  |
        | repair-claimed-b       | CLAIMED     | B / b-session-0042      | 12      | 12               | deadline 2026-07-13T16:05:00Z |
        | repair-retry-b         | RETRY_WAIT  | none                    | 5       | 5                | 2026-07-13T16:06:40Z          |
        | repair-blocked-b       | BLOCKED     | none                    | 8       | 8                | no verified source remains    |
        | repair-succeeded-b     | SUCCEEDED   | none                    | 4       | 4                | exact durable publication     |
        | repair-obsolete-b      | OBSOLETE    | none                    | 3       | 3                | generation 8 became current   |
      And the node writes snapshot version 2 with immutable specifications, retry policies, attempt histories, command deduplication results, and last-applied term and index
      And the node stops and restores from that snapshot plus later log entries
      Then every repair identity, state, owner, process session, deadline, attempt, claim generation, retry time, reason, and deduplication result is recovered exactly
      And completion carrying an older token for "repair-claimed-b" remains rejected as stale after restore
      And object payload bytes and temporary transfer files are absent from both snapshot versions
      And the next snapshot remains version 2
      But an unknown future snapshot version fails closed without downgrading or discarding repair jobs

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

    @REQ-CLUSTER-027 @functional-requirement @non-functional-requirement @healing @anti-entropy @periodic @durability @backpressure @restart-safety @consensus @grpc @mtls @filesystem @implemented-and-validated
    Scenario: Bounded periodic discovery repairs only damaged current replicas named for each fixed node
      Given validation mode "fixed A/B/C Ratis current-reference paging with real filesystem targets, actual grpc-java UUID-bound mTLS repair, consensus and filesystem inspection, and query, probe, repair, page, overlap, and replica-read counters" is selected for requirement "REQ-CLUSTER-027"
      And fixture "s3-reactive-api-adapter/src/test/resources/fixtures/upload/large-object.bin" has length 134 and SHA-256 "46918899a9ddbe1d1c2f1613416501b3e8d7cdbc2a63a78298d0cc3ee388e800"
      And current consensus contains these "WHOLE_OBJECT" ObjectReferenceGeneration records in canonical bucket, object-key, and generation order:
        | bucket                      | key                                                   | generation | artifact                                      | named replicas | B target beneath target/ep10/three-node/node-b/objects                                                |
        | ep10-anti-entropy-archive   | evidence/2026/anti-entropy/corrupt-on-b.bin           | 7          | whole-ae-corrupt-7f351d76-50d8-4f48-9b86-001 | A,B,C          | corrupt whole-ae-corrupt-7f351d76-50d8-4f48-9b86-001.artifact                                         |
        | ep10-anti-entropy-archive   | evidence/2026/anti-entropy/exact-on-b.bin             | 3          | whole-ae-exact-7f351d76-50d8-4f48-9b86-002   | A,B,C          | exact whole-ae-exact-7f351d76-50d8-4f48-9b86-002.artifact                                             |
        | ep10-anti-entropy-archive   | evidence/2026/anti-entropy/missing-on-b.bin           | 11         | whole-ae-missing-7f351d76-50d8-4f48-9b86-003 | A,B,C          | absent whole-ae-missing-7f351d76-50d8-4f48-9b86-003.artifact                                          |
        | ep10-anti-entropy-archive   | evidence/2026/anti-entropy/not-named-on-b.bin         | 5          | whole-ae-other-7f351d76-50d8-4f48-9b86-004   | A,C            | unprobed whole-ae-other-7f351d76-50d8-4f48-9b86-004.artifact                                          |
      And each named source artifact under "target/ep10/three-node/node-a/objects" or "target/ep10/three-node/node-c/objects" is independently equal to that fixture
      And the consensus page limit is 2, the inter-page operator is concat-style serial continuation, and one node may own at most one active discovery page and one bounded repair execution
      When the focused periodic anti-entropy gate executes bounded cycles
      Then Ratis exposes only current ObjectReferenceGeneration records as bounded pages in that canonical order
      And each non-terminal page has an exclusive cursor equal to its last record and the next page contains only records strictly after that cursor
      And invalid page limits and cursors, malformed query or page encodings, trailing input, and decoded pages above hard limit 256 fail closed
      And no page exceeds limit 2, no cycle materializes all references, and page, probe, ensure, and repair counters show no overlapping page or target processing
      And periodic cycles on fixed nodes A, B, and C inspect only current references whose named replica UUID set contains that local node
      And B never probes or repairs "whole-ae-other-7f351d76-50d8-4f48-9b86-004.artifact" because its current reference does not name B
      And each local named target is probed for existence and, when present, verified against the committed length 134 and committed SHA-256 before repair is considered
      And every local filesystem probe executes on Reactor boundedElastic rather than a caller or event-loop thread
      And closing B's process-local discovery scheduler while a bounded page response is delayed cancels that active cycle and leaves no active page or target probe
      And the missing and corrupt B obligations each durably ensure exactly one existing canonical consensus-owned repair job bound to bucket, key, generation, artifact, and B UUID "22222222-2222-4222-8222-222222222222"
      And duplicate observations do not create another job, transition, claim, or publication
      And existing exact-current claim and publication fences execute each required repair from a different verified named replica over an actual grpc-java RPC with UUID-bound mutual TLS
      And the repaired B targets are byte-for-byte equal to the fixture while the already exact B target causes zero repair ensures, claims, replica-read RPCs, or replacement publications

    @REQ-CLUSTER-027 @functional-requirement @non-functional-requirement @healing @anti-entropy @periodic @durability @backpressure @restart-safety @consensus @observability @failure-handling @implemented-and-validated
    Scenario: Cursor recovery failures and reference races remain bounded safe and retryable across cycles
      Given validation mode "fixed A/B/C Ratis current-reference paging with real filesystem targets, actual grpc-java UUID-bound mTLS repair, consensus and filesystem inspection, and query, probe, repair, page, overlap, and replica-read counters" is selected for requirement "REQ-CLUSTER-027"
      And a process-local exclusive cursor starts before the first canonical current reference with configured page limit 2
      And later cycles inject one bounded current-reference query failure, one local target probe failure, one repair ensure failure, and one fenced repair execution failure
      And two current generation 7 obligations for bucket "ep10-anti-entropy-archive" race independently: key "evidence/2026/anti-entropy/ensure-reference-race.bin" changes to generation 8 before a stale ensure, while key "evidence/2026/anti-entropy/publication-reference-race.bin" changes to generation 8 at typed gate "BEFORE_TARGET_PUBLICATION" after transfer and before the final exact-current recheck and target publication
      When the focused periodic anti-entropy gate executes bounded cycles
      Then the process-local cursor advances only after every record in a page has completed inspection and any required durable ensure
      And a query, probe, or ensure failure leaves that page cursor unadvanced, increments a labeled failure counter without logging object bytes or credentials, and is retried by a later periodic cycle
      And a fenced repair execution failure remains explicit committed retryable repair state and is retried by existing repair execution rather than counted as discovery success
      And after a terminal page the next periodic cycle resets to the first page instead of polling forever after the terminal cursor
      And after B's process-local discovery and repair schedulers are closed and reconstructed in the same JVM, B begins discovery at the first page without persisting or trusting its prior process-local cursor
      And repeated first-page discovery after B scheduler reconstruction deduplicates through the canonical consensus-owned repair identity and command results
      And the distinct generation 7 work changed before ensure or at "BEFORE_TARGET_PUBLICATION" is rejected or made "OBSOLETE", and the publication-race target remains absent until current work repairs it
      And a later cycle discovers both generation 8 references and ensures their distinct canonical obligations without rewriting either current reference
      And after B's process-local schedulers are closed their inspection reports no active discovery page, target probe, or repair scan
      And inspection exposes cycle, page, record, exact-target, missing-target, corrupt-target, query-failure, probe-failure, ensure-failure, repair-failure, retry, cursor-reset, stale-reference, deduplication, overlap, and grpc replica-read counters
      And PA-6 AntiEntropyPlanner findings or plans remain planner output and are never treated as an ensure, claim, transfer, publication, repair success, or execution counter
      But this bounded gate excludes rebalance, orphan cleanup, dynamic membership, broad partitions, erasure coding, chunks, multipart, versioning, production scale or readiness, and ADR 0030 general chaos

    @REQ-CLUSTER-017 @functional-requirement @non-functional-requirement @healing @rebalance @orphan-cleanup @later-slice @partial
    Scenario: Bounded current-generation repair does not imply broad healing rebalance or cleanup
      Given PA-6 can model healing and rebalance plans without executing their data side effects
      And REQ-CLUSTER-019, REQ-CLUSTER-020, REQ-CLUSTER-021, REQ-CLUSTER-022, REQ-CLUSTER-023, REQ-CLUSTER-024, REQ-CLUSTER-025, REQ-CLUSTER-026, and REQ-CLUSTER-027 are "implemented-and-validated" within their bounded repair and discovery scopes
      When cluster execution status is reported against production behavior and semantic validation
      Then bounded current-generation whole-object repair and bounded periodic current-generation fixed A/B/C anti-entropy are "implemented-and-validated" only within those narrower requirement scopes
      But REQ-CLUSTER-017 remains "partial" because rebalance execution, automated orphan cleanup, and wider healing or topology coverage remain "absent"
      And no planner result, dry run, job schema, route, or in-memory scheduler is reported as copied, repaired, rebalanced, or reclaimed data

    @REQ-CLUSTER-018 @functional-requirement @non-functional-requirement @partition @fault-injection @later-slice @not-implemented
    Scenario: Partition behavior beyond the exercised one-node stop remains unproven
      Given the first acceptance fault stops coordinator A while B and C remain mutually reachable
      When cluster resilience status is reported
      Then asymmetric partitions, delayed and reordered messages, split control and data paths, stale leaders, duplicate identities, and repeated coordinator changes are "not implemented" or "not validated"
      And the one-node-stop proof is not described as general partition tolerance, two-node-loss tolerance, or distributed production readiness
