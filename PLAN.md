# Magrathea ObjectStore — Correction Plan

> **Planning status:** This document is a correction and implementation plan. It is **not** an implementation result, and unchecked items must not be described elsewhere as already delivered.

Two phase numbering schemes exist in this plan and are kept deliberately distinct:

- **PA-1 .. PA-6** — Post-Audit Production Readiness phases (upload reliability, filesystem reliability, reactive pipeline, observability, S3 semantics, distributed readiness).
- **CC-0 .. CC-10** — Course Correction phases from ADR 0016 (storage-engine configuration, MINIO_STANDARD, backend wiring, admin API, Cucumber parity, quality gates).
- **EP-0 .. EP-11** — Enterprise Production Readiness phases (new; see the dedicated section below).
- **KA-1 .. KA-6** — Killer-App Track phases (market leadership on top of EP; see the dedicated section below).

## Current Baseline Evidence (2026-07-02)

This is the **single authoritative evidence table** for current test counts. Older counts that previously appeared inline in this plan (827, 833, 883, 902 tests; "64 scenarios") were snapshots of earlier runs and are superseded; do not cite them as latest.

| Evidence item | Result (2026-07-02) |
|---|---|
| Full test gate | `mvn clean test` in Podman `magrathea-build:fedora42` (JDK 21, Maven 3.9.9, AWS CLI 2.34.29): **BUILD SUCCESS, 13 modules, 942 tests / 0 failures / 0 errors / 5 skipped** |
| Gherkin requirements appendix | Fresh: `python3 scripts/generate-gherkin-requirements-appendix.py --check` OK — **66 scenarios from 6 feature files** |
| PA-1 upload runners | `Phase1UploadStorageEngineCucumberTest` + `Phase1UploadStorageEngineAwsCliCucumberTest`: **28 scenarios discovered / 0 failures per class** (non-selected scenarios excluded by tag filter) |
| PA-2 AWS CLI same-key stress | REQ-FS-006 same-key stress re-validation: **6/6 fresh-JVM runs passed** |
| Coverage | JaCoCo is the coverage baseline; current per-module numbers live in [`docs/test-report.md`](docs/test-report.md) — not embedded here to avoid staleness |
| S3 route inventory (2026-07-02) | ~95 operations mapped by the S3 router; 64 handler methods in `S3BucketConfigHandler` alone. **Inventory evidence only — explicitly NOT completion evidence** (per ADR 0018, mapped ≠ implemented). |

## Post-Audit Production Readiness Plan (PA-1 .. PA-6)

The post-audit baseline is that Magrathea ObjectStore remains a prototype with an advanced architecture, not a production-ready object store. Declared PA-1/PA-2/PA-5 scopes and EP-2 storage-engine metadata durability are implemented and validated; streaming completion, external observability, distributed readiness, and broader S3 semantics remain open and are tracked in the Enterprise Production Readiness Plan below.

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
| Validation evidence | Feature: `phase-3-reactive-pipeline.feature`. `Phase3ReactivePipelineCucumberTest` runner exists but runtime `@phase-3 @webclient` scenarios still carry `@not-implemented` and are excluded. `storage-engine-reactive-application` module gate 159/159. REQ-PIPELINE-007/008 (single-pass PutObject tee; incremental dedup windows), REQ-PIPELINE-009 (non-range GetObject attaches `oc.content()` directly), REQ-PIPELINE-010 (ranged GetObject slices the content Flux), and REQ-PIPELINE-011/012 (UploadPart/UploadPartCopy and part-store DataBuffer streaming) are Cucumber-validated by `Phase3ReactivePipelineStaticArchitectureSpecsCucumberTest`; REQ-PIPELINE-007/008 remain fast-guarded by `ReactiveUploadStreamingArchitectureTest`. Latest targeted gates: `mvn -pl s3-reactive-api-adapter -Dtest=Phase3ReactivePipelineStaticArchitectureSpecsCucumberTest test`, `mvn -pl s3-reactive-api-adapter -Dtest=Phase5S3SemanticCompatibilityRequirementsCucumberTest test`, and `mvn -pl s3-reactive-api-adapter -Dtest=Phase5S3SemanticCompatibilityAwsCliCucumberTest test` passed with no generated-password banner. |
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
| Current status | `@implemented-and-validated` for 32 WebTestClient scenarios, 14 AWS CLI scenarios, and 1 full-process restart scenario: ETag format/consistency (REQ-S3-001), multipart ETag semantics, completed-object ordered part assembly, UploadPartCopy copied-byte assembly, multipart XML error semantics, and part-body restart completion (REQ-S3-002-A/B/D/E/F/G/H/I/J/K), byte-range GET incl. 416 (REQ-S3-003), conditional requests (REQ-S3-004), CopyObject ETag (REQ-S3-005), object tagging lifecycle (REQ-S3-006), explicit unsupported/config-only classification for versioning/object lock/lifecycle (REQ-S3-007). `@implemented-not-e2e-validated` remains only for REQ-S3-002-C's same-process ListParts restart-simulation scenario, which is superseded for part-body/final-assembly durability by REQ-S3-002-E full-process evidence. |
| Validation evidence | `Phase5S3SemanticCompatibilityRequirementsCucumberTest` 25/25; `Phase5S3SemanticCompatibilityAwsCliCucumberTest` 12 `@awscli-required` examples executed, non-required filtered. Feature: `phase-5-s3-semantic-compatibility.feature`. |
| Remaining gaps | REQ-S3-002-C full-restart e2e validation; broader staged pipeline/runtime backpressure evidence for large multipart objects; versioning/delete markers/object-lock/lifecycle/replication enforcement remains intentionally unsupported or config-only. |

### PA-6 — Distributed Readiness ⚠️ Modeled and unit-validated, not e2e/distributed-production-ready

| Field | Plan / Result |
|---|---|
| Focus | Design and verify behavior needed before claiming distributed storage readiness. |
| Current status | `@implemented-not-e2e-validated` for the modeled domain scope: deterministic failure-domain placement, health-aware membership decisions, quorum decisions, anti-entropy/healing plans, rebalance plans, readiness classification (REQ-DIST-001..006) — all pure domain under `storage-engine-domain/.../distributed`, unit validated (module gate 164/164). |
| Remaining gaps | No real networked membership, replication execution, healing/rebalance job runners, durable multi-node manifest publication, or multi-node e2e validation. Distributed production readiness is not claimed. The HA question is decided (real multi-node cluster); *how* is the EP-8 ADR. PA-6's modeled domain is the foundation for EP-10 execution work; PA-6 stays honest as modeled-only until EP-10 delivers networked evidence. |

## Gherkin Requirements and ARC42 Appendix

Gherkin feature files are executable requirements. They must describe business/system behavior and storage invariants, not anemic status-code checks. S3 client behavior is specified and validated only through S3-compatible APIs using shared WebTestClient/AWS CLI features; storage-engine requirements are internal/operational abilities unless they describe admin-only concepts with no S3 API equivalent (policies, devices, disk sets/topology, backend status, validation/reporting), which may be exposed through the Admin API/UI. No public storage-engine endpoints for object semantics.

### Authoring Rules (summary)

- `Business Need` for externally promised S3 behavior; `Ability` for storage-engine internals; either for admin-only capabilities depending on user visibility.
- Status tags per AGENTS.md: `@implemented-and-validated`, `@implemented-not-e2e-validated`, `@partial`, `@config-only`, `@placeholder`, `@absent`, `@not-implemented`; smoke-only checks tagged `@protocol-smoke`.
- Every production requirement scenario carries a requirement ID, `@functional-requirement` / `@non-functional-requirement` classification, quality-attribute tags, and an explicit validation mode.
- Admin-only features use `@admin-api`, `@storage-policy`, `@storage-device`, `@disk-set`, `@backend-status` — not `@s3-api`.
- WebTestClient and AWS CLI validate the same shared feature text; runner differences live in glue/config/tags only.

### Generated Requirements Appendix — status

The appendix generator, freshness gate, ARC42 linkage, and Docker build integration are **done**: `scripts/generate-gherkin-requirements-appendix.py` writes `docs/arc42/generated/gherkin-requirements.adoc`; container builder stages run `--check` (failing on staleness) and deterministically regenerate the appendix before docs/frontend asset conversion; no built static web assets are committed. Local native-image Docker validation executed this appendix gate successfully via `Dockerfile.native` with host networking in the sandbox. Root JVM `Dockerfile` validation now also executes the freshness gate, regenerated docs/UI assets, unmasked Maven packaging, non-root runtime ownership, and container smoke validation.

## Project-Wide Correction Plan — Full Audit Findings

This section implements [ADR 0017](docs/adr/0017-course-correction-broaden-project-wide-correction-plan-beyond-storage-engine-notes.md). Items below are planned corrections; remaining-gap notes are the current truth.

### A. Build/scaffolding/package hygiene

