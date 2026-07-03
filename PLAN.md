# Magrathea ObjectStore — Correction Plan

> **Planning status:** This document is a correction and implementation plan. It is **not** an implementation result, and unchecked items must not be described elsewhere as already delivered.

Two phase numbering schemes exist in this plan and are kept deliberately distinct:

- **PA-1 .. PA-6** — Post-Audit Production Readiness phases (upload reliability, filesystem reliability, reactive pipeline, observability, S3 semantics, distributed readiness).
- **CC-0 .. CC-10** — Course Correction phases from ADR 0016 (storage-engine configuration, MINIO_STANDARD, backend wiring, admin API, Cucumber parity, quality gates).
- **EP-0 .. EP-9** — Enterprise Production Readiness phases (new; see the dedicated section below).

## Current Baseline Evidence (2026-07-02)

This is the **single authoritative evidence table** for current test counts. Older counts that previously appeared inline in this plan (827, 833, 883, 902 tests; "64 scenarios") were snapshots of earlier runs and are superseded; do not cite them as latest.

| Evidence item | Result (2026-07-02) |
|---|---|
| Full test gate | `mvn clean test` in Podman `magrathea-build:fedora42` (JDK 21, Maven 3.9.9, AWS CLI 2.34.29): **BUILD SUCCESS, 13 modules, 942 tests / 0 failures / 0 errors / 5 skipped** |
| Gherkin requirements appendix | Fresh: `python3 scripts/generate-gherkin-requirements-appendix.py --check` OK — **66 scenarios from 6 feature files** |
| PA-1 upload runners | `Phase1UploadStorageEngineCucumberTest` + `Phase1UploadStorageEngineAwsCliCucumberTest`: **28 scenarios discovered / 0 failures per class** (non-selected scenarios excluded by tag filter) |
| PA-2 AWS CLI same-key stress | REQ-FS-006 same-key stress re-validation: **6/6 fresh-JVM runs passed** |
| Coverage | JaCoCo is the coverage baseline; current per-module numbers live in [`docs/test-report.md`](docs/test-report.md) — not embedded here to avoid staleness |

## Post-Audit Production Readiness Plan (PA-1 .. PA-6)

The post-audit baseline is that Magrathea ObjectStore remains a prototype with an advanced architecture, not a production-ready object store. Declared PA-1/PA-2/PA-5 scopes are implemented and validated; restart-safety for the remaining metadata families, streaming completion, external observability, distributed readiness, and broader S3 semantics remain open and are tracked in the Enterprise Production Readiness Plan below.

### PA-1 — Reliable Upload and Restart Safety ✅ Implemented and validated for the declared Phase 1 scope

| Field | Plan / Result |
|---|---|
| Focus | Make the `PutObject` → storage-engine → `GetObject` path durable, restart-safe, and bounded in memory. |
| Current status | `@implemented-and-validated` for all remaining in-scope requirements REQ-UPLOAD-001/002/004/005/006. REQ-UPLOAD-001/002 are closed under the 2026-07-02 validation-mode decision: bootstrap JUnit integration tests are formally accepted as the sole required runtime validation mode (full process stop/start against the same storage-engine filesystem root cannot be driven by in-process WebTestClient glue or AWS CLI without duplicating the bootstrap harness); rationale is documented in `phase-1-upload-storage-engine.feature`. REQ-UPLOAD-004 was independently runtime-validated in both `@webclient` and `@awscli` modes (two identical runs, see Baseline Evidence table). |
| Validation evidence | Bootstrap: `StorageEngineRestartSafetyTest` (REQ-UPLOAD-001), `StorageEngineHttpReadAfterWriteTest` (REQ-UPLOAD-002/005), `StorageEngineUploadAtomicityTest` (REQ-UPLOAD-004), `StorageEngineIntegrityDetectionTest` (REQ-UPLOAD-006). Cucumber: Phase 1 WebTestClient + AWS CLI runners per the Baseline Evidence table. |
| Acceptance gates | **Met for the declared Phase 1 scope.** REQ-UPLOAD-003 was intentionally removed from executable scope (not a failure): its Cucumber scenario and bootstrap JUnit test imposed obsolete non-dedup multi-chunk persistence that the current architecture intentionally does not provide, and Phase 1 runners no longer select it. All remaining REQ-UPLOAD-001/002/004/005/006 scenarios carry `@implemented-and-validated`; the feature-level `@partial` tag was removed. |

Completed scope (condensed): durable object metadata and key→manifest mapping with recovery path; restart-safe PUT → restart → GET; streaming upload with bounded buffers; Phase 1 WebTestClient and AWS CLI runner glue on the shared feature; `fixtures/upload/large-object.bin` (deterministic 256 MiB); failed-upload atomicity; chunk-corruption detection on read incl. `@awscli` coverage.

### PA-2 — Filesystem Reliability ✅ Implemented and validated for declared Phase 2 scope

| Field | Plan / Result |
|---|---|
| Focus | Make filesystem-backed chunks, manifests, and metadata safe against partial writes, corruption, and crashes. |
| Current status | `@implemented-and-validated` for the declared scope: atomic chunk/manifest publication (temp file + fsync + rename), SHA-256 sidecar/trailer checksums with read-time verification, `FileSystemRecoveryScanner` with reporting/quarantine/idempotence, S3 XML integrity-error mapping, disabled-by-default write fault injection, storage-class defaulting to `STANDARD`. |
| Validation evidence | REQ-FS-001..006 WebTestClient-required scope passed; dedicated storage-engine AWS CLI Phase 2 validation 4/4 (REQ-FS-003, REQ-FS-004, REQ-FS-006 different keys, REQ-FS-006 same key); same-key stress 6/6 after a real torn-reference defect was found and fixed. Full-gate numbers: see Baseline Evidence table. |
| Acceptance gates | Met for the declared Phase 2 scope. This does **not** claim distributed readiness, broader S3 semantic completion, or physical erasure-coding placement. |

### PA-3 — Reactive Pipeline Refactor ⚠️ Implemented, not e2e validated

| Field | Plan / Result |
|---|---|
| Focus | Replace the monolithic orchestrator flow with a staged reactive read/write pipeline. |
| Current status | `@implemented-not-e2e-validated`. `StorageStage`/`StorageContext`/`StorageEvent`/`StoragePipelineExecutor` implemented; `ReactiveStorageOrchestrator` refactored into staged write/read pipelines with bounded demand, deterministic failure propagation, cancellation cleanup, and stage event publication. Unit/application validated (REQ-PIPELINE-001..006). |
| Validation evidence | Feature: `phase-3-reactive-pipeline.feature`. `Phase3ReactivePipelineCucumberTest` runner exists but all `@phase-3 @webclient` scenarios carry `@not-implemented` and are excluded. `storage-engine-reactive-application` module gate 159/159. REQ-PIPELINE-007/008 (single-pass PutObject tee; incremental dedup windows) are enforced by `ReactiveUploadStreamingArchitectureTest`. |
| Remaining gap | Expose pipeline-event observability (stage transitions, event emission, backpressure counters) through an external HTTP/metrics surface, then add step implementations and remove `@not-implemented`. Read-path streaming completion is tracked as EP-3. |

### PA-4 — Observability ⚠️ Implemented and bootstrap-validated; no Cucumber e2e runner exists

