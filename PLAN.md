# Magrathea ObjectStore

## Status

Current implementation is a Java 21 + Spring Boot 4 WebFlux S3-compatible object store. The only public HTTP API is the AWS S3-compatible API exposed by the pluggable `s3-reactive-api-adapter` module.

## Maven Modules

```
magrathea-objectstorage/
├── pom.xml
├── s3-reactive-api-adapter/                    # Pluggable S3 HTTP adapter (RouterFunction, XML, Cucumber tests)
├── object-store-domain/                        # Pure S3 domain: aggregates, value objects, domain events
├── object-store-reactive-repository-application/ # Reactive CQS repository interfaces
├── object-store-reactive-application/          # Reactive application services and DTOs
├── object-store-reactive-infrastructure/       # Reactive in-memory repository implementations
├── storage-engine-domain/                      # Storage Engine domain: policy, workflow, device, trace, manifest
├── storage-engine-application/                 # Storage Engine reactive orchestration and ports
├── storage-engine-infrastructure/              # Storage Engine filesystem cluster backend (FS nodes, content-address index, chaos)
├── object-store-reactive-repository-storage-engine-infrastructure/ # ACL + adapter: Object Store → Storage Engine
├── bootstrap-application/                      # Spring Boot entry point
├── docs/                                       # ARC42, ADR, C4
└── test-aws-cli.sh                             # AWS CLI compatibility tests for implemented S3 operations
```

### Two-backend Repository Strategy

The Object Store repository interfaces (`object-store-reactive-repository-application`) have two
concrete implementations:

| Backend | Module | Profile | Description |
|---|---|---|---|
| InMemory | `object-store-reactive-infrastructure` | `single-node` (default) | Reactive in-memory repositories for development and single-node deployments |
| Storage Engine | `object-store-reactive-repository-storage-engine-infrastructure` | `storage-engine` | Filesystem cluster backend using the Storage Engine bounded context |

The ACL/adapter module (`object-store-reactive-repository-storage-engine-infrastructure`) translates
Object Store repository commands into Storage Engine domain commands. It is the Anti-Corruption
Layer between the two bounded contexts.

Removed modules/components:
- `shared-domain` removed.
- `InternalApiRouter` removed because it was not standard S3.
- Legacy blocking application/infrastructure module entries are obsolete; current runtime paths use reactive modules.

## S3 API Handler Organization

`s3-reactive-api-adapter` route mapping is intentionally split by context:

| Class | Responsibility |
|---|---|
| `S3ProxyRouter` | Route composition only |
| `S3BucketOperationsHandler` | Bucket lifecycle, bucket-level listings, location/versioning, directory-bucket listing |
| `S3BucketMetadataHandler` | Bucket ACL and tagging metadata |
| `S3BucketConfigHandler` | Bucket configuration, object-lock configuration, ABAC, and Phase F bucket metadata/table configurations |
| `S3ObjectOperationsHandler` | Object CRUD, copy, multi-delete, torrent, restore, rename, select, and Object Lambda response operations |
| `S3ObjectMetadataHandler` | Object ACL, tagging, attributes, legal hold, retention, and encryption metadata |
| `S3MultipartHandler` | Multipart upload lifecycle |
| `S3SessionHandler` | Phase F session creation |
| `S3WebSupport` | Shared request predicates and S3 XML error helpers |

`ContentStore` has been removed. Object content is saved through reactive object repository commands. Domain interfaces expose no framework types; reactive application implementations carry `Flux<DataBuffer>` at the application boundary. `InMemoryReactiveS3ObjectRepository` persists metadata plus content in the reactive infrastructure module.

## Pluggable S3 API

`s3-reactive-api-adapter` is loaded through Spring Boot 4 auto-configuration:

- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `S3ApiConfig` guarded by reactive bucket/object service classes
- `S3ApiConfig` guarded by `s3.api.enabled=true` (`true` by default)

Activation modes:

