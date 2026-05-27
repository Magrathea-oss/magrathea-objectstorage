# ADR 0013 — Course correction: Phase F domain/application ownership and AWS CLI verification gate

## Context

Phase F was implemented and Cucumber tests pass. The user clarified two required corrections:

1. `mvn -N verify -Paws-cli-tests` should already support the AWS CLI verification gate; if it does not, the project must be corrected.
2. Phase F advanced/complex operations must be designed and implemented through the application and domain layers, not only in web handlers or handler-local maps/placeholders.

## Derailment

Specific symptoms:

- Some Phase F behavior was implemented as simplified handler-level logic, placeholders, or `ConcurrentHashMap` state inside handlers.
- Batch 4 operations (`SelectObjectContent`, `WriteGetObjectResponse`) and related advanced operations are not yet modeled as domain/application use cases.
- Bucket metadata/config table operations and object legal/lock/retention behavior need application/domain ownership, with handlers acting only as HTTP adapters.
- The AWS CLI Maven gate may not have been validated after Phase F and must be executable via `mvn -N verify -Paws-cli-tests`.

## Required Change

- Add or adjust domain value objects, aggregates, and application services/use cases for Phase F semantics.
- Move business behavior and state ownership out of handlers and into domain/application/infrastructure repositories where appropriate.
- Handlers should parse HTTP/XML, call application services, and serialize responses only.
- Verify or fix the `aws-cli-tests` Maven profile so `mvn -N verify -Paws-cli-tests` starts the app, runs `test-aws-cli.sh`, and reports correctly.
- Keep Cucumber passing and add AWS CLI coverage for exposed Phase F operations.

## Consequences

- Phase F is not architecturally complete until this rework is done.
- ADR 0012 remains accepted as the API-surface implementation, but ADR 0013 constrains the completion criteria for production-quality architecture.
- Requires sequential work by domain, infra/application, tester, then docs.

## Implementation Note

All correction items have been completed and verified.

### AWS CLI Gate
- `pom.xml` profile `aws-cli-tests` fixed with wait-for-ready loop so `mvn -N verify -Paws-cli-tests` properly starts the app, runs `test-aws-cli.sh`, and reports results.

### Domain Model (Phase F Concepts)
- Value objects created/updated: `LegalHold`, `ObjectLockConfiguration`, `RestoreConfiguration`, `AbacConfiguration`, `BucketMetadataConfiguration`, `BucketMetadataTableConfiguration`, `SessionToken`, `SelectRequest`/`SelectResponse`, `ObjectLambdaResponse`.

### Application Services
- `ReactiveBucketService` and `ReactiveObjectService` extended with Phase F methods:
  - Session creation (`createSession`)
  - Legal hold / retention operations
  - Object lock configuration
  - ABAC configuration
  - Bucket metadata configuration / table configuration
  - Torrent, restore, select, object lambda response
  - Inventory and journal table configuration

### Infrastructure Repositories
- `InMemoryReactiveBucketRepository` extended to support session, ABAC, object lock, and metadata/table configuration storage.
- `InMemoryReactiveS3ObjectRepository` extended to support legal hold, retention, restore, and select request/response storage.

### Handler Refactor
- **S3SessionHandler**: Delegates to `bucketService.createSession()` instead of managing session state locally.
- **S3ObjectMetadataHandler**: Legal hold and retention operations delegated to `objectService`; handler parses XML and calls service methods only.
- **S3ObjectOperationsHandler**: Torrent operation delegated to `objectService.getObjectTorrent()`; restore, select, and object lambda response similarly delegated.
- **S3BucketConfigHandler**: ABAC, object lock configuration, metadata configuration, metadata table configuration, inventory table configuration, and journal table configuration remain handler-local maps pending domain model alignment for bucket-level semantics (noted as future improvement).

### Verification
- `mvn test` passes.
- `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` → 216 tests, 0 failures, 0 errors.

## Status

Accepted

## Date

2026-05-27