| Field | Plan / Result |
|---|---|
| Focus | Make storage behavior visible without coupling core logic to one metrics/tracing backend. |
| Current status | `@implemented-not-e2e-validated` for REQ-OBS-001..006. Composite `StorageEventPublisher`, Micrometer metrics adapter (bytes, chunks, failures, latency, dedup hit/miss, recovery), OpenTelemetry tracing adapter, recovery/corruption operational logging, and a redaction policy banning object bodies and user metadata from telemetry are implemented. |
| Validation evidence | Bootstrap JUnit: `Phase4ObservabilityIntegrationTest` (4), `Phase4ObservabilityFaultInjectionIntegrationTest` (1); infrastructure: `StorageObservabilityAdaptersTest` (2), `FileSystemRecoveryScannerObservabilityTest` (1). |
| Remaining gap | No Cucumber e2e runner executes `phase-4-observability.feature`; no Phase 4 AWS CLI runner exists; REQ-OBS-003 dedup/recovery metrics remain unit-only validated. |

### PA-5 — S3 Semantic Compatibility ✅ Implemented and WebTestClient-validated for declared Phase 5 scope

| Field | Plan / Result |
|---|---|
| Focus | Move beyond mapped endpoints and protocol smoke tests toward semantically meaningful S3 behavior. |
| Current status | `@implemented-and-validated` for 24 WebTestClient scenarios and 12 AWS CLI scenarios: ETag format/consistency (REQ-S3-001), multipart ETag semantics (REQ-S3-002-A/B), byte-range GET incl. 416 (REQ-S3-003), conditional requests (REQ-S3-004), CopyObject ETag (REQ-S3-005), object tagging lifecycle (REQ-S3-006), explicit unsupported/config-only classification for versioning/object lock/lifecycle (REQ-S3-007). `@implemented-not-e2e-validated` for REQ-S3-002-C multipart restart durability (validated via same-process filesystem inspection, not a full Spring restart). |
| Validation evidence | `Phase5S3SemanticCompatibilityRequirementsCucumberTest` 25/25; `Phase5S3SemanticCompatibilityAwsCliCucumberTest` 12 `@awscli-required` examples executed, non-required filtered. Feature: `phase-5-s3-semantic-compatibility.feature`. |
| Remaining gaps | REQ-S3-002-C full-restart e2e validation; versioning/delete markers/object-lock/lifecycle/replication enforcement remains intentionally unsupported or config-only. |

### PA-6 — Distributed Readiness ⚠️ Modeled and unit-validated, not e2e/distributed-production-ready

| Field | Plan / Result |
|---|---|
| Focus | Design and verify behavior needed before claiming distributed storage readiness. |
| Current status | `@implemented-not-e2e-validated` for the modeled domain scope: deterministic failure-domain placement, health-aware membership decisions, quorum decisions, anti-entropy/healing plans, rebalance plans, readiness classification (REQ-DIST-001..006) — all pure domain under `storage-engine-domain/.../distributed`, unit validated (module gate 164/164). |
| Remaining gaps | No real networked membership, replication execution, healing/rebalance job runners, durable multi-node manifest publication, or multi-node e2e validation. Distributed production readiness is not claimed. The single-node-HA vs distributed decision is EP-8. |

## Gherkin Requirements and ARC42 Appendix

Gherkin feature files are executable requirements. They must describe business/system behavior and storage invariants, not anemic status-code checks. S3 client behavior is specified and validated only through S3-compatible APIs using shared WebTestClient/AWS CLI features; storage-engine requirements are internal/operational abilities unless they describe admin-only concepts with no S3 API equivalent (policies, devices, disk sets/topology, backend status, validation/reporting), which may be exposed through the Admin API/UI. No public storage-engine endpoints for object semantics.

### Authoring Rules (summary)

- `Business Need` for externally promised S3 behavior; `Ability` for storage-engine internals; either for admin-only capabilities depending on user visibility.
- Status tags per AGENTS.md: `@implemented-and-validated`, `@implemented-not-e2e-validated`, `@partial`, `@config-only`, `@placeholder`, `@absent`, `@not-implemented`; smoke-only checks tagged `@protocol-smoke`.
- Every production requirement scenario carries a requirement ID, `@functional-requirement` / `@non-functional-requirement` classification, quality-attribute tags, and an explicit validation mode.
- Admin-only features use `@admin-api`, `@storage-policy`, `@storage-device`, `@disk-set`, `@backend-status` — not `@s3-api`.
- WebTestClient and AWS CLI validate the same shared feature text; runner differences live in glue/config/tags only.

### Generated Requirements Appendix — status

The appendix generator, freshness gate, ARC42 linkage, and Docker build integration are **done**: `scripts/generate-gherkin-requirements-appendix.py` writes `docs/arc42/generated/gherkin-requirements.adoc`; the Dockerfile builder stage runs `--check` (failing on staleness) and deterministically regenerates the appendix before docs/frontend asset conversion; no built static web assets are committed. Local gate: see Baseline Evidence table. Remaining: full `docker build` execution of the appendix gate is pending an environment with registry network access; CI wiring is tracked in EP-5.

## Project-Wide Correction Plan — Full Audit Findings

This section implements [ADR 0017](docs/adr/0017-course-correction-broaden-project-wide-correction-plan-beyond-storage-engine-notes.md). Items below are planned corrections; remaining-gap notes are the current truth.

### A. Build/scaffolding/package hygiene

| Field | Plan |
|---|---|
| Owner agent(s) | `java-scaffolder` primary; `java-tester` for build gates; `documenter` for report updates |
| Affected modules/files | Root and module `pom.xml`; generated/static asset boundaries; Dockerfiles; `.gitignore`/`.dockerignore`; `scripts/check-source-hygiene.sh` |
| Acceptance gates | `mvn validate` from clean checkout; clean `git status` after builds; no committed `.class`/generated `META-INF`; one canonical coverage profile; Docker healthcheck dependencies present; admin POM validates |

Done: plugin/coverage-profile centralization, hygiene ignores, `wget` healthcheck dependency, source-hygiene script, generated root output removal (`mvn validate` PASS, hygiene script PASS). Remaining: duplicate module-level `coverage` profile consolidation in `bootstrap-application/pom.xml`; full Docker build validation.

### B. Module/layering architecture

| Field | Plan |
|---|---|
| Owner agent(s) | `java-scaffolder`, `java-infra-coder`, `java-domain-coder`, `documenter` |
| Affected modules/files | Application/infrastructure module dependencies; Spring configuration; `scripts/check-module-layering.sh` |
| Acceptance gates | No application→infrastructure dependency inversion; context tests prove backend selection; duplicate repository beans fail fast; package scanning covers intended beans |

Done: layering guard wired into root `validate` (application modules cannot depend on infrastructure); explicit `objectstore`/`objectstorage` scan-root bridge; profile-guarded backend beans with conflict fail-fast; `DefaultBackendContextTest`/`StorageEngineBackendContextTest` cover selection. Remaining: no package rename (naming bridge stays); `bootstrap-application` still depends on many modules for assembly; guard is shell/POM based, no ArchUnit test.

### C. Domain quality

| Field | Plan |
|---|---|
| Owner agent(s) | `java-domain-coder`; `java-tester`; `documenter` |
| Affected modules/files | `storage-engine-domain`, `object-store-domain` aggregates/value objects/records |
| Acceptance gates | Domain module gates pass with meaningful tests; invalid lifecycle transitions fail deterministically; no mutable internal collections leaked |

