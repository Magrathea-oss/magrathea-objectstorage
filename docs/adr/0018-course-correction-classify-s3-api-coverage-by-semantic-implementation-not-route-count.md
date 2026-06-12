# ADR 0018 — Course correction: classify S3 API coverage by semantic implementation, not route count

## Context

Previous analysis and documentation treated the S3 API surface as complete because 111 of 111 S3 operations were represented by routes, handlers, or tests. The user clarified that this is misleading: many operations are fake, partial, or nominally mapped only. Current reporting mostly validates the `PutObject` path and does not prove family-wide S3 semantics, AWS CLI compatibility, persistence behavior, or storage-engine integration.

## Derailment

Route count and passing `WebTestClient` tests overstate S3 API completeness. Existing coverage claims conflate endpoint mapping with semantic implementation. The AWS CLI compatibility report is stale or failing, and advanced operation families likely return nominal responses without real state transitions, durable persistence, request/response parity, error semantics, or storage-engine behavior.

This means the project can appear to implement the full S3 API while only a small subset has meaningful behavior. In particular, handler existence, request routing, synthetic XML/HTTP responses, and isolated controller tests are not sufficient evidence that an operation is stateful, interoperable, storage-backed, or semantically compatible with Amazon S3.

## Required Change

`PLAN.md` must include a family-by-family S3 API completion proposal and reporting matrix. The matrix must classify every S3 operation by implementation level instead of route presence alone, with at least these dimensions:

- Mapped: an HTTP route and handler entry point exist.
- Stubbed: the operation returns a nominal response but does not implement meaningful S3 state changes or validation.
- Stateful: the operation creates, reads, updates, or deletes durable domain state with correct observable behavior.
- AWS CLI compatible: the operation is verified through current AWS CLI or equivalent black-box compatibility tests.
- Storage-engine compatible: the operation uses the configured storage engine correctly where object bytes, metadata, versions, or policy state require it.
- Semantically S3-compatible: the operation matches expected S3 behavior, including state transitions, response shape, headers, errors, idempotency, consistency expectations, and edge cases relevant to the supported scope.

The plan must propose completion work per S3 API family, including:

- Object CRUD.
- Bucket lifecycle and listing.
- Multipart upload.
- Metadata, ACL, and tagging.
- Bucket configuration.
- Versioning and delete markers.
- Policy, access, and public access controls.
- Encryption, object lock, legal hold, and retention.
- Lifecycle, replication, notification, logging, website, and CORS.
- Analytics, inventory, metrics, and intelligent tiering.
- Restore, select, torrent, and object lambda.
- Directory buckets and sessions.
- Request payment and ownership controls.

For each family, the plan must identify what is currently only mapped or stubbed, what must become stateful, what requires storage-engine behavior, what requires AWS CLI verification, and what can be explicitly deferred or documented as unsupported.

## Consequences

- Documentation must downgrade or qualify all `111/111` S3 API completion claims unless they explicitly mean route mapping only.
- Tests and reports must become API-family based and must distinguish route coverage from semantic, stateful, AWS CLI, and storage-engine compatibility.
- Completion priority must start from `PutObject` and read-after-write behavior, then extend outward to object reads, metadata, listings, multipart behavior, bucket state, versioning, policies, and advanced configuration families.
- Future reports must avoid treating handler existence, nominal responses, or passing narrow `WebTestClient` tests as proof of S3 compatibility.
- This ADR authorizes documentation and planning correction only. It does not authorize source-code changes and does not update `PLAN.md` by itself.

## Status

Accepted

## Date

2026-06-12