| Field | Plan |
|---|---|
| Owner agent(s) | `java-scaffolder` primary; `java-tester` for build gates; `documenter` for report updates |
| Affected modules/files | Root and module `pom.xml`; generated/static asset boundaries; Dockerfiles; `.gitignore`/`.dockerignore`; `scripts/check-source-hygiene.sh` |
| Acceptance gates | `mvn validate` from clean checkout; clean `git status` after builds; no committed `.class`/generated `META-INF`; one canonical coverage profile; Docker healthcheck dependencies present; admin POM validates |

Done: plugin/coverage-profile centralization, hygiene ignores, `wget` healthcheck dependency, source-hygiene script, generated root output removal (`mvn validate` PASS, hygiene script PASS), root JVM Docker build validation with unmasked Maven packaging and runtime smoke. Remaining: duplicate module-level `coverage` profile consolidation in `bootstrap-application/pom.xml`.

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
- **Write path:** `S3ObjectOperationsHandler.putObject` (single-pass `UploadDigest` tee), `FixedWindowDedupStep` (incremental windows), and `S3MultipartHandler.uploadPart` / `uploadPartCopy` (part-store DataBuffer streaming) no longer use `DataBufferUtils.join` on full object/part bodies. These are guarded by `ReactiveUploadStreamingArchitectureTest` for REQ-PIPELINE-007/008 and Cucumber static specs REQ-PIPELINE-007..012.
- **Read path:** `S3ObjectOperationsHandler.getObject` now streams non-range responses by attaching `oc.content()` directly to `S3ResponseBuilder.okWithBody` (REQ-PIPELINE-009) and streams ranged responses through `serveRange(obj, oc.content(), rangeHeader)` plus per-buffer `sliceRange(...)` without collecting the full object (REQ-PIPELINE-010).
- Multipart part-body persistence and final object assembly are now implemented for uploaded and copied parts: `S3MultipartPartStore` persists part bodies under the configured storage root and `CompleteMultipartUpload` assembles ordered parts into the final object (`REQ-S3-002-D/F`, WebTestClient + AWS CLI validated). Part bodies also survive a full Spring stop/start and can be completed after restart into a readable object (`REQ-S3-002-E`, full-process restart validated). Multipart edge/error XML semantics are validated for malformed copy-source headers, missing upload IDs, malformed complete XML, invalid part references, and abort/complete conflicts (`REQ-S3-002-G..K`).
- **Handler-local state in the web adapter (planner-verified 2026-07-02; RESOLVED 2026-07-03):** `S3BucketConfigHandler` originally held **6 `ConcurrentHashMap`s directly in the WEB ADAPTER**. All six families (object-lock, inventory-table, journal-table, ABAC, metadata config, metadata-table) have been moved onto the `Bucket` aggregate and are durable via `BucketStore`; the handler now holds no configuration state (`resetInMemoryConfigurations` is a retained no-op). Layering violation and durability gap closed.
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

Done: S3-only/admin contradiction corrected; storage-engine runtime claims downgraded then re-verified; JaCoCo baseline wording. Remaining: C4 diagram refresh for current routers/admin API; ADR freshness sweep; residual stale links; **`LICENSE` resolved (2026-07-03)** — the MIT `LICENSE` now exists at the repository root together with the licensing ADR (`docs/adr/0019-adopt-the-mit-license.md`), delivered by KA-1; **`docs/api-coverage.md` does not exist (verified 2026-07-02)** despite being referenced here and required by the S3 Semantic Reporting Table Template — the S3-P0 semantic inventory was never completed; closed by the generated-matrix task in the Ancillary S3 API Requirements Backlog.

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
| P1 — Restore architectural and runtime correctness | Dependency inversion fix, explicit backend selection, storage-engine wiring, runtime correctness | ⚠️ Layering/scanning/wiring done; remaining runtime correctness gaps tracked in section D and EP-3+ |
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
| Multipart upload | S3-P2 | Persisted part bodies and metadata, part listing, part-copy, final assembly, multipart ETag, abort/orphan cleanup, storage-engine integration. | Completed object reads as assembled bytes now pass WebTestClient and AWS CLI for uploaded and copied parts; uploaded part bodies also survive full Spring stop/start and can be completed after restart. Multipart XML error semantics are covered for the declared edge cases. UploadPart/UploadPartCopy now stream part bodies to the part store without `DataBufferUtils.join`; remaining work is broader staged pipeline/runtime backpressure evidence for large multipart objects and future advanced S3 semantics. |
| Object metadata/configuration (ACLs, tags, attributes, legal hold, retention, restore) | S3-P2 persistence; S3-P4 enforcement | Distinguish persisted metadata from enforcement; document unsupported enforcement separately. | Put/get/delete metadata tests; metadata survives read/head/list; unsupported enforcement documented. |
| Bucket configuration (CORS, lifecycle, website, logging, notification, replication, encryption, versioning, tagging, accelerate, location, ownership, request payment) | S3-P3 config; S3-P4 enforcement | Per-config classification as `config-only`/`enforced`/`unsupported`/`stub`. | Matrix row + get/put/delete tests per config; execution not claimed without tested background behavior. |
| Versioning/delete markers | S3-P3 | Version IDs, latest resolution, delete markers, versioned list/get/head/delete, storage-engine persistence of version state. | Versioning state machine tests; delete markers affect unversioned reads S3-compatibly. |
| Access/security/public controls (bucket policy, public access block, ACL enforcement, expected owner, ownership controls) | S3-P4 | Separate stateful storage from authorization enforcement. Enforcement is EP-1. | Authorization tests must prove deny/allow before `enforced`. |
| Encryption/object lock/retention | S3-P4 (metadata pieces S3-P2/P3) | Metadata-only vs enforced clarified; real encryption at rest is EP-1 scope. | Enforcement tests prove blocked delete/overwrite and retention expiry before semantic claims. |
| Analytics/inventory/metrics/intelligent-tiering | S3-P4 | Config-only first; no background job claims. | Config CRUD tests; matrix notes `config-only`. |
| Replication/lifecycle/notification execution | S3-P4 | Distinguish config storage from executing background behaviors. | Execution needs integration tests proving side effects; otherwise config-only/stubbed. |
| Advanced/specialized APIs (`SelectObjectContent`, `RestoreObject`, torrent, Object Lambda, directory buckets/sessions) | S3-P4 / separate scope | Likely unsupported or separate scope unless implemented. | Documented unsupported/not-implemented responses or explicit scenarios with deviations. |
| Admin/storage-engine integration APIs | S3-P0..P4 | Not AWS S3 APIs; must not become a parallel object API. Read-only catalogs + validation today; backend status planned. | Admin API tests prove catalog/validation behavior; docs keep admin APIs separate from S3 coverage. |
| Batch object operations: `DeleteObjects` | S3-P1/S3-P2 | Multi-delete semantics: per-key success/error reporting, quiet mode, partial-failure result document. Mapped and nominally working today but previously unplanned. | Semantic scenarios cover mixed success/error batches, quiet mode, and per-key error entries in both runner modes. |
| Rename: `RenameObject` (`x-amz-rename-destination`) | S3-P4 / explicit-extension scope | Non-core-AWS general-purpose S3 (S3 Express-derived). Requires a keep/reclassify/remove decision plus documented semantics (copy+delete today). | Decision recorded (ADR or matrix note); if kept, semantics documented and tested; if removed, route retired. |
| S3 Metadata tables (bucket metadata configuration, metadata table, inventory table, journal table configurations) | S3-P4 | Currently handler-local `ConcurrentHashMap` state in `S3BucketConfigHandler` (see section D critical finding). Classify `@placeholder` until repository-backed; **no background table generation may ever be claimed without jobs.** Planned real usage per KA-3(e): S3 Metadata configuration on general-purpose buckets emitting into table buckets — becomes the S3 Metadata bridge once the table-store BC exists. | Config CRUD moves behind repository ports; status stays `@placeholder`/`@config-only` until then; background generation requires tested jobs. |
| Non-standard extensions: bucket ABAC (`getBucketAbac`/`putBucketAbac`), `updateObjectEncryption`, `ListBuckets` JSON | Explicit decision | Each must be (i) reclassified as a documented Magrathea extension with its own requirements, (ii) hidden behind a feature flag, or (iii) removed. They must not silently masquerade as AWS S3 APIs. | Per-operation decision recorded; extension docs/flag/removal implemented and tested accordingly. |

### Required S3 Semantic Reporting Table Template

| Family | Operation | Route mapped | Stateful behavior | AWS CLI scenario | Storage-engine scenario | Semantic status | Notes |
|---|---|---|---|---|---|---|---|
| Example: Object CRUD | `PutObject` | Yes/No | Mapped / Stubbed / Stateful | Pass / Fail / Missing / N.A. | Pass / Fail / Missing / N.A. | Mapped / … / Semantically S3-compatible | Scope limits, deviations, linked tests. |

### S3 Priority Roadmap