Done: defensive/immutable collection exposure across object-store and storage-engine domain types; strengthened `StoredObject` invariants (module gates: storage-engine-domain 172, object-store-domain 292 — see `docs/test-report.md`). Remaining: `StoredObject` stays intentionally mutable via lifecycle methods; duplicate/ambiguous encryption class names not renamed; `Bucket` deletion still lacks an explicit terminal state; no broad immutable-aggregate redesign.

### D. Application/infrastructure/runtime correctness

| Field | Plan |
|---|---|
| Owner agent(s) | `java-infra-coder`, `java-domain-coder`, `java-tester` |
| Affected modules/files | Storage-engine ACL repository; filesystem repositories; orchestrator; S3 handlers; `bootstrap-application` |
| Acceptance gates | Storage-engine backend integration tests for put/get/delete/list and read-after-write; no full-body aggregation on hot paths; slash-key routes; multipart persistence/assembly; asserted ETag behavior; catalog-backed admin errors |

Done: `StorageEngineReactiveS3ObjectRepository#getContent` performs real read-through from persisted manifests with regression coverage incl. slash keys; `FileSystemManifestRepository` checksum round-trips; blocking filesystem I/O isolated on `Schedulers.boundedElastic()` via `BlockingFileSystemOperation` (targeted and full Maven gates PASS — see `docs/test-report.md`).

Remaining gaps:
- **Write path:** only `S3MultipartHandler.uploadPart` still uses `DataBufferUtils.join`. `S3ObjectOperationsHandler.putObject` (single-pass `UploadDigest` tee) and `FixedWindowDedupStep` (incremental windows) were fixed and are guarded by the static architecture test `ReactiveUploadStreamingArchitectureTest` (REQ-PIPELINE-007/008).
- **Read path (newly identified, planner-verified):** `S3ObjectOperationsHandler.getObject` calls `oc.content().collectList()` for **both** range and non-range requests, materializing the whole object in memory on read. Open reactive-streaming defect — tracked as EP-3. Not claimed fixed.
- Multipart part-body persistence and final object assembly remain incomplete (part bytes are currently discarded) — EP-3.
- Broader chunk identity consistency, dedup duplicate-hit short-circuit, and complete admin catalog-backed error semantics remain open (see CC-6).

### E. Storage-engine/MINIO_STANDARD/configuration ✅ Bounded first pass completed

| Field | Plan |
|---|---|
| Owner agent(s) | `java-domain-coder`, `java-infra-coder`, `java-scaffolder`, `java-tester`, `documenter` |
| Affected modules/files | Storage-engine modules; storage policy/device/disk-set YAML directories; docs |
| Acceptance gates | YAML catalogs reject malformed/duplicate/unresolved input; `MINIO_STANDARD` loads from YAML with deterministic plans; storage-engine backend fails fast on missing config; admin APIs use the same catalogs |

Done: all four acceptance gates met — `YamlStoragePolicyCatalog`/`YamlStorageDeviceCatalog`/`YamlDiskSetCatalog` with robust validation (36 catalog tests); `MINIO_STANDARD` classpath YAML with deterministic plans (`MinioStandardIntegrationTest`); fail-fast on missing external config; context tests for mutually exclusive backends; Admin API integrated via the same catalog beans (`AdminRouterTest`, 9 tests). Remaining: no integration test exercises the Admin API through a real YAML-backed Spring Boot context; physical EC shard placement and multi-node topology not runtime-validated; policy schema versioning documented but not enforced.

### F. Testing/quality

| Field | Plan |
|---|---|
| Owner agent(s) | `java-tester` primary; `documenter` for report integration |
| Affected modules/files | Surefire/JaCoCo reports; requirement features; AWS CLI suites; `docs/test-report.md` |
| Acceptance gates | `mvn test` and module gates pass; WebTestClient/AWS CLI parity matrix exists; AWS CLI failures reproducible or explicitly skipped; `docs/test-report.md` matches current command results |

Done: JaCoCo documented as baseline (Clover optional/legacy); evidence-based `docs/test-report.md`; parity increments per CC-9. Current counts: see Baseline Evidence table. Remaining: full canonical parity matrix; standalone `test-aws-cli.sh` report is stale/failing and separate from Cucumber parity.

### G. Documentation/C4/API truthfulness

| Field | Plan |
|---|---|
| Owner agent(s) | `documenter` primary; `c4model` via `delegate_agent`; `java-tester` supplies verified results |
| Affected modules/files | `README.md`, `PLAN.md`, `docs/arc42/**`, `docs/adr/**`, `docs/c4/**`, `docs/test-report.md`, `docs/api-coverage.md`, `LICENSE` |
| Acceptance gates | No implemented-status overclaims; C4/ARC42 names match code; linked docs exist; ADR statuses current and in English |

Done: S3-only/admin contradiction corrected; storage-engine runtime claims downgraded then re-verified; JaCoCo baseline wording. Remaining: C4 diagram refresh for current routers/admin API; ADR freshness sweep; residual stale links.

### H. Web/UI planning

| Field | Plan |
|---|---|
| Owner agent(s) | `documenter` for UI/API plan; `java-infra-coder` for backend contracts; frontend implementation requires the frontend workflow agents |
| Affected modules/files | `magrathea-ui/**` (planned only), `admin-api-adapter`, Docker frontend/docs packaging |
| Acceptance gates | Backend admin contract tests pass; UI stays planned until frontend ownership assigned; Docker regenerates frontend/docs assets; docs claim no unimplemented screens |

Done: read-only configuration-as-code Admin API catalogs with non-persistent validation; Dockerfile-driven asset regeneration; Admin UI screen plan delivered (`docs/admin-ui-plan.md`). Remaining: `GET /admin/backend-status` implementation; frontend workflow handoff. Superseded/expanded by EP-7.

### Prioritized roadmap (audit corrections)

| Priority | Correction focus | Status |
|---|---|---|
| P0 — Stop compounding false signals | Artifact boundaries, POM hygiene, documentation truth reset, JaCoCo baseline | ✅ Done 2026-06-12 (details in section A/G); remaining `mvn validate`-from-clean-checkout gate closed by section A evidence |
| P1 — Restore architectural and runtime correctness | Dependency inversion fix, explicit backend selection, storage-engine wiring, runtime correctness | ⚠️ Layering/scanning/wiring done; runtime correctness gaps tracked in section D and EP-2/EP-3 |
| P2 — Harden domain and configuration model | Domain tests, invariants, YAML catalogs, `MINIO_STANDARD`, topology | ✅ Bounded scope done (sections C/E); residual items listed there |
| P3 — Parity, documentation, and UI maturation | AWS CLI parity, C4/ARC42/API coverage, admin UI handoff | ⚠️ Increments done (CC-9, section H); remainder tracked in CC-9 and EP-7 |

## S3 API Semantic Completion Plan

This section implements [ADR 0018](docs/adr/0018-course-correction-classify-s3-api-coverage-by-semantic-implementation-not-route-count.md). Route count, handler count, or a `111/111` operation inventory is **not** a completion metric; counts only prove an endpoint is mapped.

### Per-Operation Classification Levels

