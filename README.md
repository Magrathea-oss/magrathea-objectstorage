# Magrathea ObjectStore

[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green)](https://spring.io/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

**Magrathea ObjectStore** is an AWS S3-compatible object store built with Spring Boot 4 WebFlux and Java 21. The public object API is the S3 REST API exposed by the pluggable `s3-reactive-api-adapter` module. In addition, `admin-api-adapter` exposes `/admin/**` endpoints for storage policy, device, and configuration management — these are internal/administrative APIs, separate from the S3 object API.

---

## System Context

<div align="center">
  <img src="docs/c4/images/SystemContext.png" alt="System Context — Magrathea ObjectStore" width="600">
</div>

*Generated from the Structurizr workspace — see `docs/c4/workspace.dsl` and [`docs/c4/README.md`](docs/c4/README.md).*

---

## Features

- **S3 object API + admin API** — the S3 REST API is the public object interface; `admin-api-adapter` provides `/admin/**` configuration-as-code catalog APIs separate from the S3 surface
- **Route inventory** — 111 Amazon S3 API action routes are mapped; ⚠️ **this is a route/surface inventory, not a semantic implementation metric** — many operations are stubbed or return nominal/placeholder responses; see [`docs/test-report.md`](docs/test-report.md) and [`PLAN.md`](PLAN.md) for the semantic coverage classification
- **Pluggable S3 API** — auto-configured when `s3-reactive-api-adapter` is on the classpath; disabled with `s3.api.enabled=false`
- **Spring Boot 4 WebFlux** — functional RouterFunction endpoints
- **Jackson 3 XML** — `tools.jackson.dataformat:jackson-dataformat-xml` with custom WebFlux encoder
- **Pure domain** — no Spring, no JPA, no reactive types in `object-store-domain`
- **In-memory infrastructure** — reactive in-memory bucket, object, and multipart repositories
- **Testing** — JUnit, Cucumber, targeted AWS CLI compatibility; the first AWS CLI object CRUD increment passes, but full S3 scenario parity is not complete; **JaCoCo is the current coverage baseline** (Clover/OpenClover is optional/legacy)

---

## Architecture

| Module | Responsibility | Notes |
|---|---|---|
| `s3-reactive-api-adapter` | Pluggable AWS S3 HTTP adapter | Auto-configuration, RouterFunction, XML responses, Cucumber tests |
| `admin-api-adapter` | Internal/admin HTTP adapter | `/admin/**` JSON API for read-only storage policy/device/disk-set catalogs and non-persistent policy validation; separate from S3 object API |
| `object-store-domain` | S3 domain model | Zero framework dependencies |
| `object-store-reactive-repository-application` | Reactive CQS repository interfaces | Bucket, object, and multipart command/query ports |
| `object-store-reactive-application` | Reactive application services and DTOs | Native Mono/Flux service APIs |
| `object-store-reactive-infrastructure` | Reactive in-memory repository implementations | No HTTP API, no S3 router |
| `object-store-reactive-repository-storage-engine-infrastructure` | Anti-Corruption Layer + adapter: Object Store → Storage Engine | Implements repository interfaces using Storage Engine backend |
| `storage-engine-domain` | Storage Engine domain model (pure) | Policy, workflow, device, trace, manifest — zero framework dependencies |
| `storage-engine-reactive-application` | Storage Engine reactive orchestration | Ports, Chunker, ReactiveStorageOrchestrator |
| `storage-engine-reactive-infrastructure` | Storage Engine filesystem cluster backend | YAML catalogs, FileSystemStorageCluster, content address index, manifest repository, chaos decorator |
| `bootstrap-application` | Spring Boot entry point | Includes S3/admin adapters and serves generated UI/documentation assets from classpath `static/**` resources |

### Backend selection

Two implementations of the Object Store repository interfaces coexist:

| Profile | Backend | Module |
|---|---|---|
| `single-node` (default for unpackaged/dev runs) | In-memory repositories | `object-store-reactive-infrastructure` |
| `storage-engine` | Storage Engine filesystem cluster | `object-store-reactive-repository-storage-engine-infrastructure` |

The packaged JVM and native container images activate `storage-engine` by default even for single-node deployments, and package the YAML storage-policy/device/disk-set catalogs under `/app/config`. The bare Spring Boot default remains `single-node` for development/test compatibility unless a deployment explicitly selects `storage-engine`.
The ACL translation layer lives in `object-store-reactive-repository-storage-engine-infrastructure`,
which translates Object Store concepts into Storage Engine commands and delegates persistence
to the Storage Engine bounded context.

### Storage Engine Architecture

The Storage Engine bounded context is a separate domain model for object persistence,
independent of the S3 API domain. It defines:

- **StoragePolicy / EffectiveStoragePolicy** — base policy vs request-resolved policy
- **VirtualDevice** — BucketDevice (non-dedup) and DedupDevice (content-addressed)
- **DedupNamespace** — GlobalDedupNamespace and BucketDedupNamespace
- **WorkflowCompatibilityKey / DeviceConfigurationHash** — deterministic device identity
- **StepPlan / StepExecutionTrace** — fixed 6-step pipeline with EXECUTED/SKIPPED/BYPASSED
- **ObjectManifest** — persisted chunk layout, separate from StoredObject aggregate
- **CompleteUpload** — common phase for PutObject and MultipartUpload
- **Chaos engineering** — alter step after checksum, before verify, with FaultInjectingStorageCluster

The pipeline order is fixed: DEDUP → COMPRESS → CRYPT → ERASURE_CODING → REPLICATION → STORE.
EC differentiates DedupDevice; replication does not.

### `MINIO_STANDARD` policy semantics

`MINIO_STANDARD` is the first executable Storage Engine policy use case. Its S3-facing storage class is `STANDARD` (`storageClassId: STANDARD`). Phase 5 semantics are explicit and test-backed: deduplication disabled, erasure-coding planning enabled as `4 data / 2 parity`, replication factor `1`, compression disabled, and encryption disabled by default. The current evidence verifies YAML catalog loading, device selection, and deterministic persistence planning; it does **not** yet claim end-to-end storage-engine read/write wiring or verified physical EC shard placement.

### Admin API (configuration-as-code)

The Admin API is an internal/operator JSON API under `/admin/**`. It is **not** part of the AWS S3 object API and must not be counted in S3 semantic coverage.

Current runtime mutability decision: storage policies, storage devices, and disk sets/topology are YAML-backed **configuration-as-code**. The runtime exposes read-only catalog views. Policy validation is non-persistent: it validates a submitted JSON policy shape and returns structured results without writing YAML or changing active runtime state.

| Endpoint | Purpose | Runtime mutability |
|---|---|---|
| `GET /admin/health` | Admin API health and links | Read-only |
| `GET /admin/live` | Liveness probe for the Admin API process | Read-only |
| `GET /admin/ready` | Readiness probe for configured storage-policy, storage-device, and disk-set catalogs | Read-only |
| `GET /admin/storage-policies` | List configured storage policies | Read-only YAML catalog |
| `GET /admin/storage-policies/{id}` | Read one storage policy | Read-only YAML catalog |
| `POST /admin/storage-policies/validate` | Validate a policy payload | Non-persistent validation only |
| `POST /admin/storage-policies` | Policy creation request | Rejected at runtime (`405 Method Not Allowed`) |
| `PUT /admin/storage-policies/{id}` | Policy update request | Rejected at runtime (`405 Method Not Allowed`) |
| `DELETE /admin/storage-policies/{id}` | Policy delete request | Rejected at runtime (`405 Method Not Allowed`) |
| `GET /admin/storage-devices` | List configured storage devices | Read-only YAML catalog |
| `GET /admin/storage-devices/{id}` | Read one storage device | Read-only YAML catalog |
| `GET /admin/disk-sets` | List configured disk sets/topology groups | Read-only YAML catalog |
| `GET /admin/disk-sets/{id}` | Read one disk set/topology group | Read-only YAML catalog |

### Admin UI plan

Planned Vue screens are policy list/detail/validation report, device list/detail, disk-set/topology overview/detail, and backend/status dashboard. Frontend implementation requires an appropriate frontend workflow/agent before changing `magrathea-ui`; the current Java workflow only documents the plan and backend contracts.

### Container build, native image and generated static assets

Generated bootstrap static resources are not expected to be committed or copied from the host into the image. `.dockerignore` excludes generated `bootstrap-application/src/main/resources/static/**` docs, assets, and entry files. The JVM `Dockerfile` installs the frontend/documentation toolchains in the builder stage, checks/regenerates the Gherkin appendix, regenerates ARC42/ADR/test-report JSON, copies source-controlled documentation images, builds `magrathea-ui`, and packages those regenerated static assets into the Spring Boot JAR without Maven fail-never mode.

The canonical JVM container path is:

```bash
docker build --network=host -f Dockerfile -t magrathea-objectstorage:jvm .
docker run --rm --network=host magrathea-objectstorage:jvm
```

The JVM runtime image uses public ECR mirrored Maven/Temurin bases, runs as the non-root `magrathea` user with writable `/app/data`, activates the `storage-engine` profile with packaged YAML catalogs, exposes ports 8080/8081, and has an Admin API healthcheck. Graceful shutdown is enabled for the bootstrap application. The 2026-07-10 validation used `--network=host` because the local Docker sandbox cannot create bridge networking; it passed Admin health, `/admin/live`, `/admin/ready` with ready catalog status, S3 ListBuckets XML, bucket/object PUT/GET, selected-backend log verification, SIGTERM committed-object recovery, SIGTERM draining of active streaming PutObject, multipart UploadPart, and CompleteMultipartUpload requests, including two concurrent PutObjects, cancelled-and-aborted part cleanup, abort-wins multipart completion overlap, and restart durability checks, offline storage-root backup/restore, declared single-node DR RTO/RPO rehearsal, object-manifest schema-version compatibility checks, shipped SLO/alerting rule coverage, and generated-password log checks. The validated graceful shutdown, offline backup, disaster recovery, schema migration, and alert response procedures are documented in `docs/runbooks/graceful-shutdown.md`, `docs/runbooks/backup-restore.md`, `docs/runbooks/disaster-recovery.md`, `docs/runbooks/schema-migration.md`, and `docs/runbooks/slo-alerts.md`.

A native-image path is also available for JVM-free deployment:

```bash
# host build; Spring Boot 4 native runtime requires GraalVM 25 native-image on PATH
mvn -Pnative -pl bootstrap-application -am -DskipTests native:compile
./bootstrap-application/target/magrathea-objectstorage

# optimized Alpine runtime image; native build happens in the Docker builder stage
docker build -f Dockerfile.native -t magrathea-objectstorage:native .
docker run --rm -p 8080:8080 -p 8081:8081 magrathea-objectstorage:native
```

`Dockerfile.native` keeps the same Docker-driven documentation/frontend regeneration gates, then compiles a GraalVM native executable with the `native,native-musl` Maven profiles and runs it from Alpine without requiring Java in the final image. The native runtime also activates `storage-engine` and uses packaged YAML catalogs. Use a GraalVM 25 native-image toolchain for Spring Boot 4 native builds; older GraalVM 21 native images can fail during link or produce a binary that Spring Boot rejects at startup. The native Docker slice is validated by Admin API health/readiness plus S3 ListBuckets XML/JSON and bucket/object PUT/GET smoke checks; the regular `Dockerfile` is now validated as the canonical JVM image while native packaging remains the JVM-free distribution path.

### S3 API activation

```properties
# default: enabled when s3-reactive-api-adapter is on the classpath
s3.api.enabled=true

# disable S3 routes even with s3-reactive-api-adapter present
s3.api.enabled=false
```

`S3ApiConfig` and `JacksonXmlCodecConfig` are loaded through:

```text
s3-reactive-api-adapter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## Quick Start

```bash
mvn clean test
mvn -pl bootstrap-application -am package -DskipTests
java -jar bootstrap-application/target/bootstrap-application-1.0.0-SNAPSHOT.jar

# optional JVM-free native executable
mvn -Pnative -pl bootstrap-application -am -DskipTests native:compile
./bootstrap-application/target/magrathea-objectstorage
```

In another shell:

```bash
aws --endpoint-url http://localhost:8080 s3api list-buckets
aws --endpoint-url http://localhost:8080 s3api create-bucket --bucket test-bucket
printf 'Hello\n' > /tmp/hello.txt
aws --endpoint-url http://localhost:8080 s3api put-object --bucket test-bucket --key hello.txt --body /tmp/hello.txt
aws --endpoint-url http://localhost:8080 s3api get-object --bucket test-bucket --key hello.txt /tmp/out.txt
```

---

## Testing

| Level | Type | Command |
|---|---|---|
| 1 | All unit + integration tests | `mvn test` |
| 2 | Domain JUnit only | `mvn test -pl object-store-domain` |
| 3 | S3 API Cucumber only | `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` (latest evidence: 248 tests, 0 failures/errors/skips) |
| 3b | Admin API adapter tests | `mvn -B -pl admin-api-adapter -am test` |
| 4 | JaCoCo coverage (current baseline) | `mvn verify` (JaCoCo runs automatically with the default lifecycle) |
| 4b | Clover coverage (optional/legacy) | `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` |
| 5 | AWS CLI compatibility | `bash test-aws-cli.sh` |
| 6 | AWS CLI via Maven profile | `mvn verify -Paws-cli-tests` (auto-starts/stops server) |

Consolidated Markdown report: [`docs/test-report.md`](docs/test-report.md)

### Requirements appendix generation

The ARC42 Gherkin requirements appendix is generated deterministically from the shared requirement feature files under `s3-reactive-api-adapter/src/test/features/requirements`.

```bash
python3 scripts/generate-gherkin-requirements-appendix.py
python3 scripts/generate-gherkin-requirements-appendix.py --check
```

The generated source is `docs/arc42/generated/gherkin-requirements.adoc` and is included from the ARC42 source docs. Do not commit regenerated static web assets for this step.

### Coverage

**JaCoCo** is the current coverage baseline and runs automatically with the standard Maven lifecycle (`mvn verify`). Reports are generated under `target/site/jacoco/`.

Clover/OpenClover is optional/legacy. A pre-commit hook exists that runs the Clover profile:

```bash
# Normal commit — Clover coverage auto-generates (legacy hook)
 git commit -m "msg"

# Skip Clover (recommended; JaCoCo runs with mvn verify)
 git commit --no-verify -m "msg"
```

Or use the helper script:

```bash
bash scripts/commit-with-coverage.sh -m "commit message"
```

The Clover hook runs `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` and stages
`target/site/clover/clover.xml` + `docs/test-report.md`. This hook is **optional/legacy**; JaCoCo is authoritative.

AWS CLI tests require:

- AWS CLI installed
- Port 8080 free (the profile starts/stops the server automatically)
- Optional endpoint override: `ENDPOINT_URL=http://host:port bash test-aws-cli.sh`

#### Workflow

```bash
# Manual: start server in one shell, run tests in another
java -jar bootstrap-application/target/bootstrap-application-1.0.0-SNAPSHOT.jar
bash test-aws-cli.sh

# Automatic via Maven (starts server, runs tests, stops server):
mvn verify -Paws-cli-tests
```

The Maven profile (`aws-cli-tests`):
1. **pre-integration-test** — starts the JAR on port 8080
2. **integration-test** — runs `test-aws-cli.sh`
3. **post-integration-test** — stops the server

Use `mvn verify -Paws-cli-tests` for a fully automated AWS CLI compatibility run.

---

## S3 API Roadmap

The implementation plan tracks all Amazon S3 actions from:

<https://docs.aws.amazon.com/AmazonS3/latest/API/API_Operations.md>

> ⚠️ **Coverage reporting has been reclassified.** The `111/111` figure reflects a route/surface inventory only — it means 111 S3 API action routes are mapped. It does **not** mean 111 operations are semantically implemented, stateful, or AWS CLI compatible.
>
> API coverage must now be reported by semantic status: **Mapped / Stubbed / Stateful / AWS CLI compatible / Storage-engine compatible / Semantically S3-compatible**.
> AWS CLI parity has improved for the canonical object CRUD subset (put default headers, get content, head, list v1/v2, delete/idempotent delete, `STANDARD` storage class via object attributes, and slash-containing object keys through catch-all routes/key normalization), but it is **not complete** for all S3 scenarios.
> See [`docs/test-report.md`](docs/test-report.md) for the classification matrix and [`PLAN.md`](PLAN.md) → *S3 API Semantic Completion Plan* for the phased roadmap.

Phases A–F route mapping is recorded in [ADR 0012](docs/adr/0012-phase-f-advanced-s3-operations.md). Semantic implementation completion is tracked separately in the correction plan.

---

## Documentation

| Artifact | Location |
|---|---|
| Implementation plan | [`PLAN.md`](PLAN.md) |
| Positioning & competitive analysis | [`docs/positioning.md`](docs/positioning.md) |
| Public roadmap | [`docs/roadmap.md`](docs/roadmap.md) |
| ARC42 architecture docs | [`docs/arc42/`](docs/arc42/) |
| ADRs | [`docs/adr/`](docs/adr/) |
| C4 diagrams | [`docs/c4/`](docs/c4/) |
| C4 workflow | [`docs/c4/README.md`](docs/c4/README.md) |

---

## License

[MIT](LICENSE)