| Mode | Behavior |
|---|---|
| `s3-reactive-api-adapter` dependency present + `s3.api.enabled=true` | S3 routes are active |
| `s3-reactive-api-adapter` dependency present + `s3.api.enabled=false` | S3 routes disabled |
| `s3-reactive-api-adapter` dependency absent | No S3 web API loaded |

## Testing Strategy

| Level | Type | Command | Notes |
|---|---|---|---|
| 1 | Pure JUnit | `mvn test -pl object-store-domain` | Domain only, no Spring |
| 2 | Cucumber BDD | `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` | RouterFunction integration |
| 3 | AWS CLI compatibility | `bash test-aws-cli.sh` | Requires app running on `localhost:8080` |
| 4 | Clover coverage | `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` | Generates Clover reports |
| 5 | AWS CLI Maven profile | `mvn -N verify -Paws-cli-tests` | Requires app running and AWS CLI installed |

Consolidated report: `docs/test-report.md` includes AWS CLI outcomes, Surefire/JUnit/Cucumber outcomes, and Clover coverage percentages.

Workflow rule: every development phase includes an **AWS CLI test sub-phase** after `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` Cucumber coverage passes and before the phase is marked complete, whenever AWS CLI exposes the implemented operation(s).

Latest verification: `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` => 227 tests, 0 failures, 0 errors. Domain: 213 tests. Application: 18 tests. Total: **458 tests**, 0 failures.

## Coverage Tooling

Clover/OpenClover is configured in the parent POM under profile `coverage`.

Report command:

```bash
mvn -Pcoverage clover:setup test clover:aggregate clover:clover
```

Expected reports:

```text
target/site/clover/
```

AWS CLI compatibility tests are not run by the default `mvn test` because they require an external process (the boot application) and AWS CLI. They are available via `test-aws-cli.sh` and Maven profile `aws-cli-tests`.

## Current API Coverage Analysis

See [`docs/api-coverage.md`](docs/api-coverage.md) for detailed request/response coverage. The implemented operation count is now **111/111**.

The coverage documentation tracks:
- request header coverage
- query parameter coverage
- request and response body coverage
- response header notes where relevant
- per-operation **Status Codes** tables

## Implementation Rule for New S3 Operations

For every newly implemented S3 operation:

1. Add RouterFunction route in `s3-reactive-api-adapter`.
2. Use AWS S3 terminology only in domain/application DTOs.
3. Add XML response/request records using Jackson 3 annotations when XML is required.
4. Add Cucumber feature scenario(s) in `s3-reactive-api-adapter/src/test/features/object-store/`.
5. Add AWS CLI coverage in `test-aws-cli.sh` if AWS CLI exposes the operation.
6. Update this PLAN coverage table.
7. Update ARC42 and ADRs if the operation introduces a new architectural decision.

### Domain / Application / Test Requirement

Every new S3 operation MUST include:

**Domain layer (`object-store-domain`):**
- Add or update domain value objects / aggregates if the operation introduces new data concepts (e.g., ACL, tagging, storage class)
- Add pure JUnit tests in `object-store-domain/src/test/` for every new domain type

**Application layer (`object-store-reactive-application`):**
- Add or update reactive application service methods in `ReactiveBucketService` / `ReactiveObjectService`
- Add or update DTOs in `application/dto/`
- Data MUST flow through domain → application → handler, NOT be stored directly in handler `ConcurrentHashMap`

**Tests:**
- At least 1 success scenario + at least 1 failure scenario per operation in Cucumber
- Failure scenarios must cover every error case documented in the AWS S3 API spec (e.g., NoSuchBucket, NoSuchKey, BucketAlreadyExists, InvalidArgument)
- AWS CLI failure tests for every error case that AWS CLI exposes

### Coverage Verification

The AWS CLI test sub-phase is mandatory after Cucumber for each phase that adds or changes an `aws s3api`-exposed operation.

```bash
# Verify success + failure coverage
mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false
bash test-aws-cli.sh

# Verify domain unit tests
mvn test -pl object-store-domain
```

