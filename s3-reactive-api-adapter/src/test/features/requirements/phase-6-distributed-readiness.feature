@requirement @phase-6 @distributed-readiness @storage-engine @implemented-not-e2e-validated
Ability: Phase 6 distributed readiness model for storage-engine placement, quorum, membership, healing, and rebalancing
  As a storage-engine operator and system owner,
  I want distributed-readiness behavior to be modeled with explicit placement, quorum, membership, health, healing, and rebalancing decisions,
  So that Magrathea can support future multi-node S3 durability work without claiming distributed production readiness before real multi-node behavior is implemented and validated.

  This feature is the single source of truth for Phase 6 distributed readiness requirements.
  It describes internal storage-engine operational capabilities that support externally visible S3 behavior.
  It does not create or require public storage-engine object API endpoints. Object upload, read, delete,
  list, metadata, tagging, ACL, multipart, and bucket behavior remain externally accessible only through
  S3-compatible APIs. Admin-only operational status may expose readiness, topology, health, planning,
  healing, and rebalancing information where those capabilities have no direct S3 API equivalent.

  These requirements are intentionally honest about the current scope. Until real networked membership,
  real replication job execution, durable multi-node manifests, and full end-to-end multi-node validation
  exist, distributed readiness must be reported as not implemented, single-node, or distributed simulation,
  not as distributed-production-ready.

  Future validation roles:
    - Placement planner unit validation checks deterministic placement decisions from a modeled topology.
    - Quorum policy unit validation checks write and read quorum outcomes without committing unverified data.
    - Health-model validation checks node membership, health state, and exclusion or risk annotations.
    - Anti-entropy validation scans modeled object manifests and replica observations to create healing plans.
    - Rebalancing validation creates safe move plans and task outcomes without reducing object quorum.
    - Admin status/report validation checks honest distributed-readiness classification in ARC42 and test reports.

  Shared topology identifiers for these requirements:
    | node id | failure domain | disk set id       | storage root                                      | initial health |
    | node-a  | rack-1         | disk-set-hot-a-01 | /var/lib/magrathea/distributed/node-a/objects    | HEALTHY        |
    | node-b  | rack-2         | disk-set-hot-b-01 | /var/lib/magrathea/distributed/node-b/objects    | HEALTHY        |
    | node-c  | rack-3         | disk-set-hot-c-01 | /var/lib/magrathea/distributed/node-c/objects    | HEALTHY        |
    | node-d  | rack-4         | disk-set-hot-d-01 | /var/lib/magrathea/distributed/node-d/objects    | HEALTHY        |

  Shared object and profile identifiers:
    | bucket             | key                             | storage profile property               | profile value           | manifest id                                                |
    | distributed-bucket | datasets/2026-06/report.parquet | magrathea.storage.backend.distributed  | simulated-distributed   | manifest-distributed-bucket-datasets-2026-06-report-parquet |

  Background:
    Given distributed storage behavior is evaluated as an internal storage-engine capability
    And external object behavior remains available only through the S3-compatible API
    And the distributed profile property "magrathea.storage.backend.distributed" is set to "simulated-distributed" for modeled planner validation
    And the modeled topology catalog is loaded from "config/storage/distributed-topology-phase-6.yml"
    And all planning, quorum, healing, rebalancing, and status decisions are observable as structured storage-engine decisions

  Rule: The storage engine must compute deterministic placement plans across nodes and failure domains before claiming distributed readiness
    Placement planning must select replica locations from explicit node membership and failure-domain data.
    A plan must be deterministic for the same topology, object identity, storage policy, and health state,
    and it must fail clearly when the requested failure-domain diversity cannot be satisfied.

    @REQ-DIST-001 @REQ-DIST-001-A @functional-requirement @non-functional-requirement @distributed-readiness @placement @determinism @failure-domain @implemented-not-e2e-validated
    Scenario: Replicated object placement uses three distinct nodes and failure domains
      Given topology "phase-6-three-rack-topology" contains nodes "node-a", "node-b", and "node-c"
      And node "node-a" belongs to failure domain "rack-1" with disk set "disk-set-hot-a-01" and storage root "/var/lib/magrathea/distributed/node-a/objects"
      And node "node-b" belongs to failure domain "rack-2" with disk set "disk-set-hot-b-01" and storage root "/var/lib/magrathea/distributed/node-b/objects"
      And node "node-c" belongs to failure domain "rack-3" with disk set "disk-set-hot-c-01" and storage root "/var/lib/magrathea/distributed/node-c/objects"
      And all three nodes are "HEALTHY"
      When the storage engine plans placement for bucket "distributed-bucket" key "datasets/2026-06/report.parquet" with replication factor 3
      Then the placement decision is "placement-plan-created"
      And the selected placements contain exactly three replica targets
      And the selected placements use nodes "node-a", "node-b", and "node-c" only once each
      And the selected placements use failure domains "rack-1", "rack-2", and "rack-3" only once each
      And the placement plan records storage roots for every selected node
      And repeating the same planning request against the same topology produces the same ordered placement plan

    @REQ-DIST-001 @REQ-DIST-001-B @functional-requirement @non-functional-requirement @distributed-readiness @placement @failure-domain @failure-handling @implemented-not-e2e-validated
    Scenario: Placement fails clearly when too few healthy failure domains exist for replication factor three
      Given topology "phase-6-two-healthy-racks" contains nodes "node-a", "node-b", and "node-c"
      And node "node-a" in failure domain "rack-1" is "HEALTHY"
      And node "node-b" in failure domain "rack-2" is "HEALTHY"
      And node "node-c" in failure domain "rack-3" is "DOWN"
      When the storage engine plans placement for bucket "distributed-bucket" key "datasets/2026-06/report.parquet" with replication factor 3
      Then the placement decision is "insufficient-failure-domains"
      And no placement plan is published as ready for commit
      And the decision reports requested failure domain count 3 and available healthy failure domain count 2
      And the decision explains that multiple replicas must not be silently placed in the same failure domain
      And readiness status for this topology remains "distributed-simulation-not-ready"

  Rule: Quorum decisions must be explicit for writes and reads
    Quorum policy must decide whether a write can be committed and whether a read can return data.
    Read quorum must include integrity validation so that corrupted or unverified replicas cannot satisfy
    a response merely by being present.

    @REQ-DIST-002 @REQ-DIST-002-A @functional-requirement @non-functional-requirement @distributed-readiness @quorum @write-quorum @observability @implemented-not-e2e-validated
    Scenario: Write quorum succeeds when two of three replicas acknowledge persistence
      Given replication factor 3 is configured for bucket "distributed-bucket" key "datasets/2026-06/report.parquet"
      And write quorum is configured as 2 acknowledgements
      And planned replicas target nodes "node-a", "node-b", and "node-c"
      And replica persistence acknowledgements are received from "node-a" and "node-b"
      When the storage engine evaluates the write quorum for manifest "manifest-distributed-bucket-datasets-2026-06-report-parquet"
      Then the quorum decision is "quorum-met"
      And the decision records acknowledged nodes "node-a" and "node-b"
      And the decision records missing acknowledgement from "node-c"
      And the manifest may be published with committed status "WRITE_QUORUM_MET"
      And an observable quorum event records replication factor 3, write quorum 2, and acknowledgement count 2

    @REQ-DIST-002 @REQ-DIST-002-B @functional-requirement @non-functional-requirement @distributed-readiness @quorum @write-quorum @failure-handling @implemented-not-e2e-validated
    Scenario: Write quorum fails when only one of three replicas acknowledges persistence
      Given replication factor 3 is configured for bucket "distributed-bucket" key "datasets/2026-06/report.parquet"
      And write quorum is configured as 2 acknowledgements
      And planned replicas target nodes "node-a", "node-b", and "node-c"
      And a replica persistence acknowledgement is received only from "node-a"
      When the storage engine evaluates the write quorum for manifest "manifest-distributed-bucket-datasets-2026-06-report-parquet"
      Then the quorum decision is "quorum-not-met"
      And the decision records acknowledged node "node-a"
      And the decision records missing acknowledgements from "node-b" and "node-c"
      And no committed manifest publication is created for bucket "distributed-bucket" key "datasets/2026-06/report.parquet"
      And the failure reason includes required write quorum 2 and acknowledgement count 1

    @REQ-DIST-002 @REQ-DIST-002-C @functional-requirement @non-functional-requirement @distributed-readiness @quorum @read-quorum @integrity @failure-handling @implemented-not-e2e-validated
    Scenario: Read quorum fails when one valid replica and one corrupted replica are observed
      Given read quorum is configured as 2 verified replicas for bucket "distributed-bucket" key "datasets/2026-06/report.parquet"
      And manifest "manifest-distributed-bucket-datasets-2026-06-report-parquet" expects replicas on "node-a", "node-b", and "node-c"
      And node "node-a" reports checksum "sha256:4c81f0bfe0c4f7c9d8f971a5de89b372176f2cdd1f6e8ee4b316f8f94b35f987" matching the manifest checksum
      And node "node-b" reports checksum "sha256:0000000000000000000000000000000000000000000000000000000000000000" that does not match the manifest checksum
      And node "node-c" is unavailable for this read
      When the storage engine evaluates the read quorum for the object
      Then the read decision is "integrity-quorum-not-met"
      And the valid replica count is 1
      And the corrupted replica on "node-b" is excluded from the verified read quorum
      And no object bytes are returned to the S3 read path from unverified replicas
      And an integrity finding records bucket "distributed-bucket", key "datasets/2026-06/report.parquet", node "node-b", and finding type "checksum-mismatch"

  Rule: Placement must use node membership and health state
    New write placement must select from known members and use health state as an input.
    Unhealthy nodes must be excluded, degraded nodes must be risky fallbacks, and an all-down topology
    must fail without inventing implicit placement targets.

    @REQ-DIST-003 @REQ-DIST-003-A @functional-requirement @non-functional-requirement @distributed-readiness @membership @health-model @placement @failure-handling @implemented-not-e2e-validated
    Scenario: Placement excludes a DOWN node and records the exclusion reason
      Given topology "phase-6-health-aware-topology" contains nodes "node-a", "node-b", and "node-c"
      And node "node-a" is "HEALTHY"
      And node "node-b" is "DOWN"
      And node "node-c" is "HEALTHY"
      When the storage engine plans a new write placement for bucket "distributed-bucket" key "datasets/2026-06/report.parquet" with replication factor 2
      Then the placement decision is "placement-plan-created"
      And the selected placements include nodes "node-a" and "node-c"
      And the selected placements exclude node "node-b"
      And the exclusion reason for "node-b" is "node-health-down"
      And the placement plan records the health snapshot used for the decision

    @REQ-DIST-003 @REQ-DIST-003-B @functional-requirement @non-functional-requirement @distributed-readiness @membership @health-model @placement @observability @implemented-not-e2e-validated
    Scenario: Placement uses a DEGRADED node only after healthy candidates are exhausted and records risk
      Given topology "phase-6-degraded-fallback-topology" contains nodes "node-a", "node-b", and "node-c"
      And node "node-a" is "HEALTHY" in failure domain "rack-1"
      And node "node-b" is "DEGRADED" in failure domain "rack-2" because disk set "disk-set-hot-b-01" reports status "HIGH_LATENCY"
      And node "node-c" is "DOWN" in failure domain "rack-3"
      When the storage engine plans a new write placement for bucket "distributed-bucket" key "datasets/2026-06/report.parquet" with replication factor 2
      Then the placement decision is "placement-plan-created-with-risk"
      And the selected placements include healthy node "node-a"
      And the selected placements include degraded node "node-b" only because no second healthy candidate is available
      And the selected placements exclude down node "node-c"
      And the placement plan records degraded placement risk "degraded-node-used-after-healthy-candidates-exhausted" for node "node-b"

    @REQ-DIST-003 @REQ-DIST-003-C @functional-requirement @non-functional-requirement @distributed-readiness @membership @health-model @placement @failure-handling @implemented-not-e2e-validated
    Scenario: Placement fails when all candidate nodes are DOWN
      Given topology "phase-6-all-down-topology" contains nodes "node-a", "node-b", and "node-c"
      And node "node-a" is "DOWN"
      And node "node-b" is "DOWN"
      And node "node-c" is "DOWN"
      When the storage engine plans a new write placement for bucket "distributed-bucket" key "datasets/2026-06/report.parquet" with replication factor 1
      Then the placement decision is "no-healthy-nodes"
      And no placement plan is published as ready for commit
      And the decision records all candidate nodes and their "DOWN" health state
      And readiness status for the topology remains "distributed-simulation-not-ready"

  Rule: The engine must detect missing or corrupt replicas and create an observable healing plan before claiming self-healing readiness
    Anti-entropy must compare expected manifest replicas with observed replica state. Missing and corrupt
    replicas must produce observable findings and healing tasks. Healing must not be marked complete when
    no verified source replica exists.

    @REQ-DIST-004 @REQ-DIST-004-A @functional-requirement @non-functional-requirement @distributed-readiness @anti-entropy @self-healing @observability @implemented-not-e2e-validated
    Scenario: Anti-entropy creates a healing task for a missing replica
      Given manifest "manifest-distributed-bucket-datasets-2026-06-report-parquet" for bucket "distributed-bucket" key "datasets/2026-06/report.parquet" expects replicas on "node-a", "node-b", and "node-c"
      And node "node-a" has a verified replica with checksum "sha256:4c81f0bfe0c4f7c9d8f971a5de89b372176f2cdd1f6e8ee4b316f8f94b35f987"
      And node "node-b" is missing its replica under storage root "/var/lib/magrathea/distributed/node-b/objects"
      And node "node-c" has a verified replica with checksum "sha256:4c81f0bfe0c4f7c9d8f971a5de89b372176f2cdd1f6e8ee4b316f8f94b35f987"
      When anti-entropy scans the manifest and replica observations
      Then it records finding "missing-replica" for node "node-b"
      And it creates healing task "heal-manifest-distributed-bucket-node-b" targeting node "node-b"
      And the healing task selects a healthy source from "node-a" or "node-c"
      And the healing task action is "copy-verified-replica"
      And self-healing readiness remains "planned-not-executed" until a real healing runner completes the copy and verifies the checksum

    @REQ-DIST-004 @REQ-DIST-004-B @functional-requirement @non-functional-requirement @distributed-readiness @anti-entropy @self-healing @integrity @observability @implemented-not-e2e-validated
    Scenario: Anti-entropy excludes a corrupt replica as a source and schedules replacement from a verified replica
      Given manifest "manifest-distributed-bucket-datasets-2026-06-report-parquet" for bucket "distributed-bucket" key "datasets/2026-06/report.parquet" expects checksum "sha256:4c81f0bfe0c4f7c9d8f971a5de89b372176f2cdd1f6e8ee4b316f8f94b35f987"
      And node "node-a" has a verified replica with the expected checksum
      And node "node-b" has a verified replica with the expected checksum
      And node "node-c" has checksum "sha256:1111111111111111111111111111111111111111111111111111111111111111" that does not match the manifest
      When anti-entropy scans the manifest and replica observations
      Then it records finding "corrupt-replica" for node "node-c"
      And it marks the replica on "node-c" as ineligible as a healing source
      And it creates a replacement task targeting node "node-c"
      And the replacement task selects a verified source from "node-a" or "node-b"
      And the observable healing plan includes checksum verification before replacement completion can be reported

    @REQ-DIST-004 @REQ-DIST-004-C @functional-requirement @non-functional-requirement @distributed-readiness @anti-entropy @self-healing @integrity @failure-handling @implemented-not-e2e-validated
    Scenario: Anti-entropy reports unrecoverable state when no verified source replica exists
      Given manifest "manifest-distributed-bucket-datasets-2026-06-report-parquet" for bucket "distributed-bucket" key "datasets/2026-06/report.parquet" expects replicas on "node-a", "node-b", and "node-c"
      And node "node-a" is missing its replica
      And node "node-b" has checksum "sha256:2222222222222222222222222222222222222222222222222222222222222222" that does not match the manifest
      And node "node-c" is unavailable for verification
      When anti-entropy scans the manifest and replica observations
      Then it records finding "unrecoverable-no-verified-source"
      And it does not create a copy healing task
      And it does not mark healing complete for the manifest
      And it raises an observable integrity alert for bucket "distributed-bucket" key "datasets/2026-06/report.parquet"
      And the readiness status for self-healing remains "not-implemented" or "simulation-unrecoverable"

  Rule: Rebalancing must be explicit, observable, and safe
    Rebalancing must propose moves as a plan before execution, preserve the configured quorum for every
    affected object, and keep original committed replicas when a copy task fails.

    @REQ-DIST-005 @REQ-DIST-005-A @functional-requirement @non-functional-requirement @distributed-readiness @rebalancing @membership @quorum @observability @implemented-not-e2e-validated
    Scenario: Rebalancing plans safe replica moves when a new healthy node joins a new rack
      Given topology "phase-6-rebalance-topology" has existing healthy nodes "node-a", "node-b", and "node-c" in racks "rack-1", "rack-2", and "rack-3"
      And new node "node-d" joins failure domain "rack-4" with disk set "disk-set-hot-d-01" and storage root "/var/lib/magrathea/distributed/node-d/objects"
      And node "node-d" reports health "HEALTHY"
      And object manifest "manifest-distributed-bucket-datasets-2026-06-report-parquet" has replication factor 3 and write quorum 2
      When rebalancing is planned for bucket "distributed-bucket" key "datasets/2026-06/report.parquet"
      Then the rebalance decision is "rebalance-plan-created"
      And the plan proposes at least one replica move or additional copy involving node "node-d"
      And every proposed move keeps at least 2 committed verified replicas available during the move
      And the plan records source node, target node, source failure domain, target failure domain, and manifest id for each move
      And the plan is observable without claiming that data has already been moved

    @REQ-DIST-005 @REQ-DIST-005-B @functional-requirement @non-functional-requirement @distributed-readiness @rebalancing @failure-handling @quorum @observability @implemented-not-e2e-validated
    Scenario: Failed rebalance copy keeps original replicas committed and records retry eligibility
      Given rebalance task "rebalance-manifest-distributed-bucket-node-d" copies a verified replica to node "node-d"
      And original committed replicas remain on "node-a", "node-b", and "node-c"
      And the copy to storage root "/var/lib/magrathea/distributed/node-d/objects" fails with reason "target-write-timeout"
      When the rebalance task result is evaluated
      Then the task status is "FAILED"
      And the task records retry eligibility "RETRYABLE"
      And the task records failure reason "target-write-timeout"
      And original replicas on "node-a", "node-b", and "node-c" remain committed for manifest "manifest-distributed-bucket-datasets-2026-06-report-parquet"
      And the object remains above configured write quorum 2 after the failed rebalance task

  Rule: Documentation and status reporting must not claim distributed-production readiness until multi-node behavior is validated
    Status reports, test reports, and ARC42 documentation must distinguish single-node operation,
    simulated distributed planning, implemented-but-not-e2e-validated behavior, and real distributed
    production readiness. Completion must not be inferred from route inventories, placeholders, or modeled plans.

    @REQ-DIST-006 @REQ-DIST-006-A @functional-requirement @non-functional-requirement @distributed-readiness @observability @status-reporting @single-node @implemented-not-e2e-validated
    Scenario: Single-process filesystem backend reports single-node or distributed simulation readiness, not production readiness
      Given the default filesystem backend runs in one application process
      And object bytes and manifests are stored under local directory "target/storage-engine-it/phase-6-single-node"
      And no networked membership service is configured
      And storage profile property "magrathea.storage.backend" is set to "filesystem"
      When the admin-only readiness status report is generated for the storage engine
      Then the distributed readiness classification is "single-node" or "distributed-simulation"
      And the report does not contain classification "distributed-production-ready"
      And the report states that S3 object behavior is still exposed only through the S3-compatible API
      And the report lists missing capabilities including networked membership, real replication job execution, and multi-node end-to-end validation

    @REQ-DIST-006 @REQ-DIST-006-B @functional-requirement @non-functional-requirement @distributed-readiness @observability @status-reporting @arc42 @failure-handling @implemented-not-e2e-validated
    Scenario: ARC42 and test reports do not mark distributed readiness complete without real multi-node evidence
      Given no real networked membership implementation has passed validation
      And no real replication job runner has completed a verified multi-node write and read cycle
      And no full end-to-end multi-node test has passed for bucket "distributed-bucket" key "datasets/2026-06/report.parquet"
      When the ARC42 appendix and consolidated test report summarize Phase 6 distributed readiness
      Then the reported status for requirements "REQ-DIST-001" through "REQ-DIST-006" is "@not-implemented" unless production code and semantic tests later provide evidence for a narrower status
      And any future status "@implemented-not-e2e-validated" is allowed only for behavior implemented in production code without full multi-node end-to-end evidence
      And the reports do not mark distributed readiness as complete or "@implemented-and-validated"
      And the reports distinguish modeled planner behavior from distributed-production readiness
