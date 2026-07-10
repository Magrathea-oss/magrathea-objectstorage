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
2. **EP-2 Metadata durability closure**: promote the storage-engine metadata
   families (bucket registry, multipart state, per-object config, tags, ACLs,
   and bucket configuration) from restart-simulation evidence to full
   process/Spring restart Cucumber validation.
3. **EP-3 Streaming and multipart completion**: bounded-memory GetObject/Range
   reads, streaming multipart part persistence, real multipart assembly, and
   restart-safe multipart completion.
4. **S3-P1/S3-P2 core semantics**: complete list semantics, CopyObject edge
   cases, DeleteObjects mixed-result semantics, conditional writes, and
   storage-engine parity for object/bucket CRUD.
5. **Versioning and delete markers**: version IDs, latest-version resolution,
   delete markers, versioned reads/deletes/lists, and interaction with multipart.
6. **Enforcement/background behavior**: lifecycle execution, replication,
   notifications, inventory/analytics/metrics generation, object-lock/legal-hold
   enforcement, and retention expiry.
7. **Distributed production readiness**: executed multi-node membership,
   placement, quorum read/write, metadata/manifest replication, healing,
   rebalance, and node-failure e2e validation.
8. **External compatibility certification**: AWS CLI, boto3, MinIO `mc`, rclone,
   Ceph s3-tests, and Iceberg/Spark/Trino workloads.

## Consequences

- EP-1 was the immediate first implementation track. Without SigV4 and
  authorization, Magrathea must not be exposed to untrusted networks and cannot
  honestly claim production S3 compatibility. After EP-1 local built-in closure,
  EP-2 metadata durability was closed for the declared storage-engine scope; EP-3
  streaming and multipart completion is now the next blocker because later
  semantic and distributed work cannot honestly rely on whole-body buffering or
  incomplete multipart persistence.
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

Status update (2026-07-10): EP-1 is `@implemented-and-validated` for the
declared local built-in scope. All current EP-1 WebTestClient and AWS CLI/e2e
scenarios are wired through a Spring Security Reactive backbone
(`SecurityWebFilterChain`, SigV4 `ServerAuthenticationConverter`, reactive
authentication/authorization managers, and S3 XML security handlers). Anonymous,
unknown-key, bad-signature, stale-date, and payload-hash-mismatch requests are
rejected; valid keys are accepted; authenticated principals without an allow rule
are denied by default; explicit deny overrides allow; PublicAccessBlock blocks
public ACL reads; expected bucket owner mismatch denies the request; durable
redacted audit is recorded; and an SSE-S3 encrypted-at-rest inspection slice
passes. Additional EP-1 service specs validate encrypted durable access-key
storage with revocation, durable policy reload, append/fsync tamper-evident audit
logging, and durable local key-management material. Future external identity
federation (OIDC/LDAP/Kerberos/STS) remains KA-4 scope rather than an EP-1
blocker.

## EP-2 status update

Status update (2026-07-10): EP-2 is `@implemented-and-validated` for the declared
storage-engine metadata durability scope. Object tags and object ACL metadata have
WebTestClient restart-simulation Cucumber coverage in `PhaseEp2MetadataDurabilityCucumberTest`,
alongside the combined bucket/object/multipart metadata scenario. `PhaseEp2MetadataDurabilityFullRestartCucumberTest`
adds full Spring stop/start evidence for bucket registry, multipart upload state,
legal hold, object lock configuration, retention, object encryption, object restore
state, object tags, object ACL metadata, CORS, notification, bucket object-lock,
inventory-table, journal-table, ABAC, metadata, metadata-table, and the combined
bucket/object-tag/object-ACL/multipart metadata scenario. REQ-DUR-001 through
REQ-DUR-005 are now `@implemented-and-validated` for storage-engine mode; the
remaining EP-2 scenarios are explicit in-memory-profile exemption checks rather
than storage-engine durability gaps. Object ACLs are persisted in a durable
storage-root sidecar (`metadata/s3-object-acls`) through the S3 adapter boundary.
