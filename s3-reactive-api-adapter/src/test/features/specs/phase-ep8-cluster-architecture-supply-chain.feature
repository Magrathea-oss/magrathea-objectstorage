@spec @phase-ep8 @architecture @supply-chain @security
Ability: Gate cluster evolution with an authoritative architecture and reproducible supply-chain evidence
  Maintainers need an accepted, executable cluster architecture contract and verifiable supply-chain evidence
  while networked cluster implementation evolves, so that EP-10 cannot invent weaker consistency,
  public inter-node APIs, unverifiable artifacts, or privileged runtime assumptions.

  EP-8 completed only the architecture decision and supply-chain gates described here. The bounded EP-10
  first slice now implements and validates fixed A/B/C CreateBucket plus unconditional single-part whole-object
  PUT/GET, direct N=3/W=2 replication without degraded writes, one-coordinator failover, and complete restart
  under REQ-CLUSTER-001..005 and REQ-CLUSTER-008..013. ADR 0027 remains "Accepted — architectural
  decision only", and REQ-HA-001..008 remain implemented-and-validated architecture contracts rather than
  runtime requirements. The bounded slice realizes only part of their Ratis control-plane and direct-data-path
  intent. Overall EP-10 remains partial: dynamic membership, erasure coding, healing and rebalance execution,
  and comprehensive partition and fault suites remain not implemented. Passing these EP-8 architecture-contract
  scenarios remains evidence of architecture intent, not duplicate validation of either the bounded runtime
  slice or the later EP-10 backlog.

  The cluster protocol is internal infrastructure behind the object-store and storage-engine boundaries.
  S3-compatible endpoints remain the only external object and bucket API. The Admin API may expose
  operational topology or status with no S3 equivalent, but neither Admin nor cluster transport may become
  an alternate object API.

  Shared supply-chain evidence identity is derived and recorded for each evidence run:
    | identity field      | authoritative recorded value |
    | source revision     | full Git revision of the current clean checked-out HEAD |
    | application version | exact root Maven project version, including every qualifier such as -SNAPSHOT |
    | publication status  | explicit evidence-run classification; a development evidence run is not a published release |
    | source date epoch   | deterministic SOURCE_DATE_EPOCH associated with that revision |
    | evidence timestamp  | UTC timestamp derived exactly from SOURCE_DATE_EPOCH |
    | image identity      | exact immutable local image ID built and validated for that revision and application version |

  The current root Maven project version is "0.1.0-SNAPSHOT". A future release workflow may supply its
  exact root Maven release version, but no evidence producer may remove, replace, or otherwise rewrite a
  version qualifier or claim that a non-published development evidence run was released.

  Rule: ADR 0027 remains architectural authority while ADR 0028 records bounded current execution
    ADR 0028 records only the fixed A/B/C runtime slice validated by REQ-CLUSTER-001..005 and REQ-CLUSTER-008..013.
    It neither replaces ADR 0027 as target architecture authority nor turns this architecture-contract evidence into runtime evidence.

    @REQ-HA-001 @non-functional-requirement @architecture @architecture-decision @internal-api @security @ep10-runtime-partial @implemented-and-validated
    Scenario: ADR 0027 remains authority while ADR 0028 supplies bounded REQ-CLUSTER-001..005 and REQ-CLUSTER-008..013 execution evidence
      Given the canonical decision is "docs/adr/0027-authoritative-cluster-control-plane-and-direct-quorum-data-path.md"
      When the architecture-contract runner evaluates ADR 0027
      Then its status is "Accepted — architectural decision only"
      And it selects internal protobuf gRPC over HTTP/2 with mutual TLS and bounded Reactor bridges
      And it selects consensus-committed membership and metadata with direct checksum-validated data transfer outside the consensus log
      And it decides static seeds, stable node identity, membership authority, topology hierarchy, ordered write and read publication, fencing, and failure semantics
      And it defines CycloneDX, SPDX-normalized license, OWASP, and hardened-runtime evidence gates
      And it compares RSocket, plain HTTP/2, gossip authority, static membership authority, quorum-only metadata, external consensus, data in Raft, and degraded writes
      And it identifies EP-10 as the owner of networked execution and multi-node fault validation
      And architecture-contract scenarios are reported separately from runtime and artifact evidence

  Rule: Production bootstrap composition preserves the private cluster boundary
    The bootstrap application may depend on cluster-protocol, storage-engine-cluster-application,
    cluster-control-ratis-infrastructure, and cluster-data-grpc-infrastructure as production composition.
    Those internal dependencies do not become supported client endpoints or alternate object APIs.

    @REQ-HA-002 @functional-requirement @non-functional-requirement @architecture @boundary @internal-api @s3-api @security @ep10-runtime-partial @implemented-and-validated
    Scenario: Production bootstrap composes internal cluster infrastructure without creating a public object or bucket facade
      Given ADR 0027 defines the planned gRPC surface for membership, control coordination, artifact transfer, verification, health evidence, and durable recovery-job execution
      When the architecture boundary is compared with the S3 Data Plane and Admin Control Plane
      Then external create, list, read, write, delete, tagging, versioning, ACL, metadata, multipart, bucket, and object operations remain available only through S3-compatible endpoints
      And an inter-node request cannot bypass S3 authentication, authorization, or the object-store to storage-engine application boundary
      And Admin access remains limited to topology, configuration, backend status, and operational evidence that has no S3 API equivalent
      And the inter-node listener is classified as internal-only and mutual-TLS authenticated rather than advertised as a supported client endpoint

  Rule: ADR 0027 remains ordered-publication authority while ADR 0028 records bounded current execution

    @REQ-HA-003 @functional-requirement @non-functional-requirement @architecture @consistency @write-ordering @determinism @integrity @durability @ep10-runtime-partial @implemented-and-validated
    Scenario: A replicated write contract has one deterministic place-stream-verify-publish-response order
      Given the initial replicated policy is "N=3, W=2" with degraded writes disabled
      And a coordinator has resolved a consensus-committed membership snapshot, topology epoch "topology-42", policy epoch "policy-17", and current object generation "generation-8"
      When the architecture-contract runner evaluates publication of operation "put-photos-2026-001" for bucket "archive-2026" and key "photos/launch-day/original.tiff"
      Then the required stages have exactly this order:
        | order | required stage |
        | 1     | reject or redirect a stale or fenced coordinator |
        | 2     | deterministically place the generation on the exact PA-6-selected targets |
        | 3     | stream immutable bytes directly to those targets without routing payload through the consensus log |
        | 4     | validate length and SHA-256 before each target durably publishes its local immutable artifact |
        | 5     | collect at least 2 idempotent durable acknowledgements bound to node UUID, artifact ID, checksum, and epochs |
        | 6     | revalidate fencing and consensus-commit one object-reference or manifest generation naming only verified artifacts |
        | 7     | return the successful S3 response only after that consensus publication commits |
      And stable operation and artifact identifiers make a retried stage idempotent

    @REQ-HA-004 @functional-requirement @non-functional-requirement @architecture @consistency @failure-handling @no-degraded-writes @integrity @ep10-runtime-partial @implemented-and-validated
    Scenario Outline: The write-failure architecture remains broader than bounded EP-10 failure validation
      Given replicated write operation "put-photos-2026-002" requires policy "N=3, W=2"
      And the planned failure is "<failure>"
      When the architecture-contract runner evaluates its publication outcome
      Then the outcome is "write-failed-not-published"
      And no successful S3 response is permitted
      And no object-reference or manifest generation is visible for the failed operation
      And any durable staged artifacts remain unreachable orphans eligible only for durable fenced cleanup
      And the architecture never lowers target diversity, write quorum, checksum validation, or consensus publication requirements as an implicit fallback

      Examples:
        | failure |
        | committed topology cannot select 3 independent targets |
        | only 1 checksum-valid durable acknowledgement is received |
        | a stream is cancelled or exceeds its deadline |
        | a target reports a checksum or length mismatch |
        | the coordinator uses a stale topology or policy epoch |
        | control-plane quorum is unavailable before reference publication |
        | consensus reference publication fails after 2 durable acknowledgements |

  Rule: Identity, bootstrap, membership, and suspicion have separate authorities

    @REQ-HA-005 @functional-requirement @non-functional-requirement @architecture @node-identity @membership @consensus @security @failure-handling @ep10-runtime-partial @implemented-and-validated
    Scenario: Dynamic stable-identity membership remains beyond the fixed A B C bootstrap
      Given storage node "node-7f4c" has persisted UUID "3f32679d-f51e-46ce-a720-1f5d927c78d2"
      And static seed addresses "10.42.0.11:9443,10.42.0.12:9443,10.42.0.13:9443" are bootstrap hints
      When the planned node changes address and rotates its mutual-TLS certificate
      Then its persisted UUID remains its storage identity
      And hostname, address, process ID, certificate serial, and seed-list position are not identity
      And adding or removing a seed does not add, remove, promote, demote, or replace a member
      And admission, promotion, demotion, replacement, removal, incarnation, and fencing become authoritative only through a committed consensus transition
      And a second live node presenting UUID "3f32679d-f51e-46ce-a720-1f5d927c78d2" is rejected or fenced

    @REQ-HA-006 @functional-requirement @non-functional-requirement @architecture @membership @failure-suspicion @consensus @availability @ep10-runtime-partial @implemented-and-validated
    Scenario: Failure suspicion may affect placement eligibility but cannot evict a member
      Given node "node-7f4c" is a consensus-committed member
      And its heartbeats are missed until liveness state "SUSPECT" is reached
      When the planned placement service evaluates a new generation
      Then it may exclude "node-7f4c" from proposed new targets and record the suspicion evidence
      But missed heartbeats, RPC timeout, partition, or seed removal do not change authoritative membership
      And eviction or replacement requires an explicit safe consensus configuration transition
      And loss of control-plane quorum prevents that transition and all new object-reference publication
      And the cluster contract fails writes instead of allowing split-brain or degraded publication

  Rule: Cluster topology extends rather than contaminates PA-6 policy

    @REQ-HA-007 @functional-requirement @non-functional-requirement @architecture @topology @failure-domain @determinism @pa-6 @ep10-runtime-partial @implemented-and-validated
    Scenario: Parent-linked topology preserves deterministic PA-6 policy components
      Given the planned YAML topology catalog "config/storage/cluster-topology.yml" declares the hierarchy "zone → rack → host → disk-set → device"
      And host "host-a-01" binds node UUID "3f32679d-f51e-46ce-a720-1f5d927c78d2"
      And disk set "disk-set-hot-a-01" owns device "nvme-a-01" at storage root "/var/lib/magrathea/data/nvme-a-01"
      When an authoritative topology and policy snapshot is constructed for PA-6
      Then every identity has one valid parent and every device has one owner
      And duplicate identities, broken parent links, conflicting device ownership, and impossible policy constraints are rejected
      And topology and policy snapshots carry consensus-controlled epochs used to fence stale publication
      And "DistributedPlacementPlanner", "QuorumPolicy", "AntiEntropyPlanner", "RebalancePlanner", and "DistributedReadinessReporter" remain deterministic side-effect-free components in "storage-engine-domain"
      And no gRPC, protobuf, Ratis, clock, persistence, retry-loop, or network-client type enters that pure policy core
      And cluster application services adapt authoritative snapshots into PA-6 inputs and execute its decisions without replacing PA-6 semantics

  Rule: Application and image SBOMs are complete, traceable, and reproducible
    The expanded production inventory is the contract for a future acceptance-eligible clean evidence run.
    Historical EP-8 evidence does not claim SBOM coverage for the four newly composed EP-10 modules.

    @REQ-SUPPLY-001 @non-functional-requirement @security @supply-chain @sbom @cyclonedx @reproducibility @machine-readable @implemented-not-e2e-validated
    Scenario: CycloneDX JSON and XML describe the complete production application reactor
      Given the current checkout has a clean working tree
      And the evidence runner records the full Git revision of the checked-out HEAD
      And the root Maven project declares the exact application version "0.1.0-SNAPSHOT"
      And the evidence run is classified as non-published development evidence
      And SOURCE_DATE_EPOCH is deterministically associated with that revision and determines the recorded UTC evidence timestamp
      And the production reactor inventory is:
        | module |
        | admin-api-adapter |
        | s3-reactive-api-adapter |
        | object-store-domain |
        | storage-engine-domain |
        | cluster-protocol |
        | storage-engine-cluster-application |
        | cluster-control-ratis-infrastructure |
        | cluster-data-grpc-infrastructure |
        | storage-engine-reactive-repository-application |
        | storage-engine-reactive-application |
        | storage-engine-reactive-infrastructure |
        | bootstrap-application |
        | object-store-reactive-repository-application |
        | object-store-reactive-application |
        | object-store-reactive-infrastructure |
        | object-store-reactive-repository-storage-engine-infrastructure |
      When the canonical supply-chain evidence build generates "target/supply-chain/application.cdx.json" and "target/supply-chain/application.cdx.xml"
      Then both artifacts validate against the same supported CycloneDX specification version
      And both represent every production module and all resolved production dependencies without test-only or coverage-aggregator components
      And their metadata agrees on the recorded full source revision, exact root Maven project version including "-SNAPSHOT", SOURCE_DATE_EPOCH-derived timestamp, and application component
      And neither artifact rewrites "0.1.0-SNAPSHOT" as "0.1.0" or claims release publication
      And "target/supply-chain/evidence-manifest.json" records those exact identity values and the SHA-256 of each SBOM
      And components retain package coordinates, exact resolved versions, dependency relationships, and available cryptographic hashes
      And normalized JSON and XML inventories reconcile to the same component identities and dependency relationships
      And rebuilding from the same locked inputs and SOURCE_DATE_EPOCH produces identical normalized content and SHA-256 evidence

    @REQ-SUPPLY-001 @non-functional-requirement @security @supply-chain @fail-closed @reproducibility @implemented-and-validated
    Scenario: Dirty content cannot be labeled as acceptance evidence for the checked-out revision
      Given the current checkout contains a tracked or untracked working-tree change
      When the canonical supply-chain evidence build derives its source identity
      Then acceptance-evidence generation fails before producing application, image, or license artifacts
      And it does not label the dirty content with the full revision of the checked-out HEAD
      And it does not reuse identity values or artifacts from a prior clean evidence run

    @REQ-SUPPLY-002 @non-functional-requirement @security @supply-chain @sbom @cyclonedx @oci @image-digest @reproducibility @implemented-and-validated
    Scenario: OCI image SBOM is bound to the exact locally validated image ID
      Given the shared evidence identity records the current clean checkout's full Git revision, exact root Maven project version including any qualifier, SOURCE_DATE_EPOCH, and derived UTC timestamp
      And the local image reference for that recorded version resolves to the exact immutable local image ID produced by the evidence build
      When the evidence runner generates "target/supply-chain/image.cdx.json" by inspecting that recorded image ID rather than a mutable tag
      Then the SBOM identifies the subject image by that exact immutable image ID
      And its source revision, application version, and timestamp agree with the shared recorded evidence identity
      And it inventories application, JVM, operating-system package, and other runtime components discoverable in the final image
      And the evidence manifest records the same image ID, full source revision, application version, SOURCE_DATE_EPOCH, derived timestamp, and SBOM SHA-256
      And runtime hardening and smoke validation use the same immutable image ID
      And tag movement or a subject-image-ID mismatch fails the gate instead of silently regenerating evidence for another image
      And repeated inspection of the same image bytes produces identical normalized inventory and SHA-256 evidence

  Rule: License and vulnerability evidence remains truthful without phase-local remediation

    @REQ-SUPPLY-003 @non-functional-requirement @security @supply-chain @license-compliance @spdx @observability @reproducibility @implemented-and-validated
    Scenario: SPDX-normalized license inventory keeps unknown and ambiguous licenses visible
      Given the CycloneDX application inventory for the shared recorded full source revision and exact root Maven project version including any qualifier has passed reactor reconciliation
      And its timestamp is derived from the recorded SOURCE_DATE_EPOCH associated with that revision
      When license evidence is generated at "target/supply-chain/license-inventory.json" and "target/supply-chain/license-inventory.html"
      Then both license artifacts agree with the application JSON, application XML, image SBOM, and evidence manifest on the recorded source revision, application version, SOURCE_DATE_EPOCH, and derived timestamp
      And each production component retains package identity, exact version, source evidence, detected license, concluded SPDX expression, and review status
      And recognized licenses use valid SPDX license identifiers or expressions
      And missing evidence is reported as "NOASSERTION" with review status "unknown"
      And conflicting or non-normalizable evidence is retained with review status "ambiguous"
      And unknown, ambiguous, copyleft, exception-bearing, and manually concluded entries remain visible in machine-readable and human-readable reports
      And generation does not label the application or a release compliant merely because an inventory exists
      And no license identifier, conclusion, approval, or compatibility decision is fabricated from absent evidence
      And repeated normalization of the same component and source evidence produces the same ordered inventory and SHA-256

    @REQ-SUPPLY-004 @non-functional-requirement @security @supply-chain @owasp-dependency-check @fail-closed @observability @implemented-and-validated
    Scenario Outline: EP-8 preserves OWASP monitoring and fail-closed evidence without remediation or suppression
      Given OWASP Dependency-Check scans every resolved production reactor dependency
      And the configured gate rejects unsuppressed findings with CVSS 7.0 or greater
      And the pre-run dependency coordinates, plugin configuration, and suppression file content are captured
      When the scan ends with status "<scan_status>"
      Then verification handling is "<handling>"
      And machine-readable evidence distinguishes unsuppressed findings, suppressed findings, and scan errors
      And findings below the failure threshold remain visible
      And an incomplete scan is never reported as clean or as zero vulnerabilities
      And dependency coordinates, plugin configuration, and suppression file content are unchanged by this EP-8 evidence run
      And no dependency upgrade, downgrade, exclusion, generated suppression, vulnerability waiver, or finding deletion is performed to make the gate pass

      Examples:
        | scan_status                              | handling |
        | complete with no threshold finding       | pass while retaining all findings |
        | complete with unsuppressed CVSS 7.0+     | fail |
        | incomplete because vulnerability data is unavailable or stale | fail closed |

  Rule: The exact evidence image runs with least privilege without losing required behavior

    @REQ-SUPPLY-005 @functional-requirement @non-functional-requirement @security @supply-chain @container-hardening @least-privilege @durability @restart-safety @implemented-and-validated
    Scenario: Hardened JVM image preserves Admin health, S3 behavior, and object bytes across restart
      Given the hardened-runtime runner builds the current source revision into a local JVM image
      And the runner records the exact immutable local image ID produced for that revision
      And every inspection, start, and replacement uses that recorded image ID rather than resolving a mutable image tag
      And the container user and group are configured as numeric non-zero IDs
      And the root filesystem is read-only
      And only data mount "/var/lib/magrathea" and temporary runtime mount "/tmp/magrathea" are writable
      And privilege escalation is disabled with "no-new-privileges"
      And all Linux capabilities are dropped with no capabilities added
      And the container is configured without host PID, IPC, network, or UTS namespace flags and mounts no container engine socket
      And daemon-level user-namespace remapping is not required by this portable runtime baseline
      When the hardened-runtime runner starts the JVM application with the filesystem backend
      Then process inspection confirms the numeric non-root user and group and every declared hardening restriction
      And runtime evidence reports user-namespace remapping support and enabled state, or records it as unavailable, according to inspected engine capabilities and configuration, and never claims support or enablement without evidence
      And the Admin readiness representation identifies the filesystem backend as ready without exposing an Admin object API
      When an S3 client creates bucket "ep8-hardened-runtime", uploads fixture "s3-reactive-api-adapter/src/test/resources/fixtures/upload/corruptible-object.bin" as key "evidence/persistent/corruptible-object.bin", and reads it back
      Then returned bytes, content length 129, ETag, and SHA-256 "178ba39b2e4e92264f35dafbd416ba3c8beb0dc87b395415f068c181d837def0" agree with the fixture and persisted object
      When the container is replaced using the same recorded image ID and data mount
      Then the Admin readiness representation is healthy again
      And an S3 read returns the same object bytes, content length, ETag, and SHA-256
      And no write outside the two explicit writable mounts was required for startup, request handling, or restart recovery

  Rule: EP-8 evidence remains distinct from bounded EP-10 validation and the later cluster backlog

    @REQ-HA-008 @REQ-SUPPLY-006 @non-functional-requirement @architecture @supply-chain @status-reporting @observability @ep10-runtime-partial @implemented-and-validated
    Scenario: Architecture and supply-chain completion preserves partial EP-10 status
      Given ADR 0027 is accepted
      And every required evidence producer and policy handler has been exercised for one clean full source revision and one exact immutable image ID
      And the application SBOM, image SBOM, license, and hardened-runtime evidence gates have passed for that same revision and image
      And OWASP Dependency-Check either completed under the configured monitoring policy or produced current-revision evidence that fails closed because the assessment is incomplete
      When the requirements appendix, roadmap, and supply-chain evidence summarize EP-8
      Then they may report the validated EP-8 architecture wiring and evidence-contract scope as complete
      And an incomplete OWASP assessment keeps vulnerability status explicitly "unknown/error" and is never reported as clean, complete, or zero vulnerabilities
      And no EP-8 result claims that an image or application was published merely because development evidence passed
      And no EP-8 result claims implemented membership, consensus correctness, multi-node persistence, quorum transfer, healing, rebalance, partition safety, or distributed production readiness
      And architecture-contract scenarios are reported separately from runtime and artifact evidence