## Verification

```bash
mvn test
mvn -Pcoverage clover:setup test clover:aggregate clover:clover

# In one shell
java -jar bootstrap-application/target/bootstrap-application-1.0.0-SNAPSHOT.jar

# In another shell
bash test-aws-cli.sh
# or
mvn -N verify -Paws-cli-tests
```

---

## Phase 1 — Unified Handler Refactoring: Domain Purity & Reactive Correctness

### Objective

Refactor `S3ObjectOperationsHandler`, `S3ObjectMetadataHandler`, and `S3MultipartHandler` to eliminate architectural violations in the object domain:

1. **S3Object state machine**: Replace random `with*` methods (`withEtag`, `withStorageClass`, `withContent`, `withLegalHold`, `withObjectLockConfiguration`, `withRetentionPeriod`, `withRestore`, `withSseHeaders`, `withEncryption`, `withKey`, `withDeleted`) with a proper workflow/state machine on `S3Object`. Each transition is a meaningful method that checks preconditions and produces domain events.
2. **Domain purity**: No HTTP concepts in the domain layer. Remove `SseHeaders` from domain — SSE is an HTTP protocol concern. Replace with `EncryptionConfiguration` (algorithm + key reference + optional encryption context), a pure domain value object meaningful in the storage domain.
3. **ETag**: ETag is computed by the repository when content is stored. The handler reads it from the service response and echoes as HTTP header. No handler-level ETag computation.
4. **Checksums**: `ChecksumValue` stays in domain as a value object (algorithm + hash). The handler extracts checksum headers and passes them as `Set<ChecksumValue>` to the service. The service validates checksum during object creation and passes it to the repository. Checksum is part of `ContentDescriptor`, validated in the domain constructor.
5. **VersionId**: Generated by the repository, not the handler.
6. **StorageClass**: Passed through to domain, not parsed in the handler.
7. **Content-Type**: Spring Boot already handles content negotiation — handlers do not need to detect or default Content-Type.

### Scope

This phase addresses **ONLY** `S3Object` and its handlers (`S3ObjectOperationsHandler`, `S3ObjectMetadataHandler`, `S3MultipartHandler`). Bucket handlers (`S3BucketOperationsHandler`, `S3BucketMetadataHandler`, `S3BucketConfigHandler`) are **NOT** touched in this phase. The scope is intentionally narrowed to object-domain purity to keep the change focused and reviewable.

### Architectural Principles

#### Layer Responsibilities

| Layer | Responsibilities |
|-------|-----------------|
| **Handler** (s3-reactive-api-adapter) | Extract HTTP headers (`bucket`, `key`, headers) into primitive/DTO values; delegate to application service; convert service response to HTTP response (headers, status code). No bucket checks, no checksum computation, no ETag generation, no version-id generation, no domain object construction. |
| **Application Service** (object-store-reactive-application) | Orchestrate domain calls; call repository; build domain objects; apply workflow transitions on `S3Object`; enforce application-level validation (bucket existence). Use Spring WebFlux patterns properly — no handler-level if-chains. |
| **Domain** (object-store-domain) | Pure aggregates and value objects: `S3Object` with state machine, `Bucket`, `ChecksumValue`, `EncryptionConfiguration`, `ObjectKey`, `StorageClass`, `ContentDescriptor`. No HTTP types. |
| **Repository** (object-store-reactive-infrastructure) | Compute ETag on store; generate version-id on store; persist and retrieve `S3Object`. |

#### S3Object State Machine

Replace ad-hoc `with*` setters with a proper workflow. `S3Object` has these states:

| State | Description | Entered via | Domain Events Produced |
|-------|-------------|-------------|------------------------|
| **Creating** | Initial state after `create()` factory method | `S3Object.create(bucketId, key)` | `ObjectCreatingEvent` |
| **Active** | Content is attached and object is fully writable | `attachContent(ContentDescriptor)` | `ObjectCreatedEvent` |
| **Locked** | Legal hold / object lock / retention is active | `applyLock(LockConfiguration)` | `ObjectLockAppliedEvent` |
| **Archived** | Object has been archived (Glacier / Deep Archive restore) | `archive()` | `ObjectArchivedEvent` |
| **Restored** | Archived object has been restored to active tier | `restore()` | `ObjectRestoredEvent` |
| **Deleted** | Object has been deleted | `delete()` | `ObjectDeletedEvent` |

**Key rules:**

- `attachContent()` is only valid from **Creating** state. Precondition: state == Creating. Produces `ObjectCreatedEvent` and transitions to **Active**.
- `delete()` is valid from **Active**, **Locked**, or **Restored** states. Produces `ObjectDeletedEvent` and transitions to **Deleted**.
- `applyLock()` is valid from **Active** or **Restored** states. Produces `ObjectLockAppliedEvent` and transitions to **Locked**.
- `archive()` is valid from **Active** or **Locked** states. Produces `ObjectArchivedEvent` and transitions to **Archived**.
- `restore()` is only valid from **Archived** state. Produces `ObjectRestoredEvent` and transitions to **Restored**.
- Each method checks its precondition and throws an `IllegalStateException` (or a domain-specific `InvalidStateTransitionException`) if violated.
- No generic setters (`withEtag`, `withStorageClass`, `withContent`, `withLegalHold`, `withObjectLockConfiguration`, `withRetentionPeriod`, `withRestore`, `withSseHeaders`, `withEncryption`, `withKey`, `withDeleted`).

**Removed methods:**

`withEtag`, `withStorageClass`, `withContent`, `withLegalHold`, `withObjectLockConfiguration`, `withRetentionPeriod`, `withRestore`, `withSseHeaders`, `withEncryption`, `withKey`, `withDeleted` — all replaced by state machine transitions above.

#### EncryptionConfiguration

Replace HTTP-centric `SseHeaders` with domain-relevant `EncryptionConfiguration`:

```java
// object-store-domain/src/main/java/.../domain/value/EncryptionConfiguration.java
public record EncryptionConfiguration(
    EncryptionAlgorithm algorithm,   // AES256, AWS_KMS, SSE_C
    EncryptionKeyReference keyRef,    // key ID or reference, NOT the actual key
    EncryptionContext encryptionContext // optional
) { }

public enum EncryptionAlgorithm {
    AES256,
    AWS_KMS,
    SSE_C
}

public record EncryptionKeyReference(
    String keyId          // key ID or reference (KMS key ID, customer key MD5, etc.)
) { }

public record EncryptionContext(
    Map<String, String> context  // optional encryption context
) { }
```

This is a **pure domain concept representing encryption intent**, NOT HTTP headers.

#### ChecksumValue

`ChecksumValue` stays in domain as a value object:

```java
// object-store-domain/src/main/java/.../domain/value/ChecksumValue.java
public record ChecksumValue(
    ChecksumAlgorithm algorithm,  // CRC32, CRC32C, SHA256, etc.
    String value                  // Base64-encoded checksum value
) {
    // Validation: algorithm must be supported
    public ChecksumValue {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}

public enum ChecksumAlgorithm {
    CRC32, CRC32C, SHA1, SHA256, SHA512,
    CRC64NVME, XXHASH64, XXHASH3, XXHASH128
}
```

Checksum is part of `ContentDescriptor`, validated in the domain constructor:

```java
// object-store-domain/src/main/java/.../domain/value/ContentDescriptor.java
public record ContentDescriptor(
    long contentLength,
    String contentType,
    Set<ChecksumValue> checksums,    // ← validated in domain constructor
    // ... other content metadata
) {
    public ContentDescriptor {
        Objects.requireNonNull(checksums, "checksums must not be null");
        // Each ChecksumValue is validated in its own constructor
    }
}
```

#### Updated S3Object Aggregate