| Level | Meaning | Evidence required before using the label |
|---|---|---|
| Mapped | Route, predicate, and handler entry point exist. | Route inventory and handler mapping test. |
| Stubbed | Route returns nominal/placeholder behavior without meaningful S3 state transitions. | Explicit test or code inventory showing documented unsupported/stub behavior. |
| Stateful | Operation creates/reads/updates/deletes durable state with observable follow-up behavior. | Stateful tests covering success, errors, and follow-up reads/listings. |
| AWS CLI compatible | Black-box AWS CLI tests verify request/response shape, headers, exit status. | Current AWS CLI scenario result linked from the test report. |
| Storage-engine compatible | Operation uses the selected storage-engine backend correctly where relevant. | Storage-engine backend scenario, not only in-memory tests. |
| Semantically S3-compatible | Supported scope matches expected S3 behavior incl. errors, idempotency, edge cases. | Semantic scenario suite plus documented unsupported deviations. |

### Family-by-Family Completion Proposal

| Family | Priority | Expected outputs | Acceptance gates |
|---|---|---|---|
| Object CRUD baseline (`PutObject`, `GetObject`, `HeadObject`, `DeleteObject`, `CopyObject`) | S3-P1 | Full CRUD semantics: read-after-write, slash keys, metadata persistence, checksum/ETag, storage class, delete idempotency, copy, storage-engine backend read/write/delete. | WebTestClient + repository tests prove state transitions; storage-engine backend passes equivalent scenarios. |
| Bucket baseline (`CreateBucket`, `HeadBucket`, `DeleteBucket`, `ListBuckets`, `ListObjects`, `ListObjectsV2`) | S3-P1 | Real bucket state, existence checks, list ordering, prefixes/delimiters/continuation tokens/markers/max keys, non-empty delete behavior. | Stateful bucket lifecycle tests; list V1/V2 tests; AWS CLI scenarios marked separately. |
| Multipart upload | S3-P2 | Persisted part bodies and metadata, part listing, part-copy, final assembly, multipart ETag, abort/orphan cleanup, storage-engine integration. | Multipart state machine tests; completed object reads as assembled bytes; storage-engine backend passes. (Streaming/persistence gap tracked as EP-3.) |
| Object metadata/configuration (ACLs, tags, attributes, legal hold, retention, restore) | S3-P2 persistence; S3-P4 enforcement | Distinguish persisted metadata from enforcement; document unsupported enforcement separately. | Put/get/delete metadata tests; metadata survives read/head/list; unsupported enforcement documented. |
| Bucket configuration (CORS, lifecycle, website, logging, notification, replication, encryption, versioning, tagging, accelerate, location, ownership, request payment) | S3-P3 config; S3-P4 enforcement | Per-config classification as `config-only`/`enforced`/`unsupported`/`stub`. | Matrix row + get/put/delete tests per config; execution not claimed without tested background behavior. |
| Versioning/delete markers | S3-P3 | Version IDs, latest resolution, delete markers, versioned list/get/head/delete, storage-engine persistence of version state. | Versioning state machine tests; delete markers affect unversioned reads S3-compatibly. |
| Access/security/public controls (bucket policy, public access block, ACL enforcement, expected owner, ownership controls) | S3-P4 | Separate stateful storage from authorization enforcement. Enforcement is EP-1. | Authorization tests must prove deny/allow before `enforced`. |
| Encryption/object lock/retention | S3-P4 (metadata pieces S3-P2/P3) | Metadata-only vs enforced clarified; real encryption at rest is EP-1 scope. | Enforcement tests prove blocked delete/overwrite and retention expiry before semantic claims. |
| Analytics/inventory/metrics/intelligent-tiering | S3-P4 | Config-only first; no background job claims. | Config CRUD tests; matrix notes `config-only`. |
| Replication/lifecycle/notification execution | S3-P4 | Distinguish config storage from executing background behaviors. | Execution needs integration tests proving side effects; otherwise config-only/stubbed. |
| Advanced/specialized APIs (`SelectObjectContent`, `RestoreObject`, torrent, Object Lambda, directory buckets/sessions) | S3-P4 / separate scope | Likely unsupported or separate scope unless implemented. | Documented unsupported/not-implemented responses or explicit scenarios with deviations. |
| Admin/storage-engine integration APIs | S3-P0..P4 | Not AWS S3 APIs; must not become a parallel object API. Read-only catalogs + validation today; backend status planned. | Admin API tests prove catalog/validation behavior; docs keep admin APIs separate from S3 coverage. |

### Required S3 Semantic Reporting Table Template

| Family | Operation | Route mapped | Stateful behavior | AWS CLI scenario | Storage-engine scenario | Semantic status | Notes |
|---|---|---|---|---|---|---|---|
| Example: Object CRUD | `PutObject` | Yes/No | Mapped / Stubbed / Stateful | Pass / Fail / Missing / N.A. | Pass / Fail / Missing / N.A. | Mapped / … / Semantically S3-compatible | Scope limits, deviations, linked tests. |

### S3 Priority Roadmap

| Priority | Correction focus | Status / gate |
|---|---|---|
| S3-P0 | Replace `111/111` claims with a semantic matrix; inventory all operations. | ✅ Documentation truth reset done 2026-06-12; full per-operation semantic inventory still pending a dedicated java-tester pass. |
| S3-P1 | Object CRUD + bucket baseline against both backends. | ⚠️ First AWS CLI CRUD/bucket increments complete (CC-9); metadata/checksum/copy semantics, fuller list semantics, and storage-engine scenarios remain. |
| S3-P2 | Multipart + object metadata/tagging/ACL persistence. | Open; multipart part persistence/assembly is EP-3. |
| S3-P3 | Bucket configuration statefulness; versioning/delete markers. | Open. |
| S3-P4 | Authorization/enforcement/background jobs/advanced APIs. | Open; authorization/enforcement is EP-1. |

### S3 Semantic Acceptance Criteria (summary)

- No operation documented as implemented without semantic classification and passing stateful tests.
- AWS CLI parity and storage-engine compatibility are separate marks; neither is implied by the other or by WebTestClient results.
- Config APIs distinguish `config-only`/`enforced`/`unsupported`/`stub`; metadata-only behavior is never described as enforcement.
- Background behavior (lifecycle, replication, notifications, analytics, tiering, restore) is not claimed without a tested background mechanism.
- Final completion claims are family-based and evidence-based, never route-count-based.

## Enterprise Production Readiness Plan (EP-0 .. EP-9)

This section defines the path from "validated prototype with honest gaps" to an enterprise-deployable object store. All EP phases start as `@absent` or `@partial` — **no completion claims are made here**.

### Binding Cross-Cutting Invariants

- **INV-1 — Reactive-first.** Every new production path must remain reactive/non-blocking end to end: Reactor, bounded memory, no `DataBufferUtils.join` or whole-body materialization, blocking I/O only on `boundedElastic`. Static architecture tests (pattern: `ReactiveUploadStreamingArchitectureTest`) must guard each newly fixed path.
- **INV-2 — Cucumber-first requirements.** Every EP phase MUST begin by writing/refreshing shared Gherkin requirement features (per AGENTS.md): requirement IDs, functional/non-functional tags, honest status tags, and WebTestClient + AWS CLI (or protocol-appropriate CLI) validation modes reusing the same shared feature text.
- **INV-3 — Admin panel is a first-class product deliverable.** A COMPLETE admin panel (not just read-only catalogs) is in scope — but it must never become an alternate object API (AGENTS.md B.3).
- **INV-4 — S3 is the internal core.** Any additional protocol adapter (WebDAV first) is an optional external facade that MUST delegate to the same reactive object-store application services (`ReactiveObjectService` and friends). Internally everything remains S3 semantics; no protocol adapter may talk to the storage engine directly or introduce a parallel persistence path.

