# Magrathea ObjectStore

[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green)](https://spring.io/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

**Magrathea ObjectStore** is an AWS S3-compatible object store built with Spring Boot 4 WebFlux and Java 21. The public object API is the S3 REST API exposed by the pluggable `s3-reactive-api-adapter` module. In addition, `admin-api-adapter` exposes `/admin/**` endpoints for storage policy, device, and configuration management — these are internal/administrative APIs, separate from the S3 object API.

> **Current cluster status:** EP-10 is **partial**. Fixed A/B/C consensus publication/failover and bounded current-generation whole-object repair are implemented and validated for `REQ-CLUSTER-001..005`, `008..013`, and `019..026`, including the seven-point real-filesystem/gRPC interruption scope in `024`. `REQ-CLUSTER-014` and broad healing `017` remain partial; `006/007/015/016/018` remain not implemented. General chaos, broad partition tolerance, rolling upgrade, dynamic membership, periodic anti-entropy, rebalance, orphan cleanup, broader transfer semantics, and production readiness remain outside the validated scope.

**Architecture:** [ARC42 entry point](docs/arc42/arc42-template.adoc) · [C4 model](docs/c4/README.md) · [Executable requirements appendix](docs/arc42/generated/gherkin-requirements.adoc) · [Focused evidence](docs/test-report.md)

---

## System Context

<div align="center">
  <img src="docs/c4/images/SystemContext.png" alt="System Context — Magrathea ObjectStore" width="600">
</div>

*Generated from the Structurizr workspace — see `docs/c4/workspace.dsl` and [`docs/c4/README.md`](docs/c4/README.md).*

---

## Features

- **S3 data plane + Admin control plane** — the S3 REST API is the exclusive object interface; `admin-api-adapter` provides separate `/admin/**` health, status, configuration-as-code catalog, validation, capacity/quota, route-inventory, and truthful report-availability contracts
- **S3 semantic coverage** — the generated [`docs/api-coverage.md`](docs/api-coverage.md) matrix currently finds 108/111 official operations with router-handler mappings, but only **20/111** with explicit operation-linked `@implemented-and-validated` evidence; **91/111 remain ineligible for a 100% completion claim** pending implementation or stronger classification/evidence. Route presence is not completion evidence.
- **Pluggable S3 API** — auto-configured when `s3-reactive-api-adapter` is on the classpath; disabled with `s3.api.enabled=false`
- **Spring Boot 4 WebFlux** — functional RouterFunction endpoints
- **Jackson 3 XML** — `tools.jackson.dataformat:jackson-dataformat-xml` with custom WebFlux encoder
- **Pure domain** — no Spring, no JPA, no reactive types in `object-store-domain`
- **In-memory infrastructure** — reactive in-memory bucket, object, and multipart repositories
- **Testing** — JUnit, Cucumber, targeted AWS CLI compatibility; the Java 21 EP-10 shared real-process gate passes 14 scenarios / 188 steps for `001..005/019/020` (repair-only `019/020`: 4 / 80). The 2026-07-14 focused `024` gate passes 7 / 168, repair control passes 22 / 294, and data-plane regression passes 4 / 40; full S3 and cluster capability parity is not complete. **JaCoCo is the current coverage baseline** (Clover/OpenClover is optional/legacy)

---

## Architecture

| Module | Responsibility | Notes |
|---|---|---|
| `s3-reactive-api-adapter` | Pluggable AWS S3 HTTP adapter | Auto-configuration, RouterFunction, XML responses, Cucumber tests |
| `admin-api-adapter` | Internal/admin HTTP adapter | `/admin/**` JSON API for health/readiness, backend/catalog status, read-only catalogs, non-persistent validation, capacity/quota, route inventory, and report-provider availability; separate from S3 object API |
| `object-store-domain` | S3 domain model | Zero framework dependencies |
| `object-store-reactive-repository-application` | Reactive CQS repository interfaces | Bucket, object, and multipart command/query ports |
| `object-store-reactive-application` | Reactive application services and DTOs | Native Mono/Flux service APIs |
| `object-store-reactive-infrastructure` | Reactive in-memory repository implementations | No HTTP API, no S3 router |
| `object-store-reactive-repository-storage-engine-infrastructure` | Anti-Corruption Layer + adapter: Object Store → Storage Engine | Implements repository interfaces using single-node Storage Engine or bounded cluster repositories |
| `storage-engine-cluster-application` | Transport-neutral cluster application layer | Fixed-slice control/data ports, `N=3/W=2` whole-object coordination, and bounded repair coordinator/scheduler/worker; no Ratis/gRPC dependencies |
| `cluster-protocol` | Internal versioned protobuf contracts | No S3 API or domain policy |
| `cluster-control-ratis-infrastructure` | Fixed-cluster control infrastructure | Ratis voter/state machine, persisted version-2 repair lifecycle and snapshot migration, fixed bootstrap, control mTLS |
| `cluster-data-grpc-infrastructure` | Direct replica transport infrastructure | grpc-java bounded write/read/repair transfer, checksums, deadlines/cancellation, replica mTLS |
| `storage-engine-domain` | Storage Engine domain model (pure) | Policy, workflow, device, trace, manifest — zero framework dependencies |
| `storage-engine-reactive-application` | Storage Engine reactive orchestration | Ports, Chunker, ReactiveStorageOrchestrator |
| `storage-engine-reactive-infrastructure` | Storage Engine filesystem cluster backend | YAML catalogs, FileSystemStorageCluster, content address index, manifest repository, chaos decorator |
| `bootstrap-application` | Spring Boot entry point | Includes S3/admin adapters and serves generated UI/documentation assets from classpath `static/**` resources |

### Backend selection

The Object Store repository implementations are selected by profile:

| Profile | Backend | Module |
|---|---|---|
| `storage-engine` | **Default and only supported single-node product backend** | `object-store-reactive-repository-storage-engine-infrastructure` |
| `storage-engine,cluster` | Bounded fixed A/B/C consensus and whole-object backend | Storage Engine adapter plus the four cluster modules above |
| `legacy-in-memory-test` | Explicit test-only in-memory adapters; unsupported as a product backend and eligible for retirement | `object-store-reactive-infrastructure` |

The packaged JVM and native container images activate `storage-engine` by default and package the YAML storage-policy/device/disk-set catalogs under `/app/config`. Bare and packaged single-node product runs share the same Storage Engine default; blank backend selection also resolves to Storage Engine. Containers do not activate the cluster profile by default. The `cluster` profile is limited to the fixed first slice and requires explicit node/root/address/mTLS configuration; unsupported cluster operations fail rather than falling back to single-node semantics.
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

The Admin API is an internal/operator JSON API under `/admin/**`. It is **not** part of the AWS S3 object API and must not be counted in S3 semantic coverage. Its validated route inventory contains no object or bucket data-plane operations.

Current runtime mutability decision: storage policies, storage devices, and disk sets/topology are YAML-backed **configuration-as-code**. The runtime exposes read-only catalog views. Policy validation is non-persistent: it validates a submitted JSON policy shape and returns structured results without writing YAML or changing active runtime state. Backend/status and capacity/quota contracts are implemented. Operational report endpoints expose availability, but no real recovery, GC, scrub, audit, metrics, or traces provider is configured; they therefore return typed HTTP 503 not-configured responses.

| Endpoint | Purpose | Runtime mutability |
|---|---|---|
| `GET /admin/health` | Admin API health and links | Read-only |
| `GET /admin/live` | Liveness probe for the Admin API process | Read-only |
| `GET /admin/ready` | Readiness probe for configured storage-policy, storage-device, and disk-set catalogs | Read-only |
| `GET /admin/backend-status` | Selected backend, profile/property evidence, catalog sources/counts, and storage roots | Read-only |
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
| `GET /admin/buckets/{bucket}/capacity` | Read logical capacity/quota accounting | Read-only administrative accounting |
| `GET /admin/reports/{recovery\|garbage-collection\|scrub\|audit\|metrics\|traces}` | Read an operational report only when a real provider is configured | HTTP 503 `report-provider-not-configured` when absent |

### Admin UI and Product Shell

EP-7 implements a product-neutral `@magrathea/product-shell`, an explicit Product Extension contract, the Object Storage extension/application, an independent example product, and a reusable product template. The validated UI covers health/readiness/backend status, read-only policy/device/disk-set catalogs, non-persistent policy validation, capacity/quota, truthful unavailable operational-report states, and an optional S3 HeadObject diagnostic through the S3 Data Plane.

The bounded `REQ-ADMIN-001..031` scope is implemented and validated: **72/72 Vitest** tests and **39/39 Playwright/axe** tests across Chromium widths **360/768/1440**, plus deterministic extension removal, reproducible dual-product packaging, canonical Docker frontend-packaging validation, and **18 Admin API Cucumber scenarios / 132 steps**. EP-7 overall remains partial under [`PLAN.md`](PLAN.md): credential/tenant administration and real recovery, GC, scrub, audit, metrics, and traces providers are absent. Without providers, those routes intentionally return `503 report-provider-not-configured`; this is not real operational data.

### Container build, native image and generated static assets

Generated bootstrap static resources are not expected to be committed or copied from the host into the image. `.dockerignore` excludes generated `bootstrap-application/src/main/resources/static/**` docs, assets, and entry files. The JVM `Dockerfile` installs the frontend/documentation toolchains in the builder stage, checks/regenerates the Gherkin appendix, regenerates ARC42/ADR/test-report JSON, copies source-controlled documentation images, builds `magrathea-ui`, and packages those regenerated static assets into the Spring Boot JAR without Maven fail-never mode.

The canonical JVM container path is:

```bash
# deterministic dual-product + documentation packaging gate
docker build --network=host --target frontend-packaging-validation \
  -t magrathea-frontend-packaging-validation:local .

docker build --network=host -f Dockerfile -t magrathea-objectstorage:jvm .
docker run --rm --network=host magrathea-objectstorage:jvm

# Docker-required non-root, persistent-volume, SIGTERM/replacement requirement
mvn -Pdocker-cucumber-tests -pl s3-reactive-api-adapter -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=PhaseEp5JvmContainerReplacementRequirementsCucumberIT test
```

The replacement gate is intentionally opt-in for ordinary Maven runs and mandatory before release publication. Its validated procedure is documented in [`docs/runbooks/container-replacement.md`](docs/runbooks/container-replacement.md).

The JVM runtime image uses public ECR mirrored Maven/Temurin bases, runs as the non-root `magrathea` user with writable `/app/data`, activates the `storage-engine` profile with packaged YAML catalogs, exposes ports 8080/8081, and has an Admin API healthcheck. EP-6 enforces the documented `0.1.x` single-node object, multipart, timeout, concurrency, connection, and request-rate safety bounds; mandatory 45-second CI load and weekly/manual 15-minute soak profiles validate bounded operation under `-Xmx256m`. These observations are not production sizing or competitive benchmarks; see [`docs/runbooks/performance-capacity.md`](docs/runbooks/performance-capacity.md). Graceful shutdown is enabled for the bootstrap application. The 2026-07-10 validation used `--network=host` because the local Docker sandbox cannot create bridge networking; it passed Admin health, `/admin/live`, `/admin/ready` with ready catalog status, S3 ListBuckets XML, bucket/object PUT/GET, selected-backend log verification, SIGTERM committed-object recovery, SIGTERM draining of active streaming PutObject, multipart UploadPart, and CompleteMultipartUpload requests, including concurrent PutObjects, a bounded mixed read/write load, cancelled-and-aborted part cleanup, abort-wins multipart completion overlap, and restart durability checks, offline storage-root backup/restore, declared single-node DR RTO/RPO rehearsal, object-manifest, multipart-session, bucket-registry, object-configuration, object-reference, and object-ACL schema-version compatibility checks, shipped SLO/alerting rule coverage, opt-in live Prometheus-to-Alertmanager webhook delivery, and generated-password log checks. The validated graceful shutdown, offline backup, disaster recovery, schema migration, and alert response procedures are documented in `docs/runbooks/graceful-shutdown.md`, `docs/runbooks/backup-restore.md`, `docs/runbooks/disaster-recovery.md`, `docs/runbooks/schema-migration.md`, and `docs/runbooks/slo-alerts.md`.

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
java -jar bootstrap-application/target/bootstrap-application-0.1.0-SNAPSHOT.jar

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
| 3b | EP-7 Admin API Cucumber | `mvn -B -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PhaseEp7AdminApiRequirementsCucumberTest test` (18 scenarios / 132 steps passed) |
| 3c | Frontend unit/component/accessibility | `cd magrathea-ui && npm test` (72/72 passed) |
| 3d | Real-browser Playwright/axe | `cd magrathea-ui && npm run test:e2e:ci` (39/39 at 360/768/1440 passed) |
| 4 | JaCoCo coverage (current baseline) | `mvn verify` (JaCoCo runs automatically with the default lifecycle) |
| 4b | Clover coverage (optional/legacy) | `mvn -Pcoverage clover:setup test clover:aggregate clover:clover` |
| 5 | AWS CLI compatibility | `bash test-aws-cli.sh` |
| 6 | AWS CLI via Maven profile | `mvn verify -Paws-cli-tests` (auto-starts/stops server) |

Consolidated Markdown report: [`docs/test-report.md`](docs/test-report.md)

The EP-10 semantic claim comes from its focused shared real-process WebTestClient/AWS CLI gates and focused Ratis/gRPC/cross-module runners documented there. An ordinary root `mvn test` pass is supporting integration evidence only and must not be substituted for the 14-scenario / 188-step shared result, 4-scenario / 80-step repair-only result, or 22-scenario / 210-step repair-control result, nor used to upgrade later EP-10 scope. The current dirty-working-tree root pass is likewise not acceptance supply-chain evidence: `REQ-SUPPLY-001` remains implemented-not-e2e-validated until a clean-revision application SBOM/license/image packet covers all four cluster modules.

### Requirements appendix generation

The ARC42 Gherkin requirements appendix is generated deterministically from all shared `.feature` files under `s3-reactive-api-adapter/src/test/features/requirements` and `s3-reactive-api-adapter/src/test/features/specs`.

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
java -jar bootstrap-application/target/bootstrap-application-0.1.0-SNAPSHOT.jar
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
> API coverage is generated in [`docs/api-coverage.md`](docs/api-coverage.md) from the canonical 111-operation inventory, router mappings, and executable requirement tags. The current conservative baseline is **108 mapped**, **20 implemented-and-validated with explicit operation-linked evidence**, and **91 not yet eligible for a 100% completion claim**.
> AWS CLI parity has improved for the canonical object CRUD subset (put default headers, get content, head, list v1/v2, delete/idempotent delete, `STANDARD` storage class via object attributes, and slash-containing object keys through catch-all routes/key normalization), but it is **not complete** for all S3 scenarios.
> See [`docs/test-report.md`](docs/test-report.md) for the classification matrix and [`PLAN.md`](PLAN.md) → *S3 API Semantic Completion Plan* for the phased roadmap.

Phases A–F route mapping is recorded in [ADR 0012](docs/adr/0012-phase-f-advanced-s3-operations.md). Semantic implementation completion is tracked separately in the correction plan.

---

## Documentation

| Artifact | Location |
|---|---|
| Implementation plan | [`PLAN.md`](PLAN.md) |
| Generated S3 API semantic coverage | [`docs/api-coverage.md`](docs/api-coverage.md) |
| Positioning & competitive analysis | [`docs/positioning.md`](docs/positioning.md) |
| Public roadmap | [`docs/roadmap.md`](docs/roadmap.md) |
| Release policy | [`docs/release-policy.md`](docs/release-policy.md) |
| EP-7 Admin UI implemented scope/backlog | [`docs/admin-ui-plan.md`](docs/admin-ui-plan.md) |
| JVM container replacement runbook | [`docs/runbooks/container-replacement.md`](docs/runbooks/container-replacement.md) |
| Performance and capacity validation | [`docs/runbooks/performance-capacity.md`](docs/runbooks/performance-capacity.md) |
| ARC42 architecture docs | [`docs/arc42/`](docs/arc42/) |
| ADRs | [`docs/adr/`](docs/adr/) |
| C4 diagrams | [`docs/c4/`](docs/c4/) |
| C4 workflow | [`docs/c4/README.md`](docs/c4/README.md) |

---

## License

[MIT](LICENSE)
