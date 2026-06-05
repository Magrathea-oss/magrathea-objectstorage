# ADR 0015 — Handler minimal pattern and postponed repository responsibilities

## Context

Phase 1 of the Handler Correction Plan required cleaning up all S3 API handlers to follow a strict minimal pattern. Originally, handlers contained validation logic, bucket existence checks, ETag computation, CORS validation, website routing, and handler-local state (ConcurrentHashMap for ACL/tagging, ABAC, ObjectLock, etc.). This made handlers complex, coupled to cross-cutting concerns, and difficult to test in isolation.

The project uses hexagonal architecture with DDD: handlers are HTTP adapters, application services orchestrate domain calls, repositories handle persistence. Cross-cutting concerns belong in the repository layer where preconditions can be checked before state mutations.

## Decision

We will enforce the **extract → delegate → response** pattern for all handlers in `s3-reactive-api-adapter`:

1. **Extract**: `S3RequestExtractor` parses HTTP headers, path variables, and query parameters into typed primitives and DTOs
2. **Delegate**: The handler calls a single reactive service method — no validation, no bucket checks, no ETag computation, no CORS/website logic, no handler-local state
3. **Response**: `S3ResponseBuilder` or direct XML serialization converts the service result into an HTTP response

All cross-cutting concerns are **postponed to the repository layer**:

- Validation (Spring Reactive Validator) → repository preconditions
- Bucket existence checks → repository preconditions
- Bucket name validation → repository preconditions
- ETag computation → repository (generated on store)
- Handler-local ACL/grants/tagging → repository
- CORS validation → repository/service
- Website routing → repository/service

The `S3BucketConfigHandler` is **excluded** from this cleanup because it uses a complex registry/strategy pattern with `ConcurrentHashMap` for ABAC, ObjectLock, Metadata, InventoryTable, and JournalTable configurations. Its cleanup is postponed to a separate phase.

### Handlers cleaned up

| Handler | Before | After |
|---|---|---|
| `S3ObjectOperationsHandler` | Bucket checks, validation, ETag | Delegates to `ReactiveObjectService` |
| `S3ObjectMetadataHandler` | `bucketService.findByName()`, `ConcurrentHashMap` for ACL/tag, CORS | Delegates to `ReactiveObjectService` |
| `S3MultipartHandler` | `bucketService.findByName()`, `DigestUtils.md5DigestAsHex()` for ETag | Delegates to `ReactiveMultipartUploadService` |
| `S3BucketOperationsHandler` | Bucket name validation, `validateRuntimeRequest`, `applyRuntimeHeaders`, `websiteRouting` | Delegates to `ReactiveBucketService` |
| `S3BucketMetadataHandler` | `ConcurrentHashMap` for ACL/tag, `visibleAcl`, `validatePublicAclMutation`, CORS | Delegates to `ReactiveBucketService` |
| `S3SessionHandler` | Session state management | Delegates to `ReactiveBucketService.createSession()` |
| `S3BucketConfigHandler` | Complex registry + `ConcurrentHashMap` for ABAC/ObjectLock/Metadata | ⏳ Excluded |

### New domain files

| File | Description |
|---|---|
| `WriteState.java` | Enum: `CREATED` → `WRITING` → `WRITTEN` → `DELETED` — write lifecycle state machine |
| `ContentDescriptor.java` | Record: size, checksum, content type, disposition, encoding, language |
| `EncryptionConfiguration.java` (aggregate) | Record: encryption type, KMS key ID, KMS context, algorithm |
| `EncryptionType.java` | Enum: `NONE`, `SSE_S3`, `SSE_KMS`, `SSE_C` |

These files support the S3Object sealed state machine hierarchy (`ActiveS3Object`, `ArchivedS3Object`, `LockedS3Object`, `DeletedS3Object`) and write lifecycle tracking.

## Consequences

- **Handlers are simpler**: each handler is a thin HTTP adapter — easy to test, easy to review, easy to maintain
- **Repository layer gains responsibility**: preconditions, ETag computation, CORS, website routing, and state management move to the infrastructure layer where they belong
- **Postponed tests removed**: tests for validation, bucket checks, ETag, CORS, website routing are removed until those features are implemented in repositories
- **S3BucketConfigHandler remains complex**: its cleanup is deferred to a separate phase
- **Risk**: postponed features may be forgotten. Tracked in PLAN.md "Postponed Items" table
- **Verification**: `mvn test` passes with 0 failures

## Status

Accepted

## Date

2026-06-05