### EP-0 — Governance: Definition of Production Ready (PRIORITY: foundation)

| Field | Plan |
|---|---|
| Focus | Single measurable exit checklist aggregating all EP gates; evidence-based reporting rules. |
| Owner agents | `java-planner` + `documenter` |
| Requirement feature file | n/a (governance; gates aggregate from EP-1..EP-9 feature files) |
| Expected outputs | Definition of Production Ready checklist (below) maintained in this plan; reporting rules binding all EP phases to the Baseline Evidence discipline. |
| Acceptance gates | No "production-ready" claim anywhere until all **Blocker** EP phases are `@implemented-and-validated`. |
| Status | `@absent` (checklist seeded below, unchecked) |

### EP-1 — Security & Identity (BLOCKER)

| Field | Plan |
|---|---|
| Focus | Authentication, authorization, audit, and real encryption. Planner-verified evidence: no SigV4 signature verification, no Spring Security dependency, the S3 API accepts anonymous requests; ACLs/policies are persisted but never enforced. |
| Owner agents | `java-domain-coder` (policy model), `java-infra-coder` (filters/adapters), `java-tester` |
| Requirement feature file | `phase-ep1-security.feature` |
| Key requirement IDs | REQ-SEC-* |
| Expected outputs | AWS SigV4 request authentication; durable credential/access-key store; deny-by-default authorization with bucket policy/ACL evaluation; audit logging of requests; real SSE (currently metadata-only) with pluggable key management; TLS deployment guidance. |
| Acceptance gates | Unauthenticated/incorrectly signed requests rejected; deny-by-default proven by tests; ACL/policy enforcement validated in both runner modes; audit events observable; SSE round-trip proven at rest, not just headers. |
| Status | `@absent` |

### EP-2 — Complete Metadata Durability (BLOCKER)

| Field | Plan |
|---|---|
| Focus | Close the in-memory metadata gap (see the qualified checklist items in CC below): bucket registry (`StorageEngineReactiveBucketRepository`), multipart upload state (`StorageEngineReactiveMultipartUploadRepository`), and per-object configuration metadata (ACL/legal hold/lock config/retention/encryption/restore maps in `StorageEngineReactiveS3ObjectRepository` lines 66–77) are still in-memory `ConcurrentHashMap` state even in storage-engine mode and are lost on restart. |
| Owner agents | `java-infra-coder` primary; `java-domain-coder`; `java-tester` |
| Requirement feature file | `phase-ep2-metadata-durability.feature` |
| Key requirement IDs | REQ-DUR-* |
| Expected outputs | Durable bucket registry, durable multipart upload state, durable per-object configuration metadata (ACL, tags where not already durable, object lock, retention, legal hold, encryption config, restore state) in storage-engine mode. |
| Acceptance gates | Restart-safety Cucumber scenarios per state family using the bootstrap restart harness pattern of REQ-UPLOAD-001/002; state survives full process stop/start. |
| Status | `@partial` (object bytes + manifests + object references are durable; the families above are not) |

### EP-3 — Reactive Streaming Completion (BLOCKER)

| Field | Plan |
|---|---|
| Focus | (a) GetObject read path streams without `collectList()` — full-body and Range requests served with bounded memory (fixes the read-path defect recorded in section D); (b) multipart `uploadPart` persists part bodies through the storage engine WITHOUT `DataBufferUtils.join`, and `completeMultipartUpload` assembles the real object (currently part bytes are discarded); (c) extend the static architecture test to forbid `join`/`collectList` in the fixed paths. |
| Owner agents | `java-infra-coder`, `java-domain-coder`, `java-tester` |
| Requirement feature file | extend `phase-3-reactive-pipeline.feature` + new `phase-ep3-multipart-streaming.feature` |
| Key requirement IDs | REQ-PIPELINE-009+, REQ-MPU-* |
| Expected outputs | Streaming GetObject (full + Range); streaming multipart part persistence and real assembly; extended `ReactiveUploadStreamingArchitectureTest`-style guards. |
| Acceptance gates | Large-object read/multipart scenarios pass in both runner modes with bounded memory; architecture test forbids regressions; completed multipart object reads back as assembled bytes. |
| Status | `@partial` (write path largely fixed and guarded; read path and multipart persistence open) |

### EP-4 — Space Management & Data Hygiene (HIGH)

| Field | Plan |
|---|---|
| Focus | Evidence: no chunk GC/reference counting exists (only `FileSystemRecoveryScanner`, on-demand); no ENOSPC behavior; no quotas; no background scrubbing. |
| Owner agents | `java-domain-coder`, `java-infra-coder`, `java-tester` |
| Requirement feature file | `phase-ep4-space-management.feature` |
| Key requirement IDs | REQ-GC-*, REQ-QUOTA-* |
| Expected outputs | Chunk garbage collection for deleted objects (refcount or mark-and-sweep job); dedup reference counting; defined disk-full (ENOSPC) behavior; per-bucket/tenant quotas; periodic integrity scrubbing job. |
| Acceptance gates | Deleted-object chunks reclaimed without corrupting shared dedup chunks; quota enforcement observable via S3 errors; scrubbing findings reported/quarantined; ENOSPC yields defined S3 errors, not corruption. |
| Status | `@absent` |

### EP-5 — Operability & Delivery (HIGH)

| Field | Plan |
|---|---|
| Focus | Evidence: no CI pipeline exists in the repo (no `.github`, no GitLab CI, no Jenkinsfile); gates are local/Podman only. |
| Owner agents | `java-scaffolder`, `java-infra-coder`, `documenter`, `java-tester` |
| Requirement feature file | `phase-ep5-operability.feature` (where executable; otherwise documented procedures tagged `@not-implemented` until automated) |
| Key requirement IDs | REQ-OPS-* |
| Expected outputs | CI pipeline running the full gate + appendix check + docker build; release/versioning strategy; backup/restore procedure; DR with declared RTO/RPO; enforced schema/manifest versioning and migration; runbooks; SLOs and alert rules; readiness/liveness probes beyond `/admin/health`; verified graceful shutdown draining. |
| Acceptance gates | CI runs green on the full gate; backup/restore rehearsed; probes and shutdown behavior validated; versioning/migration enforced by tests. |
| Status | `@absent` |

### EP-6 — Performance & Capacity Validation (HIGH)

| Field | Plan |
|---|---|
| Focus | No load/perf evidence exists today. |
| Owner agents | `java-tester` primary; `java-infra-coder` |
| Requirement feature file | `phase-ep6-performance.feature` |
| Key requirement IDs | REQ-PERF-* (non-functional) |
| Expected outputs | Load/soak/benchmark suite (e.g. k6/Gatling/JMH); documented object-size/concurrency limits; bounded-memory verification under load; request timeouts; connection limits; basic rate limiting. |
| Acceptance gates | Declared limits reproducibly validated; memory bounded under sustained load; timeout/limit behavior observable and tested. |
| Status | `@absent` |