| Priority | Correction focus | Status / gate |
|---|---|---|
| S3-P0 | Replace `111/111` claims with a semantic matrix; inventory all operations. | ✅ Closed 2026-07-11 by generated `docs/api-coverage.md` and `REQ-DOC-001`: 111 canonical official operations, 108 with mapped router handlers, 20 with explicit operation-linked `@implemented-and-validated` evidence, and 91 still ineligible for a 100% completion claim. |
| S3-P1 | Object CRUD + bucket baseline against both backends. | ⚠️ First AWS CLI CRUD/bucket increments complete (CC-9); metadata/checksum/copy semantics, fuller list semantics, and storage-engine scenarios remain. |
| S3-P2 | Multipart + object metadata/tagging/ACL persistence. | Open; multipart part persistence/assembly is EP-3. |
| S3-P3 | Bucket configuration statefulness; versioning/delete markers. | Open. |
| S3-P4 | Authorization/enforcement/background jobs/advanced APIs. | Open; authorization/enforcement is EP-1. |

### Ancillary S3 API Requirements Backlog (planned)

Planner-verified 2026-07-02: no requirement feature file covers the ancillary bucket-configuration families — only phase-1..6 requirement features exist, and phase-5 REQ-S3-001..007 covers ETag/multipart/Range/conditional/copy/tagging/unsupported-classification only. The legacy `object-store/bucket-config.feature` contains 148 status-code-only scenarios (protocol-smoke class per AGENTS.md A.5) that are **not** tagged `@protocol-smoke`.

Planned requirement feature file: **`phase-7-s3-ancillary-config.feature`** — `Business Need`, shared WebTestClient + AWS CLI validation modes per INV-2, requirement IDs **REQ-S3-CFG-\***, one `Rule` per family: CORS, policy, encryption-config, lifecycle-config, logging, website, notification, replication-config, requestPayment, ownershipControls, publicAccessBlock, accelerate, analytics/inventory/metrics/intelligentTiering, metadata tables, ABAC-or-removal, object-lock config.

Honest status at authoring time (no status may be invented):

- `@config-only` — families persisted through `ReactiveBucketService`/bucket repository.
- `@placeholder` — the 6 handler-local families held in `S3BucketConfigHandler` `ConcurrentHashMap`s (ABAC, object-lock config, metadata config, metadata-table, inventory-table, journal-table). The metadata/metadata-table/inventory-table/journal-table families are planned for real S3 Metadata usage bridging into the table-store BC — see KA-3(e).
- `@not-implemented` — all enforcement/background behavior: CORS enforcement, lifecycle execution, replication execution, notification delivery, website serving, logging delivery, analytics/inventory generation.

Task list:

- [ ] Write `phase-7-s3-ancillary-config.feature` per the structure above (`java-tester`).
- [ ] Tag the 148 legacy `bucket-config.feature` status-code scenarios `@protocol-smoke` (`java-tester`).
- [x] Move the 6 handler-local config families behind repository ports (`java-infra-coder`; feeds EP-2).
- [x] Generate `docs/api-coverage.md` as a machine-generated semantic matrix using the Required S3 Semantic Reporting Table Template, wired into CI freshness checks and `REQ-DOC-001`. The conservative baseline closes S3-P0 inventory without claiming that `pending-evidence` operations are absent or complete.

### S3 Semantic Acceptance Criteria (summary)

- No operation documented as implemented without semantic classification and passing stateful tests.
- AWS CLI parity and storage-engine compatibility are separate marks; neither is implied by the other or by WebTestClient results.
- Config APIs distinguish `config-only`/`enforced`/`unsupported`/`stub`; metadata-only behavior is never described as enforcement.
- Background behavior (lifecycle, replication, notifications, analytics, tiering, restore) is not claimed without a tested background mechanism.
- Final completion claims are family-based and evidence-based, never route-count-based.

## Enterprise Production Readiness Plan (EP-0 .. EP-11)

This section defines the path from "validated prototype with honest gaps" to an enterprise-deployable object store. All EP phases start as `@absent` or `@partial` — **no completion claims are made here**.

### Binding Cross-Cutting Invariants

- **INV-1 — Reactive-first.** Every new production path must remain reactive/non-blocking end to end: Reactor, bounded memory, no `DataBufferUtils.join` or whole-body materialization, blocking I/O only on `boundedElastic`. Static architecture tests (pattern: `ReactiveUploadStreamingArchitectureTest`) must guard each newly fixed path.
- **INV-2 — Cucumber-first requirements.** Every EP phase MUST begin by writing/refreshing shared Gherkin requirement features (per AGENTS.md): requirement IDs, functional/non-functional tags, honest status tags, and WebTestClient + AWS CLI (or protocol-appropriate CLI) validation modes reusing the same shared feature text.
- **INV-3 — Admin panel is a first-class product deliverable.** A COMPLETE admin panel (not just read-only catalogs) is in scope — but it must never become an alternate object API (AGENTS.md B.3).
- **INV-4 — S3 is the internal core.** Any additional protocol facade (WebDAV, SMB, future protocols) is an optional external adapter that MUST delegate to the same reactive object-store application services (`ReactiveObjectService` and friends) — internally everything remains S3. No facade or internal transport (including gRPC) may talk to the storage engine directly, introduce a parallel persistence path, or become an alternate public object API.
- **INV-5 — Self-contained by default, Maven-executable.** Every capability that can integrate external infrastructure (Kafka/NATS, Prometheus/Grafana, Elasticsearch, external KMS, LDAP/AD/Kerberos KDC, external CA) MUST ship a simplified built-in backend (embedded event-delivery log, built-in metrics view in the admin panel, file-based audit store, local key store, local CA fallback) so the functionality is fully demonstrable without external infrastructure; external adapters are optional plugins. All gates, validations, and benchmarks must be executable via Maven wherever possible (containerized steps invoked from Maven), supporting air-gapped/offline installation — a hard requirement for the government/military target.
- **INV-6 — Conditional chunking only.** Chunks are storage artifacts only for multipart uploads, deduplication windows, and erasure-coding stripes/shards. A plain single-object write with multipart, deduplication, and erasure coding all disabled remains one streamed whole-object storage unit; it must not be split, named, reported, reference-counted, or garbage-collected as generic chunks. Manifests, recovery, GC, quotas, metrics, and Admin reports must distinguish whole-object units, multipart parts, dedup chunks, and EC artifacts.

### EP-0 — Governance: Definition of Production Ready (PRIORITY: foundation)

| Field | Plan |
|---|---|
| Focus | Single measurable exit checklist aggregating all EP gates; evidence-based reporting rules. |
| Owner agents | `java-planner` + `documenter` |
| Requirement feature file | n/a (governance; gates aggregate from EP-1..EP-11 feature files) |
| Expected outputs | Definition of Production Ready checklist (below) maintained in this plan; reporting rules binding all EP phases to the Baseline Evidence discipline. |
| Acceptance gates | No "production-ready" claim anywhere until all **Blocker** EP phases are `@implemented-and-validated`. |
| Status | `@absent` (checklist seeded below, unchecked) |

### EP-1 — Security & Identity (BLOCKER)

| Field | Plan |
|---|---|
| Focus | Authentication, authorization, audit, and real encryption. Opt-in secured mode is wired through Spring Security Reactive (`SecurityWebFilterChain`, SigV4 `ServerAuthenticationConverter`, reactive authentication/authorization managers, and S3 XML security handlers) for all current EP-1 WebTestClient and EP-1 AWS CLI/e2e scenarios. It verifies SigV4 Authorization headers for configured access keys, verifies exact payload hashes, rejects anonymous/unknown-key/bad-signature/stale-date/payload-hash-mismatch requests before route mutation, enforces deny-by-default/explicit-deny, denies PublicAccessBlock/public ACL access, denies expected bucket owner mismatch, records durable redacted tamper-evident audit events, and proves an SSE-S3 encrypted-at-rest inspection slice using durable local key-management material. The built-in local credential store encrypts secrets at rest and supports revocation; the durable policy store reloads allow/deny decisions; the audit file is append/fsync-based with hash-chain integrity. Unsecured mode remains the default trusted-environment mode and is explicitly permit-all. |
| Owner agents | `java-domain-coder` (policy model), `java-infra-coder` (filters/adapters), `java-tester` |
| Requirement feature file | `s3-reactive-api-adapter/src/test/features/requirements/phase-ep1-security-identity.feature` |
| Key requirement IDs | REQ-SEC-* |
| Expected outputs | AWS SigV4 request authentication; durable credential/access-key store; deny-by-default authorization with bucket policy/ACL evaluation; audit logging of requests; SSE-S3 with pluggable key management; TLS deployment guidance. |
| Acceptance gates | Unauthenticated/incorrectly signed requests rejected; deny-by-default proven by tests; ACL/policy enforcement validated in both runner modes; audit events observable and durable; SSE round-trip proven at rest, not just headers. |
| Status | `@implemented-and-validated` for the declared EP-1 local built-in scope. REQ-SEC-001 through REQ-SEC-009 are validated by both `PhaseEp1SecurityIdentityRequirementsCucumberTest` and `PhaseEp1SecurityIdentityAwsCliCucumberTest`; `specs/sigv4-verifier.feature` preserves verifier/filter component evidence; `specs/ep1-security-services.feature` validates durable encrypted credentials/revocation (REQ-SEC-010), durable allow/deny policy reload (REQ-SEC-011), durable tamper-evident audit (REQ-SEC-012), and durable key-management/SSE encryption material (REQ-SEC-013). ADR 0023 Spring Security Reactive backbone carries the slice. External IdP/KDC/OIDC/LDAP/Kerberos federation remains KA-4 future scope, not an EP-1 blocker. |

