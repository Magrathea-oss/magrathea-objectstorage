# ADR 0014 — Storage Engine Bounded Context

## Context

The Object Store S3 API domain (`object-store-domain`) is a pure model of S3 concepts: buckets, objects, multipart uploads, ACLs, tagging, encryption, and legal hold. Its repository interfaces (`object-store-reactive-repository-application`) abstract persistence behind command/query ports. The initial infrastructure module (`object-store-reactive-infrastructure`) provides in-memory implementations suitable for development and single-node deployments.

Production deployments require a persistent, scalable storage backend. The Storage Engine bounded context was created as a separate domain model for object persistence, independent of the S3 API domain. It defines storage-specific concepts: chunk-level pipelines, content-addressed deduplication, erasure coding, replication, device topology, and chaos engineering.

## Decision

### 1. Module Layout — Four New Modules

Three Storage Engine modules plus one Anti-Corruption Layer/adapter module. Current module names use reactive naming parity for the application and infrastructure modules:

| Module | Purpose |
|---|---|
| `storage-engine-domain` | Storage Engine domain model: policy, workflow, device, trace, manifest — zero framework dependencies |
| `storage-engine-reactive-application` | Storage Engine reactive orchestration: ports, Chunker, ReactiveStorageOrchestrator |
| `storage-engine-reactive-infrastructure` | Storage Engine filesystem cluster backend: FileSystemStorageCluster, content-address index, manifest repository, chaos decorator |
| `object-store-reactive-repository-storage-engine-infrastructure` | Anti-Corruption Layer + adapter: translates Object Store repository commands into Storage Engine commands |

### 2. Two-Backend Repository Strategy

Two concrete implementations of the Object Store repository interfaces coexist:

| Profile | Backend | Module |
|---|---|---|
| `single-node` (default) | In-memory repositories | `object-store-reactive-infrastructure` |
| `storage-engine` | Storage Engine filesystem cluster | `object-store-reactive-repository-storage-engine-infrastructure` |

The Storage Engine backend is selected explicitly through the documented runtime profile/property path. The ACL/adapter module is the only module that depends on both bounded contexts; all other modules remain decoupled. Runtime read/write completeness remains gated by later integration tests.

### 3. Anti-Corruption Layer Design

`object-store-reactive-repository-storage-engine-infrastructure` is the sole integration point between
the two bounded contexts. It implements the Object Store repository interfaces by:

- Translating Object Store commands (e.g., `CreateBucketCommand`, `PutObjectCommand`) into
  Storage Engine domain commands (e.g., `RegisterDeviceCommand`, `CompleteUpload`)
- Mapping S3 domain aggregates to Storage Engine concepts
- Delegating persistence to the Storage Engine bounded context via its application ports

This layer prevents Storage Engine concepts from leaking into the Object Store domain and vice versa.

### 4. Fixed 6-Step Pipeline

The Storage Engine defines a fixed pipeline for every chunk write. The order is immutable:

```
DEDUP → COMPRESS → CRYPT → ERASURE_CODING → REPLICATION → STORE
```

Each step is modeled as a `StepPlan` entry with `StepExecutionTrace` recording the actual outcome:

| Outcome | Meaning |
|---|---|
| `EXECUTED` | Step ran and completed normally |
| `SKIPPED` | Step was skipped due to policy or device capability |
| `BYPASSED` | Step was bypassed (e.g., by chaos injection) |

### 5. VirtualDevice Sealed Hierarchy

`VirtualDevice` is a sealed type representing a logical storage device:

- **BucketDevice** — non-dedup device; content is stored as-is per bucket
- **DedupDevice** — content-addressed device; deduplication is enabled

The sealed hierarchy prevents extension outside the domain and ensures all device types
are handled exhaustively.

### 6. DedupNamespace

`DedupNamespace` controls the scope of content-addressed deduplication:

- **GlobalDedupNamespace** — deduplication across all buckets
- **BucketDedupNamespace** — deduplication within a single bucket

The namespace is resolved from the effective storage policy at upload time.

### 7. Device Identity — Two Deterministic Keys

`VirtualDevice` identity is determined by two hashed keys:

- **WorkflowCompatibilityKey** — derived from pipeline configuration (which steps are active).
  Two devices with the same workflow key produce identical chunk-level behavior.
- **DeviceConfigurationHash** — derived from all device properties including the workflow key.
  Used for content-address index lookups.

The erasure coding (EC) step contributes to the device configuration hash. Replication does not,
because replication is a storage-layer concern that does not change the content-addressed identity.

### 8. CompleteUpload — Common Upload Phase

`CompleteUpload` is a domain concept shared by both PutObject and MultipartUpload. It represents
the point at which the upload is finalized at the object level before the chunk-level pipeline runs.
This phase:

- Validates the upload context (bucket, key, policy)
- Resolves the effective storage policy
- Selects or creates the target `VirtualDevice`
- Emits the `CompleteUploadEvent` that triggers the chunk pipeline

### 9. Chaos Engineering

A `FaultInjectingStorageCluster` decorator wraps the real `FileSystemStorageCluster`. It introduces
an **alter** step that executes after checksum computation and before verify. The alter step can:

- Corrupt a byte at a random position
- Skip the write (simulate write failure)
- Introduce latency
- Bypass the verify step

The alter step is controlled by a chaos probability parameter, enabling reproducible fault injection
in tests and non-deterministic injection in production-like environments.

The pipeline position for chaos is: DEDUP → COMPRESS → CRYPT → ERASURE_CODING → REPLICATION → ALTER → STORE.
Alter happens after checksum (so the checksum is of the unaltered data) and before verify (so verify
detects the alteration).

## Consequences

- **Positive**: Domain purity is maintained — the S3 API domain has no dependency on storage-engine concepts.
  The ACL/adapter module is the only bridge between the two bounded contexts.
- **Positive**: Two-backend strategy enables development with in-memory repositories while production
  deployments use the Storage Engine backend. Profile selection is straightforward.
- **Positive**: The fixed pipeline order prevents accidental reordering and ensures consistent
  chunk processing across all writes. EC and replication roles are clearly separated.
- **Positive**: VirtualDevice sealed hierarchy makes device types explicit and exhaustive.
  DedupNamespace provides flexible deduplication scope.
- **Positive**: Device identity via WorkflowCompatibilityKey and DeviceConfigurationHash is
  deterministic and composable. EC inclusion in the hash ensures that devices with different
  EC configurations are distinct.
- **Positive**: Chaos engineering is built into the infrastructure decorator, not scattered across
  the domain or application layers. The alter-after-checksum-before-verify position ensures
  checksums validate the pre-alter data.
- **Positive**: CompleteUpload as a common phase unifies PutObject and MultipartUpload at the
  object level before chunk-level processing.
- **Negative**: The ACL/adapter module creates a dependency from the Object Store infrastructure
  to the Storage Engine bounded context. If the Storage Engine domain changes, the adapter must
  be updated.
- **Risk**: The Storage Engine bounded context is complex (6-step pipeline, device topology,
  content-address index, manifest persistence). Its correctness depends on rigorous testing
  and integration verification.

## Status

Accepted. Updated 2026-06-12 to reflect the `storage-engine-reactive-application` / `storage-engine-reactive-infrastructure` module rename and the current verification boundary.

## Date

2026-05-30