### EP-7 — Complete Admin Panel (HIGH)

| Field | Plan |
|---|---|
| Focus | Builds on `docs/admin-ui-plan.md`. Backend read-only catalogs are done; the UI is absent. Admin API completion: `GET /admin/backend-status` per the planned contract; recovery-scanner reports; GC/space reports from EP-4; audit-log access from EP-1; credential/tenant management from EP-1. Full Vue UI in `magrathea-ui`: dashboard (backend status, health, metrics), storage policies (list/detail/validate), devices, disk sets/topology, recovery/GC reports, observability views, and admin-side S3 diagnostics that CALL the S3 API rather than bypass it. |
| Owner agents | Java workflow plans contracts (`java-infra-coder`, `documenter`); frontend implementation requires the frontend workflow agents (`frontend-scaffolder`/`core`/`ui`/`app-coder` + `frontend-tester` from the multi-agent base skill). |
| Requirement feature file | `phase-ep7-admin-panel.feature` |
| Key requirement IDs | REQ-ADMIN-* (`@admin-api` tags; `Business Need` for user-visible admin capabilities) |
| Expected outputs | Complete Admin API + complete Vue admin panel per INV-3. |
| Acceptance gates | Admin API contract tests pass; UI validated by frontend-tester; panel remains admin/config/status only — never an alternate object CRUD API (AGENTS.md B.3). |
| Status | `@partial` (backend read-only catalogs done; UI `@absent`) |

### EP-8 — HA Decision & Supply Chain (MEDIUM)

| Field | Plan |
|---|---|
| Focus | Decide the enterprise deployment target and harden the supply chain. |
| Owner agents | `documenter` (ADR), `java-scaffolder`, `java-tester` |
| Requirement feature file | `phase-ep8-supply-chain.feature` (where executable) |
| Key requirement IDs | REQ-HA-*, REQ-SUPPLY-* |
| Expected outputs | Explicit ADR deciding: single-node with HA (active/passive on shared storage) vs real distributed (which would activate PA-6 execution work); SBOM generation; dependency CVE scanning; container image hardening (non-root); license compliance report. |
| Acceptance gates | ADR accepted; SBOM/CVE scan/image hardening wired into the EP-5 pipeline; license report generated. |
| Status | `@absent` |

### EP-9 — WebDAV API Adapter (FUTURE, OPTIONAL)

| Field | Plan |
|---|---|
| Focus | Product decision from the owner: a `webdav-api-adapter` module will sit ALONGSIDE `s3-reactive-api-adapter` as a second protocol facade. |
| Owner agents | `java-scaffolder`, `java-infra-coder`, `java-tester`, `documenter` (ADR at phase start) |
| Requirement feature file | `phase-ep9-webdav.feature` |
| Key requirement IDs | REQ-WEBDAV-* (`Business Need`, `@webdav-api` tag) |
| Expected outputs | Optional, disabled-by-default adapter — activation mirrors the S3 adapter pattern (dependency present + `webdav.api.enabled=true` via Spring Boot auto-configuration). |
| Acceptance gates | See binding constraints below; WebDAV-client validation mode (e.g. curl/cadaver runner) plus WebTestClient sharing scenario text per AGENTS.md. |
| Prerequisites | EP-1 (auth), EP-3 (streaming read path) |
| Status | `@absent` |

Binding EP-9 constraints:

1. MUST delegate exclusively to the same reactive object-store application services (`ReactiveObjectService` / `ReactiveBucketService` et al.) — internally everything remains S3: WebDAV collections map to buckets/key prefixes, WebDAV resources map to S3 objects, PROPFIND maps to List/Head, GET/PUT/DELETE map to Get/Put/DeleteObject, MOVE/COPY map to S3 copy+delete/copy semantics.
2. NO direct storage-engine access, no parallel persistence, no separate metadata store — properties beyond S3 semantics must be represented as S3 user metadata or rejected.
3. Fully reactive (INV-1).
4. Requirements written first as `Business Need` features with `@webdav-api` tag and both validation modes (INV-2).
5. An ADR must be written at phase start covering the protocol subset (locking/LOCK-UNLOCK likely `@not-implemented` initially, versioning mapping, auth reuse from EP-1).
6. `webdav-api-adapter` is listed as PLANNED in the module map below.

### EP Priority and Sequence

| Order | Phase | Priority | Status |
|---|---|---|---|
| 1 | EP-2 Metadata durability | Blocker | `@partial` |
| 2 | EP-3 Reactive streaming completion | Blocker | `@partial` |
| 3 | EP-1 Security & identity | Blocker | `@absent` |
| 4 | EP-4 Space management & data hygiene | High | `@absent` |
| 5 | EP-5 Operability & delivery | High | `@absent` |
| 6 | EP-6 Performance & capacity | High | `@absent` |
| 7 | EP-7 Complete admin panel (backend contracts may proceed in parallel with EP-1..EP-6) | High | `@partial` |
| 8 | EP-8 HA decision & supply chain | Medium | `@absent` |
| 9 | EP-9 WebDAV adapter (optional; after EP-1 and EP-3) | Future | `@absent` |

EP-0 governance applies continuously from the start.

### Definition of Production Ready (EP-0 checklist — all unchecked)

- [ ] EP-1: SigV4 authentication, deny-by-default authorization, audit logging, and real SSE are `@implemented-and-validated`.
- [ ] EP-2: all metadata families (bucket registry, multipart state, per-object configuration) survive restart in storage-engine mode, validated by restart harness scenarios.
- [ ] EP-3: GetObject and multipart paths stream with bounded memory; architecture tests guard all fixed paths.
- [ ] EP-4: chunk GC, dedup refcounting, quotas, ENOSPC behavior, and scrubbing are validated.
- [ ] EP-5: CI pipeline green on full gate + appendix check + docker build; backup/restore and DR rehearsed; probes/shutdown validated.
- [ ] EP-6: load/soak limits documented and reproducibly validated.
- [ ] EP-7: complete admin panel delivered and validated; panel is not an alternate object API.
- [ ] EP-8: HA target ADR accepted; SBOM/CVE/image hardening/license gates wired into CI.
- [ ] EP-9 (optional): WebDAV adapter, if built, meets all binding constraints.
- [ ] All claims above are backed by evidence per the Baseline Evidence discipline; no status inferred from route inventories or smoke checks.

## Course Correction Plan — Storage Engine Configuration and MINIO_STANDARD (CC-0 .. CC-10)

This section supersedes earlier planning statements where they conflict with [ADR 0016](docs/adr/0016-course-correction-storage-engine-policy-device-configuration-and-minio-standard-use-case.md).

### Baseline Summary (condensed)

- Java 21 / Spring Boot 4 WebFlux S3-compatible object store; object-store reactive modules tested; storage-engine modules runtime-wired behind the `storage-engine` profile.
- The S3 API is the public object API; `/admin/**` is a separate admin/configuration API limited to non-S3 storage-engine concepts.
- JaCoCo is the coverage baseline; Clover/OpenClover is optional/legacy.
- AWS CLI Cucumber parity is incremental, not complete (see CC-9).
- YAML-backed policy/device/disk-set catalogs and the read-only Admin API are delivered; `MINIO_STANDARD` is the first test-backed policy use case.

### Architecture Correction Goals (unchanged)