KA dependencies: KA-2 (Ceph s3-tests requires SigV4), KA-3 (presigned URLs), and KA-4 (identity federation incl. Kerberos) all depend on EP-1 — this raises EP-1's practical priority; identity work lands in the planned identity-access bounded context (see Bounded Context Evolution under the Killer-App Track).

### EP-2 — Complete Metadata Durability (BLOCKER)

| Field | Plan |
|---|---|
| Focus | Close the in-memory metadata gap (see the qualified checklist items in CC below): bucket registry, multipart upload state, per-object configuration metadata, object tags/ACLs, and bucket configuration families must survive restart in storage-engine mode. **Extended scope (2026-07-02):** the 6 handler-local bucket-config families in `S3BucketConfigHandler` (ABAC, object-lock config, metadata config, metadata-table, inventory-table, journal-table — see section D) and bucket configuration state generally must move behind repository ports AND become durable in storage-engine mode. |
| Owner agents | `java-infra-coder` primary; `java-domain-coder`; `java-tester` |
| Requirement feature file | `phase-ep2-metadata-durability.feature` |
| Key requirement IDs | REQ-DUR-* |
| Expected outputs | Durable bucket registry, durable multipart upload state, durable per-object configuration metadata (ACL, tags where not already durable, object lock, retention, legal hold, encryption config, restore state) in storage-engine mode; the 6 handler-local bucket-config families relocated behind repository ports and made durable, plus durable bucket configuration state generally. |
| Acceptance gates | Restart-safety Cucumber scenarios per state family using the bootstrap restart harness pattern of REQ-UPLOAD-001/002; state survives full process stop/start. |
| Status | `@implemented-and-validated` for the declared storage-engine durability scope — **closed 2026-07-10**: bucket registry (full `BucketConfig` document), multipart upload state (upload id + recorded parts), per-object configuration (legal hold, lock config, retention, encryption, restore), object tags, object ACL sidecars, and bucket configuration families are durable under the storage-engine filesystem metadata root. **All 6 handler-local bucket config families eliminated**: object-lock, inventory-table, journal-table (new `BucketConfig` VOs) plus ABAC, metadata config and metadata-table (existing VOs remodelled to the S3 API shape) now live on the `Bucket` aggregate and persist through `updateBucket` → `BucketStore`; `S3BucketConfigHandler` holds no configuration state. Validated by `MetadataDurabilityRestartTest` (7 restart-simulation unit tests incl. `$BucketConfigFamilies`), upgraded `StorageEngineRestartSafetyTest` (real context restart evidence), `PhaseEp2MetadataDurabilityCucumberTest` (3 selected WebTestClient restart-simulation scenarios), and `PhaseEp2MetadataDurabilityFullRestartCucumberTest` (18 full Spring stop/start scenarios: bucket registry, multipart state, legal hold, object lock configuration, retention, encryption, restore, tags, ACL, CORS, notification, bucket object-lock, inventory-table, journal-table, ABAC, metadata, metadata-table, and combined metadata). The remaining EP-2 scenarios are explicit `@in-memory-exemption` checks and are not storage-engine durability gaps. Test-glue restart semantics split: `reset()` (wipe) vs `reloadFromDisk()` (restart simulation). |

### EP-3 — Reactive Streaming Completion (BLOCKER)

| Field | Plan |
|---|---|
| Focus | (a) GetObject read path streams without `collectList()` — full-body and Range requests served with bounded memory; (b) multipart `uploadPart` and `uploadPartCopy` persist part bodies through the part store without `DataBufferUtils.join`, and `completeMultipartUpload` assembles the real object; (c) static architecture Cucumber specs forbid `join`/`collectList` regressions in the fixed paths; (d) remaining future work is runtime/backpressure validation for the broader staged pipeline. |
| Owner agents | `java-infra-coder`, `java-domain-coder`, `java-tester` |
| Requirement feature file | extend `phase-3-reactive-pipeline.feature` + new `phase-ep3-multipart-streaming.feature` |
| Key requirement IDs | REQ-PIPELINE-009+, REQ-MPU-* |
| Expected outputs | Streaming GetObject (full + Range); streaming multipart part persistence and real assembly; extended `ReactiveUploadStreamingArchitectureTest`-style guards. |
| Acceptance gates | Large-object read/multipart scenarios pass in both runner modes with bounded memory; architecture test forbids regressions; completed multipart object reads back as assembled bytes. |
| Status | `@partial` (write path static streaming constraints `REQ-PIPELINE-007/008`, non-range GetObject streaming `REQ-PIPELINE-009`, ranged GetObject streaming `REQ-PIPELINE-010`, multipart UploadPart/UploadPartCopy and part-store static streaming constraints `REQ-PIPELINE-011/012`, completed multipart object assembly `REQ-S3-002-D`, UploadPartCopy copied-byte assembly `REQ-S3-002-F`, multipart XML error semantics `REQ-S3-002-G..K`, and multipart part-body full-process restart completion `REQ-S3-002-E` are `@implemented-and-validated`. The staged-pipeline Cucumber runner now validates REQ-PIPELINE-001 canonical UUID/checksum publication and post-object-commit content-address publication in pipeline-unit and WebTestClient modes, REQ-PIPELINE-004 filesystem cleanup for chunk, manifest, and upstream-body failures, REQ-PIPELINE-005 pipeline-unit plus client-driven loopback HTTP cancellation cleanup owned by Reactor `usingWhen`, and REQ-PIPELINE-006 instrumentation. REQ-PIPELINE-002 is implemented-and-validated in pipeline-unit and WebTestClient modes: both execute a deterministic 256 MiB upload through the production staged pipeline and real filesystem with measured live source-buffer retention, bounded request signals, manifest/order/length checks, exact streamed readback, and anti-aggregation guards. The WebTestClient mode selects the dedicated 1 MiB-chunk `PIPELINE` test policy through `x-amz-storage-class`; the adapter requests three upstream buffers per batch so transport plus active processing remains within the declared four-buffer ceiling. REQ-PIPELINE-005 is implemented-and-validated in pipeline-unit and WebTestClient-runner modes; REQ-PIPELINE-003/004 remain partial. REQ-PIPELINE-003 now executes the declared deterministic 256 MiB fixture in both modes without test-side whole-object retention: pipeline-unit proves first-chunk/backpressure behavior with incremental SHA-256 readback, while WebTestClient streams the response into an incremental digest after a bounded integrity preflight. The HTTP mode preserves deterministic REQ-FS-003/004 XML errors but does not claim first-byte latency or single-pass filesystem I/O. `REQ-PIPELINE-013` supplies finite-demand adapter-boundary evidence only. ADR 0025 and new REQ-PIPELINE-014/015 correct the storage taxonomy: plain writes must remain whole-object units, while only multipart, dedup, and EC may produce chunks/shards. REQ-PIPELINE-014 is implemented-and-validated in pipeline-unit and WebTestClient modes: a plain 8 MiB upload remains one `FileUnit`, records `whole-object-pass-through`, writes a schema-2 typed `WHOLE_OBJECT` manifest, persists only under `whole-objects`, and reads back exactly; schema-0/1 chunk-only manifests remain readable as `LEGACY_CHUNK`. REQ-PIPELINE-015 is implemented-and-validated in both modes: an 8 MiB EC_4_2 upload creates two bounded 4 MiB stripes, eight ordered data shards and four parity shards with typed manifests, checksum validation, and exact readback. The remaining EP-3 blocker is the GetObject integrity-preflight latency/single-pass trade-off: the current deterministic pre-commit XML integrity contract performs a full bounded preflight followed by the response read.) |

### EP-4 — Space Management & Data Hygiene (HIGH)

| Field | Plan |
|---|---|
| Focus | Evidence: no segmented-artifact GC/reference counting exists (only `FileSystemRecoveryScanner`, on-demand); no ENOSPC behavior; no quotas; no background scrubbing. GC must obey INV-6: multipart parts, dedup chunks, and EC artifacts are chunk-scoped, while plain uploads reclaim whole-object units without inventing chunks. |
| Owner agents | `java-domain-coder`, `java-infra-coder`, `java-tester` |
| Requirement feature file | `specs/phase-ep4-space-management.feature` for internal artifact lifecycle/scrubbing plus `requirements/phase-ep4-capacity-protection.feature` for S3/Admin quota and ENOSPC behavior |
| Key requirement IDs | REQ-GC-*, REQ-QUOTA-* |
| Expected outputs | Type-aware reclamation for whole-object units and segmented artifacts; chunk garbage collection for multipart/dedup/EC only; dedup reference counting; defined disk-full (ENOSPC) behavior; per-bucket/tenant quotas; periodic integrity scrubbing job. |
| Acceptance gates | Deleted-object chunks reclaimed without corrupting shared dedup chunks; quota enforcement observable via S3 errors; scrubbing findings reported/quarantined; ENOSPC yields defined S3 errors, not corruption. |
| Status | `@not-implemented`: ADR 0025 and Cucumber requirement baselines now define conditional chunking, typed whole-object/multipart/dedup/EC artifacts, reclamation, scrubbing, quotas, and ENOSPC behavior. Production artifact-taxonomy migration and all EP-4 runtime behavior remain open. |

