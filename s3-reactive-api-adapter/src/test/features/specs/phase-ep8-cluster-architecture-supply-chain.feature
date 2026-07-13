@spec @phase-ep8 @architecture @supply-chain @security
Ability: Gate future cluster work with an authoritative architecture and reproducible supply-chain evidence
  Maintainers need an accepted, executable cluster architecture contract and verifiable release evidence
  before networked cluster implementation begins, so that EP-10 cannot invent weaker consistency,
  public inter-node APIs, unverifiable artifacts, or privileged runtime assumptions.

  EP-8 completes only the architecture decision and supply-chain gates described here. Cluster transport,
  membership execution, consensus, multi-node persistence, quorum transfer, healing, rebalance, and
  fault-injection evidence remain planned for EP-10. Passing an architecture-contract scenario is not
  evidence that the corresponding cluster behavior exists.

  The cluster protocol is internal infrastructure behind the object-store and storage-engine boundaries.
  S3-compatible endpoints remain the only external object and bucket API. The Admin API may expose
  operational topology or status with no S3 equivalent, but neither Admin nor cluster transport may become
  an alternate object API.

  Shared release evidence identity is derived and recorded for each evidence run:
    | identity field      | authoritative recorded value |
    | source revision     | full Git revision of the current clean checked-out HEAD |
    | application version | release version declared by the root Maven project |
    | source date epoch   | deterministic SOURCE_DATE_EPOCH associated with that revision |
    | evidence timestamp  | UTC timestamp derived exactly from SOURCE_DATE_EPOCH |
    | image identity      | exact immutable local image ID built and validated for that revision and application version |

  Rule: ADR 0027 is the complete authority for the planned cluster boundary

    @REQ-HA-001 @non-functional-requirement @architecture @architecture-decision @internal-api @security @partial
    Scenario: The accepted decision is complete without claiming a cluster implementation
      Given the canonical decision is "docs/adr/0027-authoritative-cluster-control-plane-and-direct-quorum-data-path.md"
      When the architecture-contract runner evaluates ADR 0027
      Then its status is "Accepted — architectural decision only"
      And it selects internal protobuf gRPC over HTTP/2 with mutual TLS and bounded Reactor bridges
      And it selects consensus-committed membership and metadata with direct checksum-validated data transfer outside the consensus log
      And it records embedded Apache Ratis as a planned implementation subject to a time-boxed integration and fault-behavior spike
      And it decides static seeds, stable node identity, membership authority, topology hierarchy, ordered write and read publication, fencing, and failure semantics
      And it defines CycloneDX, SPDX-normalized license, OWASP, and hardened-runtime evidence gates
      And it compares RSocket, plain HTTP/2, gossip authority, static membership authority, quorum-only metadata, external consensus, data in Raft, and degraded writes
      And it identifies EP-10 as the owner of networked execution and multi-node fault validation
      And no accepted wording reports Ratis, Raft correctness, membership, quorum transfer, healing, rebalance, or multi-node durability as implemented

    @REQ-HA-002 @functional-requirement @non-functional-requirement @architecture @boundary @internal-api @s3-api @security @partial
    Scenario: Inter-node transport cannot become a public object or bucket facade
      Given ADR 0027 defines the planned gRPC surface for membership, control coordination, artifact transfer, verification, health evidence, and durable recovery-job execution
      When the architecture boundary is compared with the S3 Data Plane and Admin Control Plane
      Then external create, list, read, write, delete, tagging, versioning, ACL, metadata, multipart, bucket, and object operations remain available only through S3-compatible endpoints
      And no cluster protobuf service or Admin route exposes those object or bucket operations
      And an inter-node request cannot bypass S3 authentication, authorization, or the object-store to storage-engine application boundary
      And Admin access remains limited to topology, configuration, backend status, and operational evidence that has no S3 API equivalent
      And the inter-node listener is classified as internal-only and mutual-TLS authenticated rather than advertised as a supported client endpoint

  Rule: Ordered publication is an architecture contract and not EP-10 execution evidence

    @REQ-HA-003 @functional-requirement @non-functional-requirement @architecture @consistency @write-ordering @determinism @integrity @durability @absent
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
      And the contract does not assert that any stage has a networked implementation in EP-8

    @REQ-HA-004 @functional-requirement @non-functional-requirement @architecture @consistency @failure-handling @no-degraded-writes @integrity @absent
    Scenario Outline: Write failure cannot degrade durability or expose an unpublished generation
      Given replicated write operation "put-photos-2026-002" requires policy "N=3, W=2"
      And the planned failure is "<failure>"
      When the architecture-contract runner evaluates its publication outcome
      Then the outcome is "write-failed-not-published"
      And no successful S3 response is permitted
      And no object-reference or manifest generation is visible for the failed operation
      And any durable staged artifacts remain unreachable orphans eligible only for durable fenced cleanup
      And the architecture never lowers target diversity, write quorum, checksum validation, or consensus publication requirements as an implicit fallback
      And this expected failure is a planned EP-10 contract rather than evidence of implemented fault handling

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

    @REQ-HA-005 @functional-requirement @non-functional-requirement @architecture @node-identity @membership @consensus @security @failure-handling @absent
    Scenario: Stable UUID identity survives endpoint and certificate changes while consensus controls membership
      Given storage node "node-7f4c" has persisted UUID "3f32679d-f51e-46ce-a720-1f5d927c78d2"
      And static seed addresses "10.42.0.11:9443,10.42.0.12:9443,10.42.0.13:9443" are bootstrap hints
      When the planned node changes address and rotates its mutual-TLS certificate
      Then its persisted UUID remains its storage identity
      And hostname, address, process ID, certificate serial, and seed-list position are not identity
      And adding or removing a seed does not add, remove, promote, demote, or replace a member
      And admission, promotion, demotion, replacement, removal, incarnation, and fencing become authoritative only through a committed consensus transition
      And a second live node presenting UUID "3f32679d-f51e-46ce-a720-1f5d927c78d2" is rejected or fenced
      And these transitions remain planned until EP-10 supplies networked consensus evidence

    @REQ-HA-006 @functional-requirement @non-functional-requirement @architecture @membership @failure-suspicion @consensus @availability @absent
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

    @REQ-HA-007 @functional-requirement @non-functional-requirement @architecture @topology @failure-domain @determinism @pa-6 @absent
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

    @REQ-SUPPLY-001 @non-functional-requirement @security @supply-chain @sbom @cyclonedx @reproducibility @machine-readable @absent
    Scenario: CycloneDX JSON and XML describe the complete production application reactor
      Given the current checkout has a clean working tree
      And the evidence runner records the full Git revision of the checked-out HEAD
      And the root Maven project declares the application release version
      And SOURCE_DATE_EPOCH is deterministically associated with that revision and determines the recorded UTC evidence timestamp
      And the production reactor inventory is:
        | module |
        | admin-api-adapter |
        | s3-reactive-api-adapter |
        | object-store-domain |
        | storage-engine-domain |
        | storage-engine-reactive-repository-application |
        | storage-engine-reactive-application |
        | storage-engine-reactive-infrastructure |
        | bootstrap-application |
        | object-store-reactive-repository-application |
        | object-store-reactive-application |
        | object-store-reactive-infrastructure |
        | object-store-reactive-repository-storage-engine-infrastructure |
      When the canonical release evidence build generates "target/supply-chain/application.cdx.json" and "target/supply-chain/application.cdx.xml"
      Then both artifacts validate against the same supported CycloneDX specification version
      And both represent every production module and all resolved production dependencies without test-only or coverage-aggregator components
      And their metadata agrees on the recorded full source revision, root Maven release version, SOURCE_DATE_EPOCH-derived timestamp, and released application component
      And "target/supply-chain/evidence-manifest.json" records those exact identity values and the SHA-256 of each SBOM
      And components retain package coordinates, exact resolved versions, dependency relationships, and available cryptographic hashes
      And normalized JSON and XML inventories reconcile to the same component identities and dependency relationships
      And rebuilding from the same locked inputs and SOURCE_DATE_EPOCH produces identical normalized content and SHA-256 evidence

    @REQ-SUPPLY-001 @non-functional-requirement @security @supply-chain @fail-closed @reproducibility @absent
    Scenario: Dirty content cannot be labeled as release evidence for the checked-out revision
      Given the current checkout contains a tracked or untracked working-tree change
      When the canonical release evidence build derives its source identity
      Then release-evidence generation fails before publishing application, image, or license artifacts
      And it does not label the dirty content with the full revision of the checked-out HEAD
      And it does not reuse identity values or artifacts from a prior clean evidence run

    @REQ-SUPPLY-002 @non-functional-requirement @security @supply-chain @sbom @cyclonedx @oci @image-digest @reproducibility @absent
    Scenario: OCI image SBOM is bound to the exact locally validated image ID
      Given the shared evidence identity records the current clean checkout's full Git revision, root Maven release version, SOURCE_DATE_EPOCH, and derived UTC timestamp
      And the local image reference for that recorded version resolves to the exact immutable local image ID produced by the release build
      When the evidence runner generates "target/supply-chain/image.cdx.json" by inspecting that recorded image ID rather than a mutable tag
      Then the SBOM identifies the subject image by that exact immutable image ID
      And its source revision, application version, and timestamp agree with the shared recorded evidence identity
      And it inventories application, JVM, operating-system package, and other runtime components discoverable in the final image
      And the evidence manifest records the same image ID, full source revision, application version, SOURCE_DATE_EPOCH, derived timestamp, and SBOM SHA-256
      And runtime hardening and smoke validation use the same immutable image ID
      And tag movement or a subject-image-ID mismatch fails the gate instead of silently regenerating evidence for another image
      And repeated inspection of the same image bytes produces identical normalized inventory and SHA-256 evidence

  Rule: License and vulnerability evidence remains truthful without phase-local remediation

    @REQ-SUPPLY-003 @non-functional-requirement @security @supply-chain @license-compliance @spdx @observability @reproducibility @absent
    Scenario: SPDX-normalized license inventory keeps unknown and ambiguous licenses visible
      Given the CycloneDX application inventory for the shared recorded full source revision and root Maven release version has passed reactor reconciliation
      And its timestamp is derived from the recorded SOURCE_DATE_EPOCH associated with that revision
      When license evidence is generated at "target/supply-chain/license-inventory.json" and "target/supply-chain/license-inventory.html"
      Then both license artifacts agree with the application JSON, application XML, image SBOM, and evidence manifest on the recorded source revision, application version, SOURCE_DATE_EPOCH, and derived timestamp
      And each production component retains package identity, exact version, source evidence, detected license, concluded SPDX expression, and review status
      And recognized licenses use valid SPDX license identifiers or expressions
      And missing evidence is reported as "NOASSERTION" with review status "unknown"
      And conflicting or non-normalizable evidence is retained with review status "ambiguous"
      And unknown, ambiguous, copyleft, exception-bearing, and manually concluded entries remain visible in machine-readable and human-readable reports
      And generation does not label the release compliant merely because an inventory exists
      And no license identifier, conclusion, approval, or compatibility decision is fabricated from absent evidence
      And repeated normalization of the same component and source evidence produces the same ordered inventory and SHA-256

    @REQ-SUPPLY-004 @non-functional-requirement @security @supply-chain @owasp-dependency-check @fail-closed @observability @partial
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

  Rule: The exact release image runs with least privilege without losing required behavior

    @REQ-SUPPLY-005 @functional-requirement @non-functional-requirement @security @supply-chain @container-hardening @least-privilege @durability @restart-safety @absent
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

  Rule: EP-8 evidence cannot upgrade EP-10 cluster status

    @REQ-HA-008 @REQ-SUPPLY-006 @non-functional-requirement @architecture @supply-chain @status-reporting @observability @partial
    Scenario: Architecture and supply-chain completion remains distinct from cluster execution
      Given ADR 0027 is accepted
      And all required application SBOM, image SBOM, license, OWASP, and hardened-runtime evidence gates have passed for one immutable revision and image digest
      When the requirements appendix, roadmap, and release evidence summarize EP-8
      Then they may report the validated EP-8 architecture and supply-chain gate scope as complete
      But EP-10 and networked cluster execution remain "@absent" until their own shared semantic multi-node and fault-injection scenarios pass
      And no EP-8 result claims implemented membership, consensus correctness, multi-node persistence, quorum transfer, healing, rebalance, partition safety, or distributed production readiness
      And architecture-contract scenarios are reported separately from runtime and artifact evidence