Chunking stays inside `DedupConfig`, configurable via `StoragePolicy`; one YAML file per policy and per device/disk-set/topology entity; YAML-backed catalogs; `MINIO_STANDARD` first use case; admin management as configuration-as-code/read-only with non-persistent validation; selectable storage-engine backend; AWS CLI/WebTestClient scenario parity where possible; ACL-separated bounded contexts.

### Module Ownership Map

| Concern | Primary modules / paths |
|---|---|
| S3 object API | `s3-reactive-api-adapter` |
| Object-store domain/application | `object-store-domain`, `object-store-reactive-application`, `object-store-reactive-repository-application` |
| In-memory backend | `object-store-reactive-infrastructure` |
| Storage-engine backend ACL | `object-store-reactive-repository-storage-engine-infrastructure` |
| Storage engine domain / application / infrastructure | `storage-engine-domain`, `storage-engine-reactive-application`, `storage-engine-reactive-infrastructure` |
| Runtime assembly | `bootstrap-application`, root `pom.xml` |
| Admin API / Admin UI | `admin-api-adapter` / `magrathea-ui` (frontend workflow required for UI code) |
| Documentation/reports | `README.md`, `PLAN.md`, `docs/**` |

### Completed corrections (CC-0 .. CC-5)

| Phase | Outcome (one line) | Key evidence |
|---|---|---|
| CC-0 Documentation/Decision Sync | ADR 0016 accepted; JaCoCo baseline wording; S3-only/admin contradiction fixed; storage-engine overclaims downgraded. | Doc review; cross-phase checklist verifications 2026-07-02 |
| CC-1 Nomenclature/Wiring Strategy | Reactive naming parity documented; profile-based backend selection with duplicate-bean prevention. | `mvn validate`; backend context tests |
| CC-2 StoragePolicy Domain Hardening | Policy/dedup invariants enforced; chunking owned by `DedupConfig`; policy-driven thresholds. | commit `abb426e`; storage-engine-domain tests |
| CC-3 StorageDevice/Topology Domain | Domain-pure device/disk-set/failure-domain/health/capacity model sufficient for `MINIO_STANDARD`. | commit `abb426e`; topology validation tests |
| CC-4 YAML Catalogs | One-file-per-entity YAML catalogs with validation, external config dirs, startup-snapshot reload semantics. | commit `d53b543`; 36 catalog tests |
| CC-5 `MINIO_STANDARD` | Explicit semantics (S3 `STANDARD`, dedup off, EC planning 4+2, RF 1, compression/encryption off) loaded from YAML with deterministic plans. | commits `b0a5f74`/`0ec84cf`; `PersistencePlannerMinioStandardTest`, `MinioStandardIntegrationTest` |

### CC-6 — Orchestrator/Dedup/Store Consistency — remaining open items

| Field | Open items |
|---|---|
| Owner agents | `java-domain-coder`, `java-infra-coder` |
| Remaining | Dedup duplicate-hit short-circuit (duplicate uploads must skip duplicate chunk persistence when dedup is enabled); fully consistent chunk identity across dedup index, manifest, filesystem persistence, and reads; transaction/rollback behavior definition for partial writes; streaming tests crossing chunk boundaries. |
| Gates | Orchestrator tests for single/multi-chunk, duplicate, non-dedup content, and read-after-write. |

### CC-7 — Storage-Engine Backend Wiring — done with a scope qualifier

Backend selection, mutually exclusive beans, fail-fast on missing config, and S3 write/read through the storage engine are delivered **for object bytes, manifests, and object references only**. The remaining in-memory metadata families are tracked as **EP-2** (see the qualified checklist items below).

### CC-8 — Web/Admin API and UI — remaining open items

| Field | Open items |
|---|---|
| Owner agents | `java-infra-coder` (backend), frontend workflow agents (UI) |
| Delivered | Read-only `/admin/storage-policies`, `/admin/storage-devices`, `/admin/disk-sets`; non-persistent `POST /admin/storage-policies/validate`; 405 on mutation; Admin UI screen plan (`docs/admin-ui-plan.md`). |
| Remaining | Implement `GET /admin/backend-status` per the planned contract (selected backend, selecting profile/property, catalog summary, storage roots, recovery-scanner summary); frontend workflow handoff before changing `magrathea-ui`. Superseded/expanded by **EP-7**. |

### CC-9 — Cucumber Parity — remaining open items

| Field | Open items |
|---|---|
| Owner agent | `java-tester` |
| Delivered | AWS CLI parity increments for object CRUD (incl. slash keys), bucket operations, ACL/tagging, object attributes, and multipart lifecycle, with production fixes — commits `a03bc4a` / `b2c333c` / `9ff5a08` (one-line historical reference; details in git history). |
| Remaining | Extract canonical scenario definitions from WebTestClient-only features; full parity beyond the delivered increments (every canonical scenario has both `@webclient` and `@awscli` coverage or an explicit skip/unsupported reason); decide whether `test-aws-cli.sh` becomes generated/legacy or remains a separate compatibility smoke report; document AWS CLI environment requirements. |

### CC-10 — Quality Gates ✅ Done

Phase CC-10 quality gates were completed 2026-06-12 (HEAD `351d088`): `mvn validate` and `mvn test` gates green, JaCoCo established as the coverage baseline. Current test counts and per-module coverage live in `docs/test-report.md` and the Baseline Evidence table; do not cite the historical CC-10 snapshot numbers as latest.

### Cross-Phase Acceptance Checklist

- [x] ADR 0016 accepted and docs aligned; JaCoCo documented as baseline; admin API no longer contradicted by S3-only wording (verified 2026-07-02).
- [x] `StoragePolicy` owns dedup/chunk configuration through `DedupConfig` (verified 2026-07-02; `FixedWindowDedupStep` takes chunk size from the policy spec, commit `9a50ae6`).
- [x] One YAML file per `StoragePolicy` and per `StorageDevice`/disk set/topology entity; catalogs validate IDs, schemas, and references.
- [x] `MINIO_STANDARD` explicitly defined, loaded from YAML, and testable (verified 2026-07-02).
- [x] Storage-engine backend can be selected at runtime without duplicate repositories. **Qualifier:** true for object bytes, manifests, and object references only — see next item.
- [x] S3 write/read path works end to end with the selected storage-engine backend **for object bytes + manifests + object references only.** Bucket registry (`StorageEngineReactiveBucketRepository`), multipart upload state (`StorageEngineReactiveMultipartUploadRepository`), and per-object configuration metadata (ACL/legal hold/lock config/retention/encryption/restore maps in `StorageEngineReactiveS3ObjectRepository` lines 66–77) are still in-memory `ConcurrentHashMap` state even in storage-engine mode and are lost on restart. Tracked as enterprise gap **EP-2**.
- [x] Backend Admin API exposes read-only policy/device/disk-set catalogs and non-persistent validation as configuration-as-code.
- [x] Admin UI plan covers policy/device/disk-set/backend-status screens and awaits frontend workflow ownership (`docs/admin-ui-plan.md`, 2026-07-02).
- [ ] AWS CLI Cucumber parity for all canonical scenarios (increments delivered; remainder tracked in CC-9).
- [x] Documentation reports planned vs completed work accurately.