### EP-5 — Operability & Delivery (HIGH)

| Field | Plan |
|---|---|
| Focus | First CI/operability slice now exists: appendix/source-hygiene checks, focused Cucumber/security/semantic/EP-5 gates, root JVM Docker build+smoke, manual native Docker build+smoke, packaged single-node containers using storage-engine by default, Admin API liveness/readiness probes, SIGTERM shutdown validation for committed state plus single, multipart, and two-request concurrent streaming writes, first offline backup/restore rehearsal, first single-node DR rehearsal with declared RTO/RPO, and first SLO/alerting rule bundle. Broader EP-5 operability remains open. |
| Owner agents | `java-scaffolder`, `java-infra-coder`, `documenter`, `java-tester` |
| Requirement feature file | `s3-reactive-api-adapter/src/test/features/requirements/phase-ep5-operability.feature` (Business Need; remaining procedures stay tagged `@not-implemented` until automated) |
| Key requirement IDs | REQ-OPS-* |
| Expected outputs | CI pipeline running the full gate + appendix check + docker build; release/versioning strategy; backup/restore procedure; DR with declared RTO/RPO; enforced schema/manifest versioning and migration; runbooks; SLOs and alert rules; readiness/liveness probes beyond `/admin/health`; verified graceful shutdown draining. |
| Acceptance gates | CI runs green on the full gate; backup/restore rehearsed; probes and shutdown behavior validated; versioning/migration enforced by tests. |
| Status | `@partial`: CI packaging gates are wired; packaged JVM/native single-node containers activate storage-engine and package YAML catalogs so `/admin/ready` is expected to be ready; Admin API `/admin/live` + `/admin/ready` are implemented and Cucumber-validated as `REQ-OPS-001..003`, including fail-closed readiness when required catalogs are unavailable; `REQ-OPS-004` validates that a storage-engine process exits after SIGTERM without forced termination and preserves a committed S3 object across restart; `REQ-OPS-009` validates that SIGTERM drains an active 524,288-byte streaming PutObject before exit and preserves its byte count/checksum across restart; `REQ-OPS-010` drains an active multipart UploadPart, captures its ETag, and completes the multipart upload after restart; `REQ-OPS-011` drains two concurrent PutObjects and verifies both objects after restart; `REQ-OPS-012` drains an active CompleteMultipartUpload and verifies the assembled object after restart; `REQ-OPS-013` cancels an active UploadPart, aborts its upload before SIGTERM, and verifies after restart that no object, active upload, or part artifacts remain; `REQ-OPS-014` overlaps abort with an active CompleteMultipartUpload during SIGTERM and validates the abort-wins terminal state; `REQ-OPS-015` drains a bounded mixed load of three streaming writes and two throttled reads and verifies all objects after restart; `docs/runbooks/graceful-shutdown.md` documents both procedures; `REQ-OPS-005` validates offline storage-root backup/restore after simulated primary data loss and `docs/runbooks/backup-restore.md` documents the procedure scope; `REQ-OPS-006` validates declared single-node offline DR objectives (RTO 30 seconds, RPO last completed offline backup) and `docs/runbooks/disaster-recovery.md` documents the rehearsal; `REQ-OPS-007` validates object-manifest schema versioning/migration compatibility; `REQ-OPS-016` extends the same current/legacy/future-version contract to durable multipart upload sessions; `REQ-OPS-017` extends it to the bucket registry; `REQ-OPS-018` extends it to durable per-object configuration; `REQ-OPS-019` extends it to S3 object manifest references; `REQ-OPS-020` extends it to object ACL sidecars, completing explicit schema contracts for the declared EP-2 durable metadata families, and `docs/runbooks/schema-migration.md` documents all six contracts; `REQ-OPS-008` validates a shipped Prometheus/Loki SLO and alerting starter bundle with `docs/runbooks/slo-alerts.md`; `REQ-OPS-021` opt-in Docker validation proves live Prometheus evaluation and Alertmanager webhook delivery; future newly introduced metadata-family schema contracts, live Loki/external paging delivery, broader runbooks, online/incremental DR, multi-node DR, larger sustained and production-scale load validation plus repeated contention covering completion-wins multipart races remain. |

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

### EP-8 — Cluster Architecture ADR & Supply Chain (MEDIUM)

The product owner has decided the deployment target: a **real multi-node S3 cluster**. The EP-8 ADR no longer decides *whether* to go distributed but *how*. EP-8 is a hard prerequisite of EP-10.

| Field | Plan |
|---|---|
| Focus | Cluster architecture ADR (how, not whether) plus supply-chain hardening (scope unchanged). |
| Owner agents | `documenter` (ADR), `java-scaffolder`, `java-tester` |
| Requirement feature file | `phase-ep8-supply-chain.feature` (where executable) |
| Key requirement IDs | REQ-HA-*, REQ-SUPPLY-* |
| Expected outputs | ADR deciding: inter-node transport (see the gRPC evaluation under EP-10); membership/discovery model (static seed list first vs gossip); metadata consistency model (e.g. quorum reads/writes per PA-6 `QuorumPolicy` vs consensus/Raft for the bucket/reference metadata plane); failure-domain topology mapping onto the existing storage-device/disk-set YAML catalogs. Supply chain unchanged: SBOM generation; dependency CVE scanning; container image hardening (non-root); license compliance report. |
| Acceptance gates | ADR accepted (gates EP-10 start); SBOM/CVE scan/image hardening wired into the EP-5 pipeline; license report generated. |
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

Owner rule (2026-07-02): the WebDAV adapter sets the pattern for optional facades — any gRPC used by the SMB/VFS gateway (EP-11) follows the same rules: optional relative to the project, internal-only, thin delegation (INV-4). The EP-10 cluster gRPC is different in nature: it exists to synchronize nodes and is part of the cluster core (still internal-only).

### EP-10 — S3 Cluster (Multi-Node) (HIGH)

| Field | Plan |
|---|---|
| Focus | Evolve from single instance to a real multi-node S3 cluster: turn the PA-6 modeled domain (`DistributedPlacementPlanner`, `QuorumPolicy`, `AntiEntropyPlanner`, `RebalancePlanner`, `DistributedReadinessReporter` — already unit-validated) into executed networked behavior: node membership + health, replica/EC-shard placement across failure domains, quorum writes/reads, manifest/metadata replication, anti-entropy healing execution, rebalance execution. |
| Owner agents | `java-domain-coder` (consistency/placement semantics), `java-infra-coder` (gRPC transport, membership), `java-scaffolder` (modules/protobuf build), `java-tester` (multi-node Cucumber harness) |
| Requirement feature file | `phase-ep10-s3-cluster.feature` |
| Key requirement IDs | REQ-CLUSTER-* (`Ability` for internals: membership, replication, quorum, healing, rebalance; `Business Need` for externally observable cluster behavior: S3 write on node A / read on node B, node-failure read availability) |
| Validation modes | Multi-node e2e via docker-compose/Testcontainers (2–4 nodes) + AWS CLI against the cluster endpoint; fault-injection scenarios (node kill during PUT, quorum loss, partition) as Cucumber scenarios; INV-2 applies. |
| Planned modules | PLANNED in the module map: `storage-engine-cluster-application` (ports: membership, replica transport, sync); `storage-engine-cluster-grpc-infrastructure` (internal gRPC transport adapters, protobuf contracts). |
| Prerequisites | EP-2 (durable metadata — replicating in-memory maps is meaningless), EP-3 (streaming), EP-1 (mTLS/identity for nodes), EP-8 ADR accepted. |
| Acceptance gates | Cluster behavior proven only by multi-node e2e + fault-injection scenarios; no distributed claims beyond validated scope. |
| Status | `@absent` |

Inter-node transport — gRPC evaluation (input to the EP-8 ADR):

- **PRO gRPC:** HTTP/2 multiplexing; client/server/bidirectional streaming fits chunk and EC-shard transfer; typed protobuf contracts with schema evolution; built-in deadlines/retries/keepalive; mTLS for node-to-node authentication (ties into EP-1); mature Java ecosystem.
- **CONSTRAINT (INV-1):** must use reactive bindings (e.g. reactor-grpc style) so Reactor backpressure propagates across the wire; no blocking stubs on event loops.
- **Alternatives the ADR must compare:** RSocket (native reactive backpressure semantics); plain HTTP/2 between nodes (reuses the existing stack).
- **BOUNDARY RULE:** the inter-node gRPC API is INTERNAL ONLY — a cluster transport, never a public object API; it must not bypass the storage-engine ACL or become a third external facade. Same governance as AGENTS.md B.3.

### EP-11 — SMB Gateway: Samba VFS C Module (FUTURE, OPTIONAL)