```java
public sealed abstract class S3Object {
    public abstract Id id();
    public abstract Bucket.Id bucketId();
    public abstract ObjectKey key();
    public abstract Optional<ContentDescriptor> contentDescriptor();
    public abstract Optional<EncryptionConfiguration> encryption();
    public abstract StorageClass storageClass();
    public abstract Map<String, String> userMetadata();
    public abstract ETag etag();                    // computed by repository
    public abstract VersionId versionId();           // generated by repository
}

// Factory method
public static CreatingS3Object create(Bucket.Id bucketId, ObjectKey key) { ... }

// States as subclasses or sealed interface variants
public final class CreatingS3Object extends S3Object {
    public ActiveS3Object attachContent(ContentDescriptor content) { ... }
}

public final class ActiveS3Object extends S3Object {
    public LockedS3Object applyLock(LockConfiguration lock) { ... }
    public ArchivedS3Object archive() { ... }
    public DeletedS3Object delete() { ... }
}

public final class LockedS3Object extends S3Object {
    public ArchivedS3Object archive() { ... }
    public DeletedS3Object delete() { ... }
}

public final class ArchivedS3Object extends S3Object {
    public RestoredS3Object restore() { ... }
}

public final class RestoredS3Object extends S3Object {
    public LockedS3Object applyLock(LockConfiguration lock) { ... }
    public DeletedS3Object delete() { ... }
}

public final class DeletedS3Object extends S3Object {
    // Terminal state — no further transitions
}
```

### Handler Refactoring Patterns

#### Minimal Handler Pattern

Handler extracts only: `bucket`, `key`, headers → passes to application service. Handler does **NOT**:
- check bucket existence
- validate storage class
- compute ETag
- parse checksums
- build domain objects

```java
// In handler — minimal extraction, delegation to service
return objectService.putObject(bucketName, key, body, checksums, metadata, storageClass)
    .flatMap(result -> buildOkResponse(result));
```

The application service (`ReactiveObjectService.putObject()`) handles all orchestration: calls repository, builds domain objects, applies workflow transitions. Handler receives the response from service and converts to HTTP response (headers, status code).

Use Spring WebFlux patterns properly — no handler-level if-chains. All branching logic lives in the application service.

#### Bucket Existence Check — Deleted from Handlers

Before (anti-pattern):
```java
// Handler checks bucket existence directly
return bucketService.doesBucketExist(bucketName)
    .flatMap(exists -> {
        if (!exists) {
            return Mono.error(new NoSuchBucketException(bucketName));
        }
        ...
    });
```

After (correct):
```java
// Handler delegates to service — no bucket check
return objectService.putObject(bucketName, key, body, checksums, metadata, storageClass)
    .flatMap(result -> buildOkResponse(result));
```

The application service (`ReactiveObjectService.putObject()`) handles bucket validation internally and returns domain-specific errors that the handler maps to HTTP status codes via `onErrorResume`.

#### Checksum — Handler Extracts, Service Validates

Handler extracts checksum headers and passes to service as `Set<ChecksumValue>`:
```java
// In handler — minimal extraction into Set<ChecksumValue>
var checksums = new HashSet<ChecksumValue>();
for (var headerName : request.headers().keySet()) {
    if (headerName.startsWith("x-amz-checksum-")) {
        var algorithm = ChecksumAlgorithm.fromS3HeaderName(headerName.substring("x-amz-checksum-".length()));
        var value = request.headers().firstHeader(headerName);
        checksums.add(new ChecksumValue(algorithm, value));
    }
}
```

Service validates checksum during object creation, passes to repository. Checksum is part of `ContentDescriptor`, validated in domain constructor.

#### ETag — Repository Computes, Handler Echoes

Handler never generates ETag:
```java
// In handler response — reads ETag from service result
var etag = result.etag().value();  // computed by repository
return ServerResponse.ok()
    .header("ETag", etag)
    .header("x-amz-version-id", result.versionId().value());
```

ETag is computed by the repository when content is stored (hash of content bytes). The handler reads ETag from the service response and echoes as HTTP header.

#### VersionId — Repository Generates, Handler Echoes