### Risk Table

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Storage-engine backend wiring | Duplicate beans, wrong backend selected, or storage-engine code not exercised end to end | Mutually exclusive Spring conditions/profiles; context tests for both backends; fail fast on ambiguous wiring | `java-infra-coder`, `java-scaffolder` |
| Policy schema design | YAML becomes unstable or too coupled to current classes | Schema versioning; framework-free domain; validated examples; documented compatibility expectations | `java-domain-coder`, `java-infra-coder` |
| Data migration | Future policy/device schema changes may strand existing manifests/chunks | Version manifests and YAML schemas; document no-migration assumption; migration ADR before persistent compatibility claims | `java-domain-coder`, `documenter` |
| Large-object streaming | Buffering entire objects breaks WebFlux behavior and memory use | Large-object/chunk-boundary tests; Flux streaming at boundaries; static architecture tests per INV-1; EP-3 closes remaining paths | `java-infra-coder`, `java-tester` |
| Real disk placement | Paths, capacity, health, and failure-domain behavior may diverge from simple tests | Minimum topology modeled; temporary-directory integration tests; document real-disk limitations | `java-domain-coder`, `java-infra-coder` |
| MinIO semantic ambiguity | `MINIO_STANDARD` interpreted inconsistently | CC-5 fixed the documented semantics; no runtime EC shard placement claims until tested | `java-domain-coder`, `documenter` |
| AWS CLI environment dependency | CLI tests fail due to missing AWS CLI, credentials, endpoint, or port conflicts | Tagged tests, preflight checks, endpoint overrides, clear skip/failure reporting | `java-tester` |
| Frontend workflow availability | Vue UI work blocked because current workflow is Java-focused | Backend contracts first; frontend workflow handoff before `magrathea-ui` changes | `documenter`, `java-infra-coder` |
| WebDAV scope creep / protocol divergence | Second protocol facade drifts from S3 semantics or grows a parallel persistence path | S3-internal-core invariant **INV-4**; ADR-gated protocol subset at EP-9 start; adapter reviews reject direct storage-engine access | `documenter`, `java-infra-coder` |
| Admin panel drifting into an alternate object API | Admin API/UI grows object CRUD semantics, violating the storage-engine boundary | AGENTS.md B.3 gate enforced in every EP-7 review; admin-side S3 diagnostics must call the S3 API | `documenter`, `java-infra-coder` |
| Unauthenticated exposure until EP-1 lands | The S3 API accepts anonymous requests today; exposure to untrusted networks means full data compromise | The service **must not be exposed to untrusted networks** until EP-1 is `@implemented-and-validated`; document this in deployment guidance | `documenter`, `java-infra-coder` |
| In-memory metadata loss on restart until EP-2 lands | Buckets, multipart state, and per-object configuration metadata are lost on restart even in storage-engine mode | EP-2 is first in the EP sequence; restart-safety scenarios per state family; no durability claims for these families until validated | `java-infra-coder`, `java-tester` |

---

## Existing Project Structure

### Maven Modules

```text
magrathea-objectstorage/
├── pom.xml
├── s3-reactive-api-adapter/                    # Pluggable S3 HTTP adapter (RouterFunction, XML, Cucumber tests)
├── webdav-api-adapter/                         # PLANNED — optional WebDAV protocol adapter (EP-9)
├── admin-api-adapter/                          # Admin/configuration API adapter
├── object-store-domain/                        # Pure S3 domain: aggregates, value objects, domain events
├── object-store-reactive-repository-application/ # Reactive CQS repository interfaces
├── object-store-reactive-application/          # Reactive application services and DTOs
├── object-store-reactive-infrastructure/       # Reactive in-memory repository implementations
├── storage-engine-domain/                      # Storage Engine domain: policy, workflow, device, trace, manifest
├── storage-engine-reactive-application/        # Storage Engine reactive orchestration and ports
├── storage-engine-reactive-infrastructure/     # Storage Engine filesystem backend and YAML catalogs
├── object-store-reactive-repository-storage-engine-infrastructure/ # ACL + adapter: Object Store → Storage Engine
├── bootstrap-application/                      # Spring Boot entry point
├── magrathea-ui/                               # Vue UI assets and documentation dashboard
├── docs/                                       # ARC42, ADR, C4, reports
└── test-aws-cli.sh                             # AWS CLI compatibility script/report path
```

### Two-Backend Repository Strategy

| Backend | Module | Activation | Description |
|---|---|---|---|
| In-memory | `object-store-reactive-infrastructure` | Default `single-node`/development mode | Reactive in-memory repositories for development and tests |
| Storage Engine | `object-store-reactive-repository-storage-engine-infrastructure` + `storage-engine-*` | `storage-engine` profile/property | Filesystem-backed storage-engine bounded context (durability qualifier: see EP-2) |

The ACL/adapter module translates object-store repository commands into storage-engine commands and remains the anti-corruption layer between the two bounded contexts.

### S3 API Handler Organization

| Class | Responsibility |
|---|---|
| `S3PathRouter` | Route composition only |
| `S3BucketOperationsHandler` | Bucket lifecycle, listings, location/versioning |
| `S3BucketMetadataHandler` | Bucket ACL and tagging metadata |
| `S3BucketConfigHandler` | Bucket configuration, object-lock configuration, ABAC, metadata/table configurations |
| `S3ObjectOperationsHandler` | Object CRUD, copy, multi-delete, restore, rename, select |
| `S3ObjectMetadataHandler` | Object ACL, tagging, attributes, legal hold, retention, encryption metadata |
| `S3MultipartHandler` | Multipart upload lifecycle |
| `S3SessionHandler` | Session creation |
| `S3WebSupport` | Shared request predicates and S3 XML error helpers |

Handler minimal-pattern cleanup (extract → delegate → response) is complete for all handlers except `S3BucketConfigHandler`, which is tracked as a residual cleanup item.

### Pluggable S3 API

`s3-reactive-api-adapter` loads through Spring Boot auto-configuration (`AutoConfiguration.imports`, `S3ApiConfig` guarded by `s3.api.enabled=true`, default `true`); absent dependency or `s3.api.enabled=false` disables the S3 web API. The planned `webdav-api-adapter` (EP-9) must mirror this activation pattern with `webdav.api.enabled=true`.

### Testing and Coverage Strategy

| Level | Type | Command | Notes |
|---|---|---|---|
| 1 | Pure JUnit | `mvn test -pl object-store-domain` / `mvn test -pl storage-engine-domain -am` | Domain only, no Spring |
| 2 | Module tests | `mvn test -pl <module> -am` | Run affected modules during each phase |
| 3 | WebTestClient Cucumber | `mvn test -pl s3-reactive-api-adapter -am -Dsurefire.failIfNoSpecifiedTests=false` | Canonical S3 behavior through in-process WebFlux driver |
| 4 | AWS CLI Cucumber | `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false` | `@awscli` runners executing the **same shared requirement features** as WebTestClient (Phase 1/2/5 requirement suites plus the CRUD/bucket/metadata/multipart parity suite); runner differences live in glue/config/tags only |
| 5 | AWS CLI compatibility script | `bash test-aws-cli.sh` | Separate/stale compatibility report; fate decided in CC-9 |
| 6 | JaCoCo coverage | `mvn -Pcoverage test jacoco:report` | Current coverage baseline; numbers in `docs/test-report.md` |
| 7 | Clover/OpenClover | Optional/legacy only | Not a primary gate unless a future ADR revives it |