| Field | Plan |
|---|---|
| Focus | Expose Magrathea shares via SMB by implementing a Samba VFS module in C (`vfs_magrathea`) loaded by `smbd`, mapping SMB file operations onto object-store semantics — same product constraints as WebDAV (EP-9): optional, disabled by default, internally everything remains S3. |
| Owner agents | C development is NOT owned by the Java workflow — requires the C workflow/agents (`c-development` workflow, `c-coder`/native agents in the multi-agent skill), exactly like the frontend handoff for EP-7. The Java workflow owns: the optional internal gateway adapter (if option b), contracts, and requirement features. |
| Requirement feature file | `phase-ep11-smb-gateway.feature` |
| Key requirement IDs | REQ-SMB-* (`Business Need`, `@smb-gateway` tag) |
| Validation modes | `smbclient` CLI runner against a containerized `smbd`+module plus S3-side state inspection through the S3 API; C module unit tests (e.g. cmocka) owned by the C workflow; INV-2 applies with protocol-appropriate runners. |
| Mapping sketch | SMB share → bucket (or bucket+prefix); files → objects; directories → key prefixes (no real hierarchy — same approach as WebDAV collections); SMB attributes beyond S3 semantics → S3 user metadata or rejected; locking/oplocks likely `@not-implemented` initially and documented. |
| Planned artifacts | PLANNED in the module map: `smb-vfs-module/` (C, Samba VFS, own build — CMake/autotools in container); optionally `object-gateway-grpc-adapter` (internal, decision-gated by the EP-11 start ADR). |
| Prerequisites | EP-1 (auth/credentials for the gateway), EP-3 (streaming read path). Independent of EP-10 (works single-node). |
| Acceptance gates | All binding constraints below met, including the internal-only gateway rule; INV-4 preserved by whichever backend the ADR selects. |
| Status | `@absent` |

EP-11 backend decision — a C module inside `smbd` cannot call the Java application services in-process, so a network boundary is mandatory. The EP-11 start ADR must choose between two backends, both preserving INV-4, decided at phase start with measurements:

- **(a) VFS module → local S3 HTTP API (libcurl + SigV4):** zero new API surface, INV-4 literally preserved; costs: SigV4 signing in C, XML overhead, SMB metadata chattiness (getattr/stat storms) over HTTP+XML.
- **(b) VFS module → dedicated INTERNAL gRPC protocol-gateway** (grpc C-core client → a thin Java gRPC adapter that delegates exclusively to `ReactiveObjectService`/`ReactiveBucketService`): binary framing + streaming reads/writes suit file I/O, much cheaper for SMB's chatty metadata ops; cost: a new internal API surface that must be governed exactly like the cluster transport — internal only, thin delegation, never a parallel public object API, disabled unless the SMB gateway is enabled.
- The ADR may also **combine them** (metadata via gRPC, bulk data via S3) — recorded as an option.

Owner rule (2026-07-02): any gRPC used by the SMB/VFS gateway follows the same rules as WebDAV — optional relative to the project, internal-only, thin delegation (INV-4); the EP-10 cluster gRPC is different in nature: it exists to synchronize nodes and is part of the cluster core (still internal-only).

### EP Priority and Sequence

| Order | Phase | Priority | Status |
|---|---|---|---|
| 1 | EP-2 Metadata durability | Blocker | `@implemented-and-validated` for declared storage-engine scope (in-memory profile explicitly exempt) |
| 2 | EP-3 Reactive streaming completion | Blocker | `@partial` |
| 3 | EP-1 Security & identity | Blocker | `@implemented-and-validated` — current S3 security slice plus built-in durable credential, policy, audit, and key-management services validated |
| 4 | EP-4 Space management & data hygiene | High | `@absent` |
| 5 | EP-5 Operability & delivery | High | `@partial` |
| 6 | EP-6 Performance & capacity | High | `@absent` |
| 7 | EP-7 Complete admin panel (backend contracts may proceed in parallel with EP-1..EP-6) | High | `@partial` |
| 8 | EP-8 HA decision & supply chain | Medium | `@absent` |
| 9 | EP-10 S3 cluster (multi-node; after the EP-8 ADR is accepted) | High | `@absent` |
| 10 | EP-11 SMB gateway (optional; after EP-1 and EP-3; independent of EP-10) | Future | `@absent` |
| 11 | EP-9 WebDAV adapter (optional; any time after EP-1 and EP-3) | Future | `@absent` |

EP-0 governance applies continuously from the start.

### Definition of Production Ready (EP-0 checklist — all unchecked)

- [x] EP-1: SigV4 authentication, deny-by-default authorization, audit logging, and real SSE are `@implemented-and-validated` for the declared local built-in scope.
- [x] EP-2: all declared storage-engine metadata families (bucket registry, multipart state, per-object configuration, object tags/ACLs, and bucket configuration) survive restart in storage-engine mode, validated by restart harness scenarios.
- [ ] EP-3: GetObject and multipart paths stream with bounded memory; architecture tests guard all fixed paths. Pipeline-unit/WebTestClient Cucumber now covers selected stage ordering, canonical chunk publication, failure/cancellation cleanup, and instrumentation; REQ-PIPELINE-002 adds deterministic 256 MiB pipeline-unit and WebTestClient bounded-demand/live-buffer evidence, REQ-PIPELINE-005 adds client-driven loopback HTTP cancellation, REQ-PIPELINE-003 now runs the actual 256 MiB fixture with incremental readback in both modes, while `REQ-PIPELINE-013` supplies finite-demand adapter-boundary evidence. The GetObject integrity-preflight latency/single-pass trade-off remains outstanding, so this gate is not closed.
- [ ] EP-4: chunk GC, dedup refcounting, quotas, ENOSPC behavior, and scrubbing are validated.
- [ ] EP-5: CI pipeline green on full gate + appendix check + docker build; offline backup/restore, SIGTERM shutdown validation covers committed state plus single PutObject, multipart UploadPart, CompleteMultipartUpload, cancelled-and-aborted UploadPart cleanup, abort-wins overlapping multipart completion, two-request concurrent PutObject streams, and a bounded five-request mixed read/write load, first single-node DR RTO/RPO rehearsal, object-manifest, multipart-session, bucket-registry, object-configuration, object-reference, and object-ACL schema-versioning, first SLO/alerting rule bundle, and live Prometheus-to-Alertmanager webhook delivery exist; online/multi-node DR, live Loki/external paging delivery, schema contracts for any future durable metadata families, larger sustained/production-scale load validation and repeated completion-wins multipart race evidence still pending.
- [ ] EP-6: load/soak limits documented and reproducibly validated.
- [ ] EP-7: complete admin panel delivered and validated; panel is not an alternate object API.
- [ ] EP-8: cluster architecture ADR (transport, membership, consistency model, failure-domain topology) accepted; SBOM/CVE/image hardening/license gates wired into CI.
- [ ] EP-9 (optional): WebDAV adapter, if built, meets all binding constraints.
- [ ] EP-10: cluster behavior (membership, quorum, failover, healing, rebalance) validated by multi-node e2e scenarios; no distributed claims beyond validated scope.
- [ ] EP-11 (optional): SMB gateway, if built, meets all binding constraints including the internal-only gateway rule.
- [ ] All claims above are backed by evidence per the Baseline Evidence discipline; no status inferred from route inventories or smoke checks.

## Killer-App Track (KA-1 .. KA-6)

The EP track makes Magrathea production-ready; the KA track makes it the killer app among S3-compatible object stores (MinIO, Ceph RGW, Garage, SeaweedFS). KA phases attach to EP prerequisites and never bypass INV-1..INV-5 or the evidence discipline. All KA capabilities start `@absent` — **no completion claims are made here**. Owner-decided target audiences: enterprise, single user, AND government/military — legacy integration paths are first-class. A second Magrathea-suite project will be PKI; its integration point is planned in the Bounded Context Evolution subsection below.

Market-window note (factual): MinIO removed features from its community console, and its AGPL license creates adoption friction for some organizations; Magrathea's MIT license (KA-1) and complete admin panel (EP-7/INV-3) target that window. Embryonic differentiators already present: YAML policy-driven storage classes; the complete admin panel; the unique WebDAV (EP-9) + SMB (EP-11) gateway combination; executable Gherkin requirements as living compliance evidence.

### KA-1 — Positioning & Licensing (IMMEDIATE, low cost)

| Field | Plan |
|---|---|
| Focus | Public positioning and licensing. **OWNER DECISION: the license is MIT.** |
| Owner agents | `documenter`, `java-planner` |
| Requirement feature file | n/a (documentation/governance) |
| Expected outputs | MIT `LICENSE` file at the repository root; licensing ADR recording the owner decision; `docs/positioning.md` naming the four differentiators (YAML policy-driven storage classes, complete admin panel, WebDAV+SMB gateway combination, executable Gherkin requirements as living compliance evidence) vs MinIO/Ceph RGW/Garage/SeaweedFS plus the government/military positioning (self-contained, air-gapped, legacy protocol paths, PKI-suite integration); public roadmap derived from this plan. |
| Acceptance gates | `LICENSE` present (closes the Section G stale link); ADR accepted; positioning stays factual — no FUD; roadmap makes no claims beyond this plan's honest statuses. |
| Prerequisites | None — immediate. |
| Status | `@partial` — MIT `LICENSE` at the repository root and licensing ADR (`docs/adr/0019-adopt-the-mit-license.md`, Accepted) delivered 2026-07-03; `docs/positioning.md` and `docs/roadmap.md` published 2026-07-03. |

