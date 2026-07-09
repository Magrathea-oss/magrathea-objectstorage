# ADR 0022: Complete S3 Solution Roadmap

Date: 2026-07-09

## Status

Proposed

## Context

Magrathea currently has a validated single-node S3-compatible subset with
semantic tests for ETags, range reads, conditional reads, object tagging, selected
multipart behavior, single-node bucket/object operations, storage-engine
filesystem reliability, and modeled distributed-readiness internals. This is not
yet a complete S3 solution.

The remaining work must stay evidence-driven: route inventory, generated API
lists, config storage, and status-code smoke checks do not prove semantic S3
compatibility. Every new claim needs production behavior plus semantic tests in
the agreed validation modes.

## Decision

Complete S3 compatibility will be sequenced by blocker risk and dependency order:

1. **EP-1 Security and identity first**: SigV4 authentication, credential store,
   deny-by-default authorization, bucket policy/ACL/PublicAccessBlock
   enforcement, audit events, and real server-side encryption.
2. **EP-3 Streaming and multipart completion**: bounded-memory GetObject/Range
   reads, streaming multipart part persistence, real multipart assembly, and
   restart-safe multipart completion.
3. **S3-P1/S3-P2 core semantics**: complete list semantics, CopyObject edge
   cases, DeleteObjects mixed-result semantics, conditional writes, and
   storage-engine parity for object/bucket CRUD.
4. **Versioning and delete markers**: version IDs, latest-version resolution,
   delete markers, versioned reads/deletes/lists, and interaction with multipart.
5. **Enforcement/background behavior**: lifecycle execution, replication,
   notifications, inventory/analytics/metrics generation, object-lock/legal-hold
   enforcement, and retention expiry.
6. **Distributed production readiness**: executed multi-node membership,
   placement, quorum read/write, metadata/manifest replication, healing,
   rebalance, and node-failure e2e validation.
7. **External compatibility certification**: AWS CLI, boto3, MinIO `mc`, rclone,
   Ceph s3-tests, and Iceberg/Spark/Trino workloads.

## Consequences

- EP-1 becomes the immediate first implementation track. Without SigV4 and
  authorization, Magrathea must not be exposed to untrusted networks and cannot
  honestly claim production S3 compatibility.
- Requirements must be authored before implementation. The first executable
  requirement source for this roadmap is
  `s3-reactive-api-adapter/src/test/features/requirements/phase-ep1-security-identity.feature`.
- Unsupported/config-only behavior remains explicitly tagged until production
  behavior and semantic validation exist.
- Admin/storage-engine APIs remain separate from S3 object semantics and must not
  become a parallel object API.

## Initial EP-1 scope

The first EP-1 slice is deliberately narrow and blocking:

- reject anonymous S3 requests in secured mode;
- accept correctly signed SigV4 requests for a configured access key;
- reject unknown access keys, bad signatures, stale dates, and payload-hash
  mismatches;
- deny by default when no policy/ACL grants the action;
- enforce bucket policy, ACL, PublicAccessBlock, and expected-owner checks for a
  representative object read/write path;
- emit durable audit events for allow/deny decisions without leaking secret keys
  or object bodies;
- prove SSE stores encrypted bytes at rest, not only metadata headers.
