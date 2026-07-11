# ADR 0025: Conditional chunking and storage-artifact taxonomy

- Status: Accepted
- Date: 2026-07-11
- Decision owner: Product owner

## Context

The current storage pipeline names every persisted `FileUnit` as a chunk-compatible UUID and represents every object manifest entry with `ChunkReferenceDescriptor`, even when multipart upload, deduplication, and erasure coding are disabled. This makes a plain streamed object look like a one-chunk object and leaks that accidental representation into recovery, integrity reporting, future garbage collection, quotas, and Admin terminology.

That model is not the intended storage architecture. Chunking has a cost and a semantic purpose; it must not become the universal representation for all objects.

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
- The manifest model and filesystem serialization require a versioned migration from chunk-only entries to typed storage-artifact entries.
- Existing schema version `1` remains readable during migration. A new schema version must write explicit artifact kinds and paths.
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

Until production code and the declared validation modes pass, these requirements remain explicitly `@not-implemented`.