### KA-2 — Ecosystem Conformance

| Field | Plan |
|---|---|
| Focus | Prove compatibility with the existing S3 ecosystem — **owner decision: the tool must work first with existing tools** before any own tooling is built (see KA-5). |
| Owner agents | `java-tester` primary; `java-infra-coder`, `java-scaffolder` |
| Requirement feature file | `phase-ka2-ecosystem-conformance.feature` |
| Key requirement IDs | REQ-COMPAT-* |
| Expected outputs | Ceph s3-tests wired as a recurring Maven-invoked containerized compatibility gate with honestly tagged known-failures (the pass-rate matrix becomes public evidence, feeding KA-6); SDK/tool matrix (boto3, aws-sdk-java/go/js, rclone, s3cmd, mc) as executable scenarios where feasible; full checksum matrix (CRC32/CRC32C/SHA-1/SHA-256/CRC64NVME — new AWS SDKs default to CRC64NVME). |
| Acceptance gates | s3-tests gate runs via Maven (containerized per INV-5); known-failures explicitly tagged, never hidden; checksum matrix validated in both runner modes; no compatibility claim without an executed gate. |
| Prerequisites | Minimal EP-1 — s3-tests requires SigV4 (raises EP-1 priority). |
| Status | `@absent` |

### KA-3 — Data-Lake Readiness & S3 Tables (HIGH VALUE)

| Field | Plan |
|---|---|
| Focus | (a) Conditional writes on PUT: `If-None-Match: *`/`If-Match` on `PutObject` and `CompleteMultipartUpload` — the Apache Iceberg commit primitive (AWS added it 2024). Planner-verified absent (2026-07-02): `S3ObjectOperationsHandler.evaluateConditionals` runs only on GET/HEAD; `putObject` never calls it. (b) Presigned URLs (GET/PUT, `X-Amz-Expires` validation) — absent today (only header names in `S3Header`); depends on EP-1 SigV4. (c) Validated Iceberg/Spark/Trino workloads as executable scenarios. |
| S3 Tables decision (owner + planner) | S3 Tables IS a killer feature and gets a SEPARATE adapter: a new `s3tables-api-adapter` module distinct from `s3-reactive-api-adapter`/`S3PathRouter` (in AWS, S3 Tables is a separate API surface: table buckets, namespaces, tables, maintenance policies), backed by a NEW `table-store` bounded context (see Bounded Context Evolution below). It stays inside this repository as modules, not a separate project, because it reuses the object-store/storage-engine planes; revisit only if it outgrows the repo. |
| S3 Metadata bridge | The already-mapped bucket "metadata table configuration" routes belong to S3 METADATA (configuration on general-purpose buckets that emits into table buckets) and are planned for that real usage: they stay in the Ancillary S3 API Requirements Backlog as `@placeholder` until the table-store BC exists, then become the S3 Metadata bridge. |
| Owner agents | `java-domain-coder`, `java-infra-coder`, `java-scaffolder`, `java-tester` |
| Requirement feature file | `phase-ka3-datalake.feature` |
| Key requirement IDs | REQ-LAKE-*, REQ-TABLES-* |
| Acceptance gates | Conditional PUT/CompleteMultipartUpload semantics validated in both runner modes; presigned GET/PUT round-trips incl. expiry rejection; Iceberg/Spark/Trino scenarios pass against the storage-engine backend; table-store behavior proven through the separate adapter, never through object-store shortcuts. |
| Prerequisites | EP-1 (SigV4 for presigned URLs), EP-3 (streaming). |
| Status | `@absent` |

### KA-4 — Eventing, Tiering, Multi-Site, Federation (ENTERPRISE/GOV SELLERS)

| Field | Plan |
|---|---|
| Focus | Real notification delivery per INV-5: built-in embedded delivery backend FIRST (durable local event log + webhook dispatch, visible in the admin panel), then optional Kafka/NATS adapters as plugins — notifications are currently `@config-only` with no delivery mechanism (planner-verified: no webhook/Kafka/NATS code exists). Lifecycle execution (transitions/expiry) and cold-tier/remote-S3 tiering. Cross-cluster async multi-site replication (beyond EP-10 intra-cluster). Identity federation extending EP-1: OIDC + LDAP/AD + Kerberos/SPNEGO — **owner decision: Kerberos is required for legacy government/military environments**; PKINIT ties into the Magrathea PKI suite project. Built-in backends per INV-5: local credential store first; external IdP/KDC as optional adapters. WORM/Object Lock compliance-certification path (SEC 17a-4 style) building on object-lock metadata. |
| Owner agents | `java-domain-coder`, `java-infra-coder`, `java-tester`, `documenter` (ADRs) |
| Requirement feature file | `phase-ka4-eventing-tiering-federation.feature` |
| Key requirement IDs | REQ-EVENT-*, REQ-TIER-*, REQ-MULTISITE-*, REQ-FED-* |
| Acceptance gates | Delivery proven end to end through the built-in backend before any external-adapter claim; lifecycle/tiering side effects tested (config storage is never claimed as execution); multi-site validated by multi-cluster e2e; federation validated incl. at least one legacy-path (Kerberos) e2e. |
| Prerequisites | EP-1, EP-2, EP-4; EP-10 for multi-site replication. |
| Status | `@absent` (notifications `@config-only` today) |

### KA-5 — Distribution (OWNER-DECIDED PRIORITY ORDER)

| Field | Plan |
|---|---|
| Focus | (1) **SINGLE BINARY / self-contained artifact (top priority):** ADR evaluating GraalVM native-image vs jlink+CDS vs self-contained fat-jar, with honest trade-offs (native-image vs reflection/Netty/WebFlux); air-gapped offline install bundle; Maven-built. (2) **Grafana dashboards (second):** per INV-5 the built-in admin-panel metrics view comes first; Prometheus endpoint + shipped dashboards are optional integration. (3) **Own CLI (VERY LOW priority):** the tool must work first with aws cli/mc/rclone (KA-2); a `magrathea-cli` is a later convenience, decision-gated. (4) **Helm/operator (VERY LOW priority):** plain container + compose + documented K8s manifests suffice initially; Helm chart later, operator only if demand proves it. |
| Owner agents | `java-scaffolder`, `java-infra-coder`, `documenter` (ADR), `java-tester` |
| Requirement feature file | `s3-reactive-api-adapter/src/test/features/specs/phase-ka5-distribution.feature` (maintainer-facing packaging Ability; ADR 0024 records the native-image decision) |
| Key requirement IDs | REQ-PKG-* |
| Acceptance gates | Self-contained artifact built by Maven and installable air-gapped; built-in admin-panel metrics view precedes external dashboards; CLI and Helm/operator items stay decision-gated backlog until demand evidence exists. |
| Prerequisites | EP-5 (CI); the single-binary ADR can start earlier. |
| Status | `@partial` for KA-5 overall. Native-image/Alpine packaging slice is `@implemented-and-validated` on 2026-07-10: `bootstrap-application` has Maven profiles `native` and `native-musl`, and `Dockerfile.native` builds a JVM-free Alpine runtime image while preserving Docker-driven docs/UI regeneration. Root JVM Docker packaging is also validated: the Dockerfile uses public ECR mirrored Maven/Temurin bases, copies the full `scripts/` gate set, avoids Maven fail-never mode, creates a writable `/app/data` owned by the non-root `magrathea` user, packages storage-engine YAML catalogs under `/app/config`, activates `storage-engine` for packaged single-node containers, exposes S3/Admin ports, and defines an Admin healthcheck. Validation: local toolchain aligned to Oracle GraalVM 25 native-image because Spring Boot 4 rejects Java 21 native images at startup; `mvn -Pnative -pl bootstrap-application -am -DskipTests native:compile` succeeds; `docker build --network=host -f Dockerfile.native -t magrathea-objectstorage:native .` succeeds; `docker build --network=host -f Dockerfile -t magrathea-objectstorage:jvm .` succeeds; both container paths have no Spring Security generated-password banner in build/runtime logs; root JVM runtime smoke validates `/admin/health`, `/admin/live`, `/admin/ready` ready status, selected storage-engine backend log, S3 ListBuckets XML, and bucket/object PUT/GET. Remaining KA-5 backlog: broader air-gapped install bundle, dashboards, own CLI, and Helm/operator decision-gated items. |

### KA-6 — Public Proof

| Field | Plan |
|---|---|
| Focus | Reproducible benchmark harness (MinIO warp and/or COSBench) Maven-invoked with pinned hardware/software manifests, vs MinIO/Garage/SeaweedFS; publish the s3-tests pass-rate matrix from KA-2; conformance/compatibility docs page. **Absolute rule: published numbers follow the evidence discipline — methodology published, no cherry-picking.** |
| Owner agents | `java-tester`, `documenter` |
| Requirement feature file | `phase-ka6-public-proof.feature` (where executable; benchmark methodology documented otherwise) |
| Key requirement IDs | REQ-PROOF-* (non-functional) |
| Acceptance gates | Benchmarks reproducible from pinned manifests via Maven; pass-rate matrix published with honestly tagged known-failures; no number is published without its methodology. |
| Prerequisites | EP-6, KA-2. |
| Status | `@absent` |