Handler never generates version ID:
```java
// Handler echoes what the repository assigned
var versionId = result.versionId().value();
```

#### StorageClass — Passed Through to Domain

Handler maps the header to domain enum, passes to service:
```java
var storageClass = StorageClass.fromS3Header(
    request.headers().firstHeader("x-amz-storage-class"));
```

#### Content-Type — Handled by Spring Boot

Spring Boot's `ServerRequest.bodyToFlux()` already handles content negotiation. The handler does NOT detect, default, or manipulate Content-Type. The domain stores the content type only if the application service passes it from the request; otherwise it is null/unknown.

### Unified Header Handling Table

| Header | Extracted by Handler | Passed as Domain Type | Validated/Processed by |
|--------|---------------------|----------------------|-----------------------|
| `x-amz-sdk-checksum-algorithm` | Yes | `ChecksumAlgorithm` enum | Service + domain (`ContentDescriptor`) |
| `x-amz-checksum-*` | Yes | `Set<ChecksumValue>` | Service + domain (`ContentDescriptor`) |
| `x-amz-meta-*` | Yes | `Map<String, String>` userMetadata | Domain aggregate |
| `x-amz-server-side-encryption` | Yes | `EncryptionConfiguration.algorithm` | Domain aggregate |
| `x-amz-server-side-encryption-customer-algorithm` | Yes | `EncryptionConfiguration.keyRef` | Domain aggregate |
| `x-amz-acl` | Yes | `Acl` domain enum | Application service |
| `x-amz-grant-*` | Yes | `Grant` value objects | Application service |
| `x-amz-expected-bucket-owner` | Yes | `OwnerId` value object | Application service |
| `Content-Type` | No (Spring Boot handles) | N/A | N/A |
| `x-amz-storage-class` | Yes | `StorageClass` domain enum | Domain aggregate |
| `x-amz-version-id` | No | N/A | Repository generates |

### Revised Sub-Phases

Instead of the original monolithic implementation order, Phase 1 is broken into focused sub-phases. Each sub-phase is a self-contained unit that can be merged and tested independently.

### Implementation Order (Revised)

```
Phase 1a: Header enum + RequestExtractor foundation               ✅ DONE
Phase 1b: ObjectKey as natural identifier                          ✅ DONE
Phase 1c: Remove handler-local state (ConcurrentHashMap)
Phase 1d: Remove handler-level ETag computation
Phase 1e: Add missing headers to PutObject (ACL, tagging, object lock)
Phase 1f: Fix StorageClass flow into domain
Phase 1g: Fix S3ProxyRouter compilation
Phase 1h: Unify SSE header extraction
Phase 1i: Add missing CopyObject headers
Phase 1j: Add missing conditional headers (GetObject, HeadObject)
Phase 1k: Add missing multipart headers (checksums, SSE-C)
Phase 1l: Add missing response headers
Phase 1m: Add missing request options headers
Phase 1n: Create HeaderExtractor utility
Phase 1o: Define S3Object state machine — sealed hierarchy, transitions, domain events
Phase 1p: Define ContentDescriptor value object
Phase 1q: Remove SseHeaders, create EncryptionConfiguration
Phase 1r: Add domain tests for state machine, EncryptionConfiguration, ContentDescriptor
Phase 1s: Add Cucumber tests for updated handlers
Phase 1t: Verify: mvn test → 0 failures
Phase 1u: Update PLAN.md, ARC42, ADRs
Phase 1v: Create docs/user-manual.md
```

### Agents

| Phase | Agent |
|-------|-------|
| Phase 1a | java-infra-coder |
| Phase 1b | java-infra-coder |
| Phase 1c | java-infra-coder |
| Phase 1d | java-infra-coder |
| Phase 1e | java-infra-coder |
| Phase 1f | java-infra-coder |
| Phase 1g | java-infra-coder |
| Phase 1h | java-infra-coder |
| Phase 1i | java-infra-coder |
| Phase 1j | java-infra-coder |
| Phase 1k | java-infra-coder |
| Phase 1l | java-infra-coder |
| Phase 1m | java-infra-coder |
| Phase 1n | java-infra-coder |
| Phase 1o–1q | java-domain-coder |
| Phase 1r | java-tester |
| Phase 1s | java-tester |
| Phase 1t | java-planner |
| Phase 1u | documenter |
| Phase 1v | documenter |

