# ADR 0009 — Reactive Repository Application Layer with CQRS: New `object-store-reactive-repository-application` Module

## Status

Accepted — 2026-05-24

Accepted by java-planner on 2026-05-24. This ADR defines the target architecture for reactive end-to-end non-blocking via native Mono/Flux modules and CQRS repository split. All implementation phases should follow the module structure and migration plan defined herein.

## Context

The current architecture (ADR 0001, ADR 0004) places repository interfaces inside `object-store-domain` using `CompletableFuture` return types, and application services in `object-store-application` bridge to `Mono`/`Flux` via `.join()` calls. This works but introduces blocking in the application layer — every `.join()` in `BucketService`, `ObjectService`, and `MultipartUploadService` pins a virtual thread, breaking the end-to-end reactive chain.

The project has committed to Spring Boot 4 WebFlux, where true end-to-end non-blocking requires that no thread blocks at any layer. The `.join()` bridge was accepted as a trade-off in ADR 0004, but it prevents:

1. **Reactive back-pressure propagation** from HTTP request body through application logic to persistence.
2. **Composable error handling** — `.join()` throws `CompletionException` that must be unwrapped manually.
3. **Cancellation support** — `.join()` cannot be cancelled; a cancelled HTTP connection still runs the blocked operation.

A second concern: repository interfaces in `object-store-domain` create a dependency on `java.util.concurrent.CompletableFuture`. While `CompletableFuture` is Java SE standard and not a framework import, it is still an async construct in what should be a pure domain module (ADR 0002). Domain aggregates (`Bucket`, `S3Object`) are pure records — repository interfaces are the only async element in the domain module.

The solution is to split repository interfaces into a dedicated `object-store-reactive-repository-application` module that:

- Uses `Mono`/`Flux` natively (no bridging, no `.join()`)
- Accepts `DataBuffer` types for streaming content (ADR 0003 boundary)
- Keeps `object-store-domain` pure: aggregates, value objects, domain events only
- Creates new `object-store-reactive-application` and `object-store-reactive-infrastructure` modules for the reactive path
- Renames `s3-api` to `s3-reactive-api-adapter` for consistency
- Adds CQRS (Command Query Responsibility Segregation): repository interfaces split into Command and Query interfaces within `object-store-reactive-repository-application`, avoiding module explosion while adding read/write separation clarity

**Key insight:** Repository interfaces contain `Mono`/`Flux`/`DataBuffer` which are application/infrastructure types, not pure domain. They belong in the application layer, hence the `-reactive-repository-application` module name. This clarifies that reactive repository interfaces are an application concern, not a domain concern.

## Decision

### Module Structure

```text
magrathea-objectstorage/
├── pom.xml
├── object-store-domain/           # PURE: aggregates, value objects (NO repositories)
├── object-store-reactive-repository-application/      # NEW: application-layer reactive repository interfaces (Mono/Flux/DataBuffer) with CQRS command/query split
├── object-store-reactive-application/            # NEW: reactive application services (no blocking)
├── object-store-reactive-infrastructure/         # NEW: reactive repository implementations
├── s3-reactive-api-adapter/         # RENAMED from s3-api
├── object-store-application/      # KEPT: classic path (unused)
├── object-store-infrastructure/   # KEPT: classic path (unused)
├── bootstrap-application/           # Updated to use reactive modules
├── persistence-context-domain/      # EMPTY placeholder
├── persistence-context-application/ # EMPTY placeholder
├── persistence-context-infrastructure/ # EMPTY placeholder
└── docs/
```

### Module Responsibilities

#### `object-store-domain` (pure — no change to aggregates/value objects)
- Remove `repository/` package entirely
- Keep: `aggregate/`, `valueobject/`, `domain/` (domain events, domain services)
- No `CompletableFuture`, no `Mono`, no `DataBuffer`
- Zero dependencies beyond Java SE

#### `object-store-reactive-repository-application` (NEW — Application Layer)
- Repository interfaces with `Mono`/`Flux`/`DataBuffer` return types, split into Command and Query per CQRS
- Exception: may depend on `reactor-core` (`Mono`, `Flux`) and `spring-core` (`DataBuffer`)
- Depends on `object-store-domain` for aggregates and value objects
- Package: `com.example.magrathea.objectstore.reactive.repository.application`
- These interfaces are **application-layer contracts** because `Mono`/`Flux`/`DataBuffer` are application/infrastructure types, not pure domain types
- Content:
  - `BucketCommandRepository` (save, delete, saveConfiguration, deleteConfiguration)
  - `BucketQueryRepository` (findById, findByName, findAll, findConfiguration)
  - `S3ObjectCommandRepository` (save, delete)
  - `S3ObjectQueryRepository` (findById, findByBucketAndKey, findByBucket, getContent)
  - `MultipartUploadCommandRepository` (save, delete)
  - `MultipartUploadQueryRepository` (findById, findByBucket, findParts)