### KA Priority and Sequence

| Order | Phase | Start condition | Status |
|---|---|---|---|
| 1 | KA-1 Positioning & licensing | Immediate | `@partial` (LICENSE + ADR done 2026-07-03; positioning/roadmap published 2026-07-03) |
| 2 | KA-2 Ecosystem conformance | After minimal EP-1 (SigV4) | `@absent` |
| 3 | KA-3 Data-lake readiness & S3 Tables | After EP-1 + EP-3 | `@absent` |
| 4 | KA-4 Eventing, tiering, multi-site, federation | After EP-1/EP-2/EP-4; multi-site after EP-10 | `@absent` |
| 5 | KA-5 Distribution | After EP-5 (single-binary ADR may start earlier) | `@partial` (native/Alpine and root JVM Docker slices validated; broader air-gap/dashboard/CLI/Helm backlog remains) |
| 6 | KA-6 Public proof | Last; after EP-6 + KA-2 | `@absent` |

### Definition of Killer App (checklist — all unchecked)

- [x] KA-1: MIT `LICENSE` and positioning published. (LICENSE + licensing ADR done 2026-07-03; positioning.md and roadmap.md published 2026-07-03.)
- [ ] KA-2: s3-tests pass-rate published with honest known-failures.
- [ ] KA-3: Iceberg/Spark validated (conditional PUT + presigned URLs working).
- [ ] KA-3: S3 Tables adapter + table-store BC delivering namespaces/tables.
- [ ] KA-4: notification delivery (built-in backend) + lifecycle execution + multi-site validated.
- [ ] KA-4: OIDC + LDAP + Kerberos federation validated incl. at least one legacy-path e2e.
- [ ] KA-5: single-binary distribution shipped and air-gap installable.
- [ ] KA-6: public reproducible benchmarks published.

### Bounded Context Evolution (planned)

Planner assessment (2026-07-02): three new bounded contexts plus one integration point are warranted. All entries are PLANNED / `@absent`.

1. **identity-access BC** (KA-4 extension after EP-1): the built-in EP-1 local backend covers credentials, access-key revocation, policy reload, audit, and key management for self-contained deployments. Future STS, OIDC/LDAP/Kerberos federation, and external IdP/KDC adapters get their own domain/application/infrastructure modules; the S3 adapter consumes them via ports and they must not live inside object-store.
2. **table-store BC** (KA-3): table buckets, namespaces, tables, Iceberg metadata, maintenance policies; exposed by the separate `s3tables-api-adapter`; persists through the storage-engine plane; the S3 Metadata bucket routes bridge into it.
3. **event-delivery BC** (KA-4): durable embedded event log + dispatchers (webhook built-in; Kafka/NATS optional plugins per INV-5); consumed by object-store domain events.
4. **PKI integration point** (Magrathea suite): a certificate-provisioning port (node mTLS for EP-10, TLS server certificates, Kerberos/PKINIT credentials) with a simplified local-CA fallback per INV-5; the real backend will be the separate Magrathea PKI project. No PKI implementation in this repo beyond the port + fallback.

Planned module map additions (naming may consolidate at scaffolding time): `identity-access-domain`/`identity-access-application`/`identity-access-infrastructure`; `table-store-domain`/`table-store-application`/`table-store-infrastructure`; `s3tables-api-adapter`; event-delivery modules; the PKI certificate-provisioning port lives in the identity-access application module with the local-CA fallback in its infrastructure module. These entries are mirrored in the Maven module map below.

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

Backend selection, mutually exclusive beans, fail-fast on missing config, and S3 write/read through the storage engine are delivered for object bytes, manifests, object references, and EP-2 storage-engine metadata durability. Remaining runtime gaps are tracked in EP-3 and later roadmap phases.

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
- [x] Storage-engine backend can be selected at runtime without duplicate repositories. **Qualifier:** full-process restart validation now covers bucket registry, multipart state, legal hold, object lock, retention, object encryption, object restore state, object tags, object ACLs, bucket configuration families, and the combined EP-2 bucket/object-tag/object-ACL/multipart scenario.
- [x] S3 write/read path works end to end with the selected storage-engine backend for object bytes, manifests, object references, bucket registry, multipart upload state, per-object configuration metadata, object tags, and object ACL metadata. EP-2 is closed for the declared storage-engine durability scope; remaining metadata work moves to later roadmap phases rather than EP-2 closure.
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
| Unauthenticated exposure in default mode | The default profile remains explicitly unsecured/permit-all for trusted environments; secured deployments must enable `s3.security.enabled=true` and provide durable credential/policy/audit/key files | Deployment guidance must state that default unsecured mode is not for untrusted networks; CI keeps secured-mode WebTestClient/AWS CLI gates green | `documenter`, `java-infra-coder` |
| In-memory metadata loss on restart in the default profile | The default single-node profile remains intentionally non-durable; storage-engine mode now persists EP-2 metadata families | Keep the in-memory exemption explicit in requirements and docs; require `storage-engine` profile for restart durability | `java-infra-coder`, `java-tester` |
| Internal gRPC surfaces drifting into a parallel public object API | Cluster transport (EP-10) or protocol gateway (EP-11) becomes an alternate object API bypassing the S3 facade | **INV-4** governance; internal-only network exposure; adapter/transport reviews reject public object semantics | `documenter`, `java-infra-coder` |
| Split-brain / consistency overclaims in cluster mode | Data loss or false durability/availability claims under partitions | EP-8 ADR consistency model; quorum tests; fault-injection scenarios before any claim | `java-domain-coder`, `java-tester` |
| C module memory safety and Samba version coupling | `vfs_magrathea` crashes `smbd` or breaks across Samba releases | C workflow ownership; sanitizers (ASan/UBSan) in the C CI; pinned Samba versions in containerized builds | C workflow agents, `documenter` |
| Reactive backpressure loss across gRPC hops | Unbounded buffering between nodes or gateway violates INV-1 | Reactive gRPC bindings requirement; static architecture tests per INV-1 | `java-infra-coder`, `java-tester` |
| Killer-app claims outpacing evidence | Marketing/docs claim market leadership without published proof, destroying credibility | KA-6 evidence rule: methodology published, no cherry-picking; all KA statuses remain evidence-based per the Baseline Evidence discipline | `documenter`, `java-planner` |
| External-infrastructure dependency creep | Capabilities become undemonstrable without Kafka/Prometheus/LDAP/external KMS etc., breaking air-gapped installs | INV-5: simplified built-in backends first; every external adapter is an optional plugin; Maven-executable gates | `java-infra-coder`, `documenter` |
| Government/military legacy-path complexity (Kerberos, air-gap, FIPS-style constraints) | Federation/legacy paths stall delivery or compromise the core architecture | identity-access BC isolation; INV-5 offline installs; PKI-suite integration port with local-CA fallback; evaluate FIPS-compliant crypto providers in the EP-1/KA-4 ADRs | `java-domain-coder`, `java-infra-coder` |

---

## Existing Project Structure

### Maven Modules

```text
magrathea-objectstorage/
├── pom.xml
├── s3-reactive-api-adapter/                    # Pluggable S3 HTTP adapter (RouterFunction, XML, Cucumber tests)
├── webdav-api-adapter/                         # PLANNED — optional WebDAV protocol adapter (EP-9)
├── storage-engine-cluster-application/         # PLANNED — cluster ports: membership, replica transport, sync (EP-10)
├── storage-engine-cluster-grpc-infrastructure/ # PLANNED — internal gRPC transport adapters, protobuf contracts (EP-10)
├── smb-vfs-module/                             # PLANNED — Samba VFS C module (non-Maven, container build; EP-11)
├── object-gateway-grpc-adapter/                # PLANNED — optional internal gRPC gateway (EP-11, decision-gated)
├── s3tables-api-adapter/                       # PLANNED — separate S3 Tables API surface: table buckets, namespaces, tables (KA-3)
├── identity-access-domain/                     # PLANNED — identity-access BC extension: STS/federation/external IdP (KA-4)
├── identity-access-application/                # PLANNED — identity-access extension services/ports incl. PKI certificate-provisioning port (KA-4)
├── identity-access-infrastructure/             # PLANNED — external IdP/KDC adapters and local-CA fallback per INV-5 (KA-4)
├── table-store-domain/                         # PLANNED — table-store BC: namespaces, tables, Iceberg metadata (KA-3)
├── table-store-application/                    # PLANNED — table-store services and ports (KA-3)
├── table-store-infrastructure/                 # PLANNED — table-store persistence via the storage-engine plane (KA-3)
├── event-delivery-*/                           # PLANNED — event-delivery BC: embedded event log + dispatchers (KA-4; naming consolidates at scaffolding)
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