### Verification

```bash
mvn test --also-make
bash test-aws-cli.sh
mvn -N verify -Paws-cli-tests
mvn test -pl s3-reactive-api-adapter -am -Pawscli-cucumber-tests
```

Expected: all 458+ tests pass, AWS CLI tests pass, domain tests pass.

### Completed in Current Work Phase

| Deliverable | File | Status |
|-------------|------|--------|
| S3Header enum (92 headers, 22 categories) | `s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/headers/S3Header.java` | ✅ Created |
| S3RequestExtractor (extractObjectKey) | `s3-reactive-api-adapter/src/main/java/com/example/magrathea/s3api/adapter/web/headers/S3RequestExtractor.java` | ✅ Created |
| ObjectKey record (bucket + key composite) | `object-store-domain/src/main/java/com/example/magrathea/objectstore/domain/value/ObjectKey.java` | ✅ Refactored |
| ReactiveObjectService.saveObjectWithContent(ObjectKey) overload | `object-store-reactive-application/src/main/java/.../ReactiveObjectService.java` | ✅ Updated |
| Repository saveWithContent(ObjectKey) / findByBucketAndKey(ObjectKey) | `InMemoryReactiveS3ObjectRepository.java` + interfaces | ✅ Updated |
| PutObject handler refactored to use ObjectKey + new overload | `S3ObjectOperationsHandler.java` | ✅ Updated |
| Header analysis document | `docs/header-analysis.md` | ✅ Created |

### Remaining Problems in PutObject (Not Yet Fixed)

| Issue | Details | Targeted In |
|-------|---------|-------------|
| StorageClass extracted but not applied to domain aggregate | `x-amz-storage-class` parsed but not stored on `S3Object` | Phase 1f |
| Missing ACL/grant headers | `x-amz-acl`, `x-amz-grant-*` not extracted | Phase 1e |
| Missing tagging header | `x-amz-tagging` not extracted | Phase 1e |
| Missing object lock headers | `x-amz-object-lock-legal-hold`, `x-amz-object-lock-mode`, `x-amz-object-lock-retain-until-on` not extracted | Phase 1e |
| Content-Disposition/Encoding passed as null | Headers extracted but passed as null to service | Phase 1e |
| Response missing headers | `x-amz-version-id`, `x-amz-request-id`, runtime headers not sent | Phase 1l |
| StorageClass validation list has non-AWS classes | `S3Header.StorageClass` enum includes invalid entries | Phase 1f |
| S3ProxyRouter compilation errors | Uses non-standard `accept()`/`queryParam()` methods | Phase 1g |
| Handler-local ConcurrentHashMap still present | ACL and tagging stored in handler maps instead of domain/repository | Phase 1c |
| Handler-level ETag computation still present | `DigestUtils.md5DigestAsHex()` in multipart handlers | Phase 1d |
| SSE header parsing duplicated across handlers | Same 7 SSE headers parsed in 2 places | Phase 1h |
| Missing CopyObject conditional headers | 8 copy-source conditional headers not extracted | Phase 1i |
| Missing GetObject/HeadObject conditional headers | Range, If-Match, If-None-Match, If-* headers not parsed | Phase 1j |
| Missing multipart checksum/SSE-C headers | UploadPart missing 7 headers | Phase 1k |
| Missing request options | `x-amz-expected-bucket-owner`, `x-amz-request-payer`, `x-amz-version-id` not parsed | Phase 1m |
| Missing response headers | `x-amz-id-2`, `x-amz-request-charged`, archive status, tagging count, etc. | Phase 1l |