#### `object-store-reactive-application` (NEW)
- Reactive application services — no `.join()`, no blocking
- Methods return `Mono<...>` or `Flux<...>` natively
- Services internally use both Command and Query repositories
- Depends on `object-store-reactive-repository-application`
- Package: `com.example.magrathea.reactive.application.service`
- Content:
  - `ReactiveBucketService`
  - `ReactiveObjectService`
  - `ReactiveMultipartUploadService`

#### `object-store-reactive-infrastructure` (NEW)
- Reactive repository implementations
- Each implementation class can implement BOTH the Command and Query interfaces for a given aggregate, OR be split into separate command/query implementations
- Example: `InMemoryBucketCommandRepository` + `InMemoryBucketQueryRepository`, or a single `InMemoryReactiveBucketRepository` implementing both
- Depends on `object-store-reactive-repository-application`
- Package: `com.example.magrathea.reactive.infrastructure.adapter.persistence`

#### `s3-reactive-api-adapter` (RENAMED from `s3-api`)
- All existing handler code stays; module renamed for clarity
- Depends on `object-store-reactive-application` instead of `object-store-application`
- Package base: unchanged (`com.example.magrathea.s3api.*`)

#### `object-store-application` (KEPT — unused classic path)
- Existing `BucketService`, `ObjectService`, `MultipartUploadService` remain
- No new development; kept for reference only

#### `object-store-infrastructure` (KEPT — unused classic path)
- Existing `InMemoryBucketRepository`, `InMemoryObjectRepository`, `InMemoryMultipartUploadRepository` remain
- No new development; kept for reference only

### Repository Interface Design (Reactive) — CQRS Split

```java
// BucketCommandRepository.java — write operations only
public interface BucketCommandRepository {
    Mono<Void> save(Bucket bucket);
    Mono<Void> delete(Bucket.Id id);
    Mono<Void> saveConfiguration(BucketConfiguration configuration);
    Mono<Void> deleteConfiguration(String bucketName);
    // ... all other bucket sub-configuration write methods
}
```

```java
// BucketQueryRepository.java — read operations only
public interface BucketQueryRepository {
    Mono<Optional<Bucket>> findById(Bucket.Id id);
    Mono<Optional<Bucket>> findByName(String name);
    Flux<Bucket> findAll();
    Mono<Optional<BucketConfiguration>> findConfiguration(String bucketName);
    // ... all other bucket sub-configuration read methods
}
```

```java
// S3ObjectCommandRepository.java — write operations only
public interface S3ObjectCommandRepository {
    Mono<Void> save(S3ObjectWrite object);
    Mono<Void> delete(S3Object.Id id);
}
```

```java
// S3ObjectQueryRepository.java — read operations only
public interface S3ObjectQueryRepository {
    Mono<Optional<S3Object>> findById(S3Object.Id id);
    Mono<Optional<S3Object>> findByBucketAndKey(Bucket.Id bucketId, ObjectKey key);
    Flux<S3Object> findByBucket(Bucket.Id bucketId);
    Mono<Optional<S3ObjectContent>> getContent(S3Object.Id id);
}
```

```java
// MultipartUploadCommandRepository.java — write operations only
public interface MultipartUploadCommandRepository {
    Mono<Void> save(MultipartUpload upload);
    Mono<Void> delete(MultipartUpload.Id id);
}
```

```java
// MultipartUploadQueryRepository.java — read operations only
public interface MultipartUploadQueryRepository {
    Mono<Optional<MultipartUpload>> findById(MultipartUpload.Id id);
    Flux<MultipartUpload> findByBucket(Bucket.Id bucketId);
    Flux<Part> findParts(MultipartUpload.Id uploadId);
}
```

### Application Service Design (Reactive, no blocking, CQRS-aware)

```java
// ReactiveBucketService.java — all methods return Mono/Flux, uses both command and query repos
@Service
public class ReactiveBucketService {
    private final BucketCommandRepository commandRepository;
    private final BucketQueryRepository queryRepository;

    public ReactiveBucketService(BucketCommandRepository commandRepository,
                                 BucketQueryRepository queryRepository) {
        this.commandRepository = commandRepository;
        this.queryRepository = queryRepository;
    }

    public Mono<BucketResponse> createBucket(CreateBucketCommand command) {
        var id = Bucket.Id.generate();
        var region = findRegion(command.region());
        var storageClass = findStorageClass(command.storageClass());
        var bucket = Bucket.create(id, command.name(), region, storageClass);
        return commandRepository.save(bucket)
            .map(ignored -> toResponse(bucket));
    }

    public Mono<BucketResponse> findById(String id) {
        return queryRepository.findById(Bucket.Id.of(id))
            .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty))
            .map(this::toResponse)
            .switchIfEmpty(Mono.error(
                new IllegalArgumentException("Bucket not found: " + id)));
    }

    public Flux<BucketResponse> findAll() {
        return queryRepository.findAll().map(this::toResponse);
    }

    public Mono<Void> deleteBucket(String id) {
        return commandRepository.delete(Bucket.Id.of(id));
    }
    // ... all other methods use commandRepository for writes, queryRepository for reads
}
```

### Dependency Rules

