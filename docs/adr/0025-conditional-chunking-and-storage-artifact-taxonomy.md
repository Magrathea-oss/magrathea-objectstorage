# ADR 0025: Conditional chunking and storage-artifact taxonomy

- Status: Accepted
- Date: 2026-07-11
- Decision owner: Product owner

## Context

Forced fixed-window multi-chunk persistence for ordinary non-dedup uploads was introduced historically by `REQ-UPLOAD-003` and intentionally removed in commit `696e147` because it was unnecessary. This ADR preserves that removal and must never be used to reintroduce it.

The pipeline correctly keeps a non-dedup upload as one streamed `FileUnit`. Schema version 2 represents its manifest entry with `StorageArtifactReferenceDescriptor` and `StorageArtifactKind.WHOLE_OBJECT`; schema 0/1 chunk entries are read as `LEGACY_CHUNK`. Plain units now persist under `nodes/<node>/whole-objects`, while dedup and EC artifacts use the segmented-artifact namespace. The remaining `ChunkId` Java value-object name is compatibility debt, not forced upload splitting.

Chunking has a cost and a semantic purpose; it must not become the universal representation for all objects.

## Decision

Chunks are created only by these mechanisms:

1. multipart upload parts;
2. deduplication windows;
3. erasure-coding stripes and data/parity shards.

A single-object upload with multipart, deduplication, and erasure coding all disabled remains one streamed whole-object storage unit. It is persisted under a whole-object storage namespace and must not create generic chunk identifiers, chunk references, dedup-index entries, multipart parts, or EC shards.

The durable artifact taxonomy is:

- `WHOLE_OBJECT`: one streamed unit for a plain object;
- `MULTIPART_PART`: an uncommitted or committed multipart part;
- `DEDUP_CHUNK`: a fingerprinted deduplication window that may have multiple owners;
- `EC_STRIPE`: an EC input stripe when represented durably;
- `EC_DATA_SHARD`: a policy-derived data shard;
- `EC_PARITY_SHARD`: a policy-derived parity shard.

Manifests must describe storage artifacts rather than assuming every entry is a chunk. Reference accounting and reclamation rules are artifact-specific:

- whole-object units have object ownership and are reclaimed as units;
- multipart parts belong to upload lifecycle until completion/abort/expiry;
- dedup chunks use scoped ownership/reference accounting;
- EC shards are reclaimed as the shard set owned by the final object reference.

## Consequences

- `FileSystemStorePort` must use its whole-object root for `FileUnit`; it must not route `FileUnit` through the chunk directory.
- The manifest model and filesystem serialization use schema version `2` with `artifactCount`, `artifact.<n>.kind`, and typed artifact entries; new files contain no `chunk.*` properties.
- Existing schema versions `0` and `1` remain readable as `LEGACY_CHUNK` entries during migration.
- `FileUnit` persistence and typed reads use the whole-object namespace without body aggregation, splitting, or duplicated payload storage.
- EC-enabled policies produce bounded four-MiB stripes and physical typed data/parity shards; their normal read path reconstructs exact bytes from ordered data shards while validating every shard checksum.
- Renaming the shared physical identifier type and adding degraded-read shard reconstruction remain follow-up compatibility/resilience work, not evidence gaps for physical persistence.
- S3 behavior remains exposed only through S3-compatible routes. The artifact taxonomy is an internal storage-engine concern and may be exposed only in Admin status/recovery/GC reports.
- EP-3 cannot close while a plain upload is represented as a generic chunk.
- EP-4 GC, dedup reference counting, quotas, ENOSPC handling, and scrubbing must follow this taxonomy.
- Existing multipart and EC plans do not count as physical implementation unless executable tests observe their artifacts and exact readback.

## Validation

The source-of-truth executable requirements are:

- `REQ-PIPELINE-014`: plain uploads remain whole-object units;
- `REQ-PIPELINE-015`: EC policies own stripe/shard segmentation;
- `REQ-GC-001..004`: type-aware reclamation;
- `REQ-SCRUB-001`: type-aware integrity scrubbing;
- `REQ-QUOTA-001/002` and `REQ-CAPACITY-001`: atomic capacity protection.

REQ-PIPELINE-014 and REQ-PIPELINE-015 have pipeline-unit and WebTestClient evidence for the whole-object namespace and physical EC shard persistence. The GetObject decision is single-pass payload streaming after metadata-only validation: upload commit verifies temporary-file bytes against the incoming digest before atomic publication, clients validate later reads against committed checksum/ETag metadata, and periodic at-rest detection/repair belongs to EP-4 scrubbing. The EP-4 lifecycle requirements remain pending.