| Module | Depends On | Framework Dependencies |
|---|---|---|
| `object-store-domain` | None | None (Java SE only) |
| `object-store-reactive-repository-application` | `object-store-domain` | `reactor-core`, `spring-core` (DataBuffer) |
| `object-store-reactive-application` | `object-store-reactive-repository-application` | `spring-context` (@Service), `reactor-core` |
| `object-store-reactive-infrastructure` | `object-store-reactive-repository-application` | `reactor-core`, `spring-core` |
| `s3-reactive-api-adapter` | `object-store-reactive-application` | Spring Boot 4 WebFlux |
| `bootstrap-application` | `s3-reactive-api-adapter`, `object-store-reactive-infrastructure` | Spring Boot 4 |

### Classic Modules (kept, unused)

| Module | Dependencies | Status |
|---|---|---|
| `object-store-application` | `object-store-domain` | Kept as-is, no new development |
| `object-store-infrastructure` | `object-store-domain` | Kept as-is, no new development |

### Migration Plan

1. **Create `object-store-reactive-repository-application`** — copy repository interfaces from `object-store-domain`, convert `CompletableFuture` to `Mono`/`Flux`, add `DataBuffer` support for content operations, split each aggregate repository into Command and Query interfaces.
2. **Remove repository interfaces from `object-store-domain`** — delete `repository/` package.
3. **Create `object-store-reactive-application`** — write new reactive services that compose `Mono`/`Flux` chains without `.join()`, injecting both Command and Query repositories.
4. **Create `object-store-reactive-infrastructure`** — write new reactive repository implementations (combined or split Command/Query).
5. **Rename `s3-api` to `s3-reactive-api-adapter`** — update `pom.xml`, directory name, internal references.
6. **Update `s3-reactive-api-adapter`** — switch dependency from `object-store-application` to `object-store-reactive-application`.
7. **Update `bootstrap-application`** — scan reactive modules, wire reactive beans.
8. **Update `pom.xml`** — add new modules, remove unused classic module references (keep modules in list but mark unused).
9. **Verify** — all Cucumber tests pass with reactive chain (no `.join()` in application path).

### What is NOT created

- **No `classic-repository-domain`** — classic repository interfaces remain only as legacy code in `object-store-domain` until step 2 removes them.
- **No `classic-application`** — `object-store-application` already covers this role; no separate module needed.
- **No `classic-infrastructure`** — `object-store-infrastructure` already covers this role; no separate module needed.

## Consequences

### Positive

- **End-to-end reactive chain** — from HTTP request (`Mono<ServerResponse>`) through application service (`Mono<BucketResponse>`) to repository (`Mono<Optional<Bucket>>`), no thread blocks at any point.
- **Composable error handling** — `Mono.error(...)` and `.switchIfEmpty()` propagate naturally without `CompletionException` unwrapping.
- **Cancellation support** — Reactor operators propagate cancellation upstream; a dropped HTTP connection cancels the database operation.
- **Back-pressure** — `Flux<Bucket>` can stream results with reactive back-pressure instead of loading all into memory via `.join()`.
- **Domain purity** — `object-store-domain` has zero async constructs; repository interfaces move to a dedicated module that explicitly depends on reactive types.
- **Clean boundary** — the exception for reactor-core/spring-core dependencies is explicit and confined to one module.

### Negative

- **Module proliferation** — three new modules (`object-store-reactive-repository-application`, `object-store-reactive-application`, `object-store-reactive-infrastructure`) added to the project.
- **Migration cost** — existing `s3-api` handlers and `bootstrap-application` must be updated to wire reactive beans instead of classic ones.
- **CQRS clarity** — splitting repository interfaces into Command and Query within a single module avoids module explosion while providing clear read/write separation at the interface level.
- **Dual maintenance** — classic modules (`object-store-application`, `object-store-infrastructure`) are kept but unused; they add maintenance surface if anyone modifies them accidentally.
- **Build time increase** — more Maven modules means longer compilation and test execution.
- **Learning curve** — developers must understand the distinction between `object-store-domain` (pure) and `object-store-reactive-repository-application` (reactive-aware with CQRS).

### Risks

- **Regulatory risk**: low — classic modules are kept as-is; rollback simply re-adds repository interfaces to `object-store-domain`.
- **Performance risk**: low — reactive chains eliminate blocking, no slower than the `.join()` bridge.
- **Compatibility risk**: medium — `s3-api` handlers must be re-wired; existing Cucumber tests must verify the new reactive path.

## Notes

- ADR 0001 (multi-module per bounded context) is partially superseded: repository interfaces no longer live in `object-store-domain`.
- ADR 0004 (CompletableFuture bridge) is superseded: no bridging needed because repository interfaces use `Mono`/`Flux` directly.
- ADR 0003 (BinaryContent vs DataBuffer boundary) remains valid — `DataBuffer` appears only in `object-store-reactive-repository-application` and `object-store-reactive-infrastructure`.
- ADR 0002 (zero-dependency domain purity) is strengthened — `object-store-domain` truly has zero dependencies.
- CQRS is applied within `object-store-reactive-repository-application` only; no separate CQRS module or package structure is needed.
