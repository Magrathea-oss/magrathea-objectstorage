# Magrathea ObjectStorage Test Report

Updated: 2026-07-14

> ⚠️ **Report status: EVIDENCE-ONLY / NOT A COMPLETION CLAIM**
>
> This report reflects point-in-time validation evidence and **must not be read as implementation completeness**.
> - The `111/111` route count (referenced elsewhere) is a mapped-surface inventory only, not semantic completion.
> - AWS CLI standalone results (46 passed / 82 failed) are from a run where the server was not started; most failures are connection errors (exit code 254), not logic failures.
> - **Current ordinary root Maven gate after `REQ-PIPELINE-017` reconciliation (2026-07-14, dirty local working tree):** `mvn -B --no-transfer-progress clean test` at the repository root passed with BUILD SUCCESS across **18/18 reactor projects**. Its **128 Surefire XML reports contain 1,927 discovered tests/examples, 1,177 executed, 750 skipped or tag-filtered, 0 failures, and 0 errors**. Opt-in EP-10 real-process acceptance, focused EP-8 supply-chain, and Docker-required `REQ-OPS-023/025` modes were not activated; their separately reported evidence is not replaced by these totals. This dirty-working-tree run is supporting integration evidence only: it is neither acceptance-eligible supply-chain evidence nor a refresh of the historical clean-revision EP-8 SBOM, license, image, hardening, or OWASP packet.
> - **REQ-OPS-023 immutable local publication rehearsal (2026-07-12, local Docker 29.6.1):** `PhaseEp5LocalReleasePublicationCucumberIT` passed against an ephemeral OCI registry. Version `0.1.0`, minor-line `0.1`, and full commit-SHA tags all resolved to the same non-empty digest; the second publication guard detected all existing immutable tags and refused overwrite semantics. The tag-driven release workflow applies the same guard before GHCR publication and records the published digest.
> - **REQ-OPS-025 JVM replacement gate (2026-07-12, local Docker 29.6.1):** `PhaseEp5JvmContainerReplacementRequirementsCucumberIT` passed with 1 executed scenario, 15 tag-filtered scenarios, 0 failures, and 0 errors. The Docker-driven script built the canonical JVM image with version `0.1.0`, source revision `8cfc8eedfa885019a432d2aef0b192e40b03ecbd`, and source URL `https://github.com/Magrathea-oss/magrathea-objectstorage`; proved runtime UID was non-root; mounted a fresh named volume at `/app/data`; wrote bucket `ep5-release-volume-bucket` and exact 30-byte object `release/persistent.txt`; stopped the first container by SIGTERM with exit code 143 and no SIGKILL fallback; removed it; started a different container from the same image ID and volume; required `/admin/live` and ready `/admin/ready`; and byte-compared the recovered object. The test is opt-in for ordinary Maven through `-Pdocker-cucumber-tests` and mandatory before release publication.
> - **Root JVM Docker image gate (2026-07-10):** `Dockerfile` now uses public ECR mirrored Maven/Temurin base images, copies the full `scripts/` gate set, runs the Gherkin appendix freshness gate before packaging, regenerates docs/UI assets, and packages without Maven fail-never mode. Packaged single-node containers activate the `storage-engine` profile, declare `magrathea.object-store.backend=storage-engine`, copy YAML catalogs under `/app/config`, and persist under `/app/data/storage-engine`. `docker build --network=host -f Dockerfile -t magrathea-objectstorage:jvm .` passed. Container smoke validation with host networking passed for `/admin/health`, `/admin/live`, `/admin/ready` returning ready catalog status, S3 ListBuckets XML, bucket `PUT`, object `PUT`, and object `GET`; the runtime log confirmed `Selected object-store backend: storage-engine`; build/runtime logs contained 0 Spring Boot generated-password banners.
> - **Native image packaging slice (2026-07-10):** the local native toolchain was aligned to Oracle GraalVM 25 because Spring Boot 4 rejects Java 21 native images at startup. `mvn -Pnative -pl bootstrap-application -am -DskipTests native:compile` passed and produced `bootstrap-application/target/magrathea-objectstorage`; a native smoke run returned healthy JSON from `/admin/health` and did not emit Spring Boot's generated-password banner. `Dockerfile.native` and the maintainer-facing `specs/phase-ka5-distribution.feature` were added for a GraalVM 25 native-image builder and JVM-free Alpine runtime. `docker build --network=host -f Dockerfile.native -t magrathea-objectstorage:native .` passed, including Gherkin appendix freshness, docs/UI regeneration, musl/static native compilation, and final Alpine image creation. Container smoke validation passed with host networking in the local Docker sandbox: `/admin/health` returned healthy JSON, S3 ListBuckets XML/JSON plus `PUT` bucket / `PUT` object / `GET` object returned the expected object payload, no generated-password banner appeared, no native reflection or shared-arena runtime errors appeared in the container log, `java`/`javac` were absent in the runtime image, binary size was 139.3 MiB, and image size was 63,378,011 bytes.
> - **Post-native follow-up gate (2026-07-10):** `mvn -pl bootstrap-application -am test -Dsurefire.failIfNoSpecifiedTests=false` passed with BUILD SUCCESS after fixing RestoreObject tier normalization (`Standard`/`Bulk` → enum values) and native ListBuckets reflection hints. The log was checked for Spring Boot's generated-password banner and none was found. Single-node WebTestClient restore scenarios that previously failed with 404 now pass.
> - **EP-3 static architecture and multipart assembly/copy/error/streaming slice (2026-07-10):** `mvn -pl s3-reactive-api-adapter -Dtest=Phase3ReactivePipelineStaticArchitectureSpecsCucumberTest test` passed with BUILD SUCCESS after adding `REQ-PIPELINE-011/012`. Cucumber validates `REQ-PIPELINE-007..012` as `@implemented-and-validated`: PutObject/static dedup constraints, non-range GetObject streaming, ranged GetObject per-buffer slicing, UploadPart/UploadPartCopy no-join streaming to part storage, and part-store DataBuffer streaming reads/writes. Multipart assembly `REQ-S3-002-D/F` is also `@implemented-and-validated`: `S3MultipartPartStore` persists uploaded and UploadPartCopy copied bytes and `CompleteMultipartUpload` assembles a readable ordered object. Multipart part-body restart completion `REQ-S3-002-E` is full-process validated by `Phase5MultipartFullRestartCucumberTest`. Multipart XML error semantics `REQ-S3-002-G..K` validate malformed copy-source, unknown upload ID, malformed complete XML, invalid part references, and abort/complete conflicts. Phase 5 WebTestClient and AWS CLI regressions passed. Generated-password banner count: 0 for all logs.
> - **EP-3 runtime backpressure boundary slice (2026-07-11):** `REQ-PIPELINE-013` adds runtime Cucumber evidence for a shared four-DataBuffer demand window on existing PutObject, GetObject/Range, UploadPart/UploadPartCopy, and multipart part-file read/write boundaries. The demand-controlled probe streams 12 ordered 64 KiB buffers, verifies each observed upstream request signal remains at or below four buffers, verifies byte order/range bytes, and exercises real filesystem part persistence/readback. Focused static/runtime, storage-engine reactive application, Phase 5 WebTestClient, and Phase 5 AWS CLI reports pass. This is finite-demand adapter-boundary evidence only; it does not independently claim complete staged StorageStage ordering, publication, lifecycle/cancellation, large-workload, or end-to-end validation.
> - **EP-3 REQ-PIPELINE-002 large pipeline-unit and WebTestClient slice (2026-07-11):** `Phase3PipelineUnitSpecsCucumberTest` passed with 21 discovered examples, 9 executed, 12 tag-filtered/skipped. Both modes generate a deterministic 256 MiB body on demand, run it through the production staged pipeline and real filesystem, measure live Netty source-payload buffers and upstream request signals against the four-buffer ceiling, verify manifest order/length and exact streamed SHA-256 readback, and guard production object-content stages against whole-object assembly. WebTestClient selects the dedicated `PIPELINE` test catalog policy with 1 MiB dedup windows through `x-amz-storage-class`. The adapter requests three upstream buffers per batch so transport plus active processing remains within the declared four-buffer ceiling. REQ-PIPELINE-002 is `@implemented-and-validated` for both declared modes.
> - **EP-3 REQ-PIPELINE-005 client cancellation slice (2026-07-11):** `Phase3ReactivePipelineCucumberTest` now passes with 21 discovered examples, 5 executed, 16 tag-filtered/skipped. The WebTestClient runner opens a temporary loopback Reactor Netty HTTP server over the production S3 router, starts a throttled 256 MiB `PIPELINE` PutObject, waits for two unpublished 1 MiB chunks, then disposes the live HTTP client subscription. The scenario observes upstream cancellation and stable post-cancellation demand, releases emitted and prefetched DataBuffers, receives one `STAGE_CANCELLED` plus `CLEANUP_COMPLETED`, removes chunks/temp files, commits no manifest/object/reference, and receives 404 on later GetObject. REQ-PIPELINE-005 is `@implemented-and-validated` in both declared modes.
> - **EP-3 REQ-PIPELINE-003/016 single-pass read and verified-write completion (2026-07-11):** deterministic 256 MiB pipeline-unit and WebTestClient reads open each persisted artifact once and emit bounded 64 KiB blocks after manifest/reference metadata validation, with no complete-payload preflight or second filesystem read. Upload persistence re-reads each temporary file incrementally and compares it with the incoming digest before atomic rename; a fault-injected mismatch leaves no committed artifact, manifest, or object reference. GetObject returns committed checksum/ETag metadata for client validation; periodic at-rest detection/repair is EP-4 scope.
> - **EP-6 performance and capacity closure (2026-07-12):** REQ-PERF-001..008 enforce and validate the `0.1.x` single-node envelope: 256 MiB single/assembled objects, 64 MiB multipart parts, streaming `EntityTooLarge`, finite request timeout, fail-fast concurrency, token-bucket `SlowDown`, and a real 64-connection process cap. REQ-PERF-009..013 provide deterministic eight-worker load/soak evidence under `-Xmx256m`, bounded/redacted metrics, JSON manifests, artifact checksums, and non-benchmark summaries. Actual CI run: 45 seconds, 184 operations, p99 204 ms, peak heap 179,890,176 B, post-GC 32,521,216 B. Actual soak: 900 seconds, 3,600 operations, p99 187 ms, peak heap 138,494,976 B, post-GC 34,362,368 B. Both had zero corruption/unexpected responses and zero idle resource leaks. These observations are regression gates, not production sizing or competitive claims.
> - **EP-4 space management and data hygiene closure (2026-07-11):** REQ-SCRUB-001/002 validates opt-in transform-aware scrubbing; REQ-GC-001..004 validates restart-safe typed whole-object, dedup, multipart, and EC reclamation; REQ-QUOTA-001/002 validates durable logical-byte quotas, Admin reporting, and atomic concurrent reservations; REQ-CAPACITY-001 validates deterministic/native ENOSPC mapping, cleanup, existing-object preservation, and redacted capacity signals. Focused EP-4 Cucumber passed: space-management **10/10**, capacity WebTestClient **3 applicable scenarios**, and real AWS CLI **3 applicable scenarios**, with 0 failures/errors. Distributed coordination remains EP-10 scope.
> - **REQ-PIPELINE-014/015 storage-artifact completion slice (2026-07-11):** pipeline-unit and WebTestClient runners validate deterministic 8 MiB uploads through the production staged pipeline and real filesystem. Plain uploads record `whole-object-pass-through`, persist exactly one `FileUnit` under `whole-objects`, create no segmented artifacts or dedup index, and read back exactly. EC_4_2 uploads produce two bounded 4 MiB stripes, eight data shards and four GF(256) parity shards under the segmented namespace; typed manifests distinguish their kinds, every sidecar is validated, and ordered data-shard readback is exact. Schema 0/1 chunk-only manifests remain readable as `LEGACY_CHUNK`.
> - **Focused `REQ-PIPELINE-017` bounded local EC reconstruction evidence (2026-07-14 reconciliation):** `ReqPipeline017EcReconstructionCucumberTest` passed exactly **5 scenarios / 46 steps**. New EC writes use schema 3 with explicit stripe/shard/k/m/parity/logical-length/stored-length/lowercase-SHA-256/transport-neutral-location facts; schema 0/1/2 compatibility paths remain readable, while ambiguous schema-2 EC reconstruction fails closed. The EC 4+2 matrix exercised **all 15 four-of-six survivor combinations** and regenerated both omitted shards byte-for-byte across data/data, data/parity, and parity/parity losses. Short final-stripe output preserves exact logical size; decoder-owned state is one stripe plus at most six shard buffers and fixed matrices, with immutable request/result snapshots reported separately; GF(256) work runs on a dedicated one-worker/16-queue scheduler. All five scenarios are now `@implemented-and-validated`. `Phase3PipelineUnitSpecsCucumberTest` remains green for the `REQ-PIPELINE-014/015` regressions. This is local output-only evidence: no shard replacement/publication, scanner or daemon, distributed placement/transfer, Ratis repair job, rebalance, cleanup, ADR 0030 fault action, or chaos behavior is claimed. `REQ-CLUSTER-015` remains not implemented and EP-10 remains partial. The focused non-publishing CI job is wired to retain JSON/Surefire evidence, but **CI has not run**.
> - **Post-reconciliation documentation and architecture regression gates (2026-07-14):** `PhaseEp8ArchitectureContractCucumberTest` passed **13 scenarios / 103 steps**; `ReqCluster014ArchitectureContractCucumberTest` passed **1 scenario / 17 steps** and observed **476 current production Java sources** in the dirty worktree. The separate 2026-07-14 checkpoint below retains its historical 473-source count. Appendix freshness passes at **534 scenarios / 34 feature files**; actionlint/YAML parsing, reproducible frontend/ARC42 conversion, module layering, source hygiene, and diff checks pass. The clean ordinary root Maven test gate also passes **18/18 reactor projects** with the 1,927/1,177/750 aggregate reported above. These are local non-publishing results; **CI has not run**.
> - **EP-7 bounded-scope closure and resumed UX evidence (2026-07-12):** `REQ-ADMIN-001..037` are tagged `@implemented-and-validated`; EP-7 overall remains `@partial` because credential/tenant administration and real recovery/GC/scrub/audit/metrics/traces providers are absent. The resumed UX groups navigation by operator task, prioritizes readiness/backend/attention evidence and degraded-device follow-up on the dashboard, progressively discloses supporting backend diagnostics without hiding critical conditions, gives pending/success/actionable-failure feedback for non-persistent policy validation, supports persistent `system`/`light`/`dark` appearance, and explains unavailable capabilities, impact, and valid next steps without presenting missing operations as enabled. Vitest passed **86/86 tests across 14 files**. Playwright Chromium passed **57/57 tests**: **19/19 at each** viewport width of **360**, **768**, and **1440** pixels; this includes **3/3 axe scans with zero violations** and **12/12 Linux Chromium visual-regression baselines**. ESLint passed with zero warnings/errors, typecheck passed for six workspaces, and template validation, deterministic extension removal, and the five-package/application product build passed. Canonical Docker `frontend-packaging-validation` was rerun and passed in **83.736s**; both product builds and their deterministic path-sorted SHA-256 inventories matched, with Product Shell digest `sha256:ee6a77dad38145fafe1d6ab691ea318eb922537a9951feeed58d35ebfbc91536`. `PhaseEp7AdminApiRequirementsCucumberTest` remains at **18 executed scenarios / 132 steps** for REQ-ADMIN-023..031; six scenarios prove truthful HTTP 503 `report-provider-not-configured` / `availability: not-configured`, not provider implementation.
> - **EP-8 architecture status and historical supply-chain packet (2026-07-13):** ADR 0027 is accepted for architecture only. `PhaseEp8ArchitectureContractCucumberTest` passed **13/13 expanded scenarios / 103 steps**; the separate `PhaseEp8SupplyChainEvidenceCucumberIT` historical clean run passed **9/9 / 103 steps**. That historical packet is bound to revision `209b3170b64d3311ba9b773fdb7bd5581519e682` and exact unpublished local image ID `sha256:e5b5862948853a613f8b92adc8135fa81b84bdd9f36c9ae8d69b9a6bd6dd7966`. Its CycloneDX 1.6 application JSON has **102 components / 103 dependencies**, SHA-256 `4025d7206af01683da5cffa84862f20f68c7568dd9338ee9920d55947df545d1`; XML SHA-256 is `b934de56a243cf6b5464566f45635e1f6ac0fc244d2ceb7c1f7418308012952f`. Its license inventory reports **85 recognized / 13 unknown / 4 ambiguous**, SHA-256 `0df3a3eac37e554dce6d67f227ffead9b28675f47fca9dab741d6850ac47b214`, with **no compliance claim**. Syft 1.31.0 image ID `sha256:c15fa8af4c25edd72c0daf026d095fe51adbcfc7ad5d79a66e93d88f249e5abb` produced a **7,105-component** image SBOM, SHA-256 `8d13ae409ef7230d52b4d850d0700db69132297910e55cffdbe27c08e28a90ba`. Hardened replacement passed as UID/GID 10001 with read-only root, explicit data/tmp mounts, `no-new-privileges`, capability drop `ALL`, no host PID/IPC/network/UTS namespaces or engine socket, truthfully unavailable userns remapping, ready `storage-engine`, and exact 129-byte object persistence with SHA-256 `178ba39b2e4e92264f35dafbd416ba3c8beb0dc87b395415f068c181d837def0` and ETag `243fcbdee5a51e0be8f784e08462e98d`. These hashes remain historical evidence only: the production reactor now composes `cluster-protocol`, `storage-engine-cluster-application`, `cluster-control-ratis-infrastructure`, and `cluster-data-grpc-infrastructure`, and no new clean-revision application SBOM/license/image packet was generated after that expansion. Therefore current complete-reactor `REQ-SUPPLY-001` is `@implemented-not-e2e-validated`; the other EP-8 requirement statuses and their explicit limitations remain unchanged. CI wiring performs **no publication**, and the dirty ordinary root run above is not acceptance supply-chain evidence.
> - **Historical EP-10 bounded fixed-cluster and current-generation repair evidence (2026-07-13):** the Java 21 shared real-process S3 runner passed `REQ-CLUSTER-001..005`, `019`, and `020` in WebTestClient and AWS CLI modes: **14 scenarios / 188 steps**, 0 failures; its focused repair-only `019/020` run passed **4 scenarios / 80 steps**. The first five requirements retained fixed A/B/C consensus publication, `N=3/W=2`, quorum refusal, failover, and complete-restart proof. `019/020` added missing-local repair before response and single-pass corrupt-GET failure followed by durable repair for a later GET. The repair-control run passed **22 scenarios / 210 steps**, and the data-plane regression passed. That evidence point did not yet execute the complete `024` real-filesystem/gRPC matrix; this sentence records the historical limitation rather than current status. The process-local scheduler only wakes and queries committed work; Ratis owns durable lifecycle, while `ClusterNodeRuntime` owns scheduler process lifecycle. These focused results are not folded into the ordinary root Maven totals.
> - **Focused `REQ-CLUSTER-024` completion evidence (2026-07-14):** `ReqCluster024CucumberTest` passed **7 scenarios / 168 steps**. B ran as an independently crashable and restarted JVM with distinct PIDs and its original non-empty identity, Ratis, and filesystem roots; the A/C voters and source-C real grpc-java server remained in the parent Cucumber JVM. The gate executed actual B-to-C grpc-java reads with UUID-bound mTLS, token-specific `FileLocalArtifactStore` staging/publication, filesystem byte/hash inspection, exact-target no-recopy reconciliation, stale-token fencing, interrupted version-2 snapshot installation with last-valid-snapshot-plus-log recovery, completion committed with its reply withheld, and live A-to-C leadership transfer. In the same validation set, repair-control passed **22 scenarios / 294 steps**, data-plane passed **4 / 40**, and control/TLS regressions passed; module compilation, layering, source hygiene, and diff checks also passed. `REQ-CLUSTER-024` is now `@implemented-and-validated`. At this earlier `024` evidence checkpoint, broad `017` remained `@partial`, `006/007/015/016/018` remained `@not-implemented`, and `014` still awaited its separate architecture-only reconciliation recorded below. This evidence is bounded: it is not general chaos, broad partition tolerance, rolling upgrade, dynamic membership, anti-entropy, rebalance, orphan cleanup, or production readiness.
> - **Focused `REQ-CLUSTER-014` architecture completion evidence (2026-07-14):** `ReqCluster014ArchitectureContractCucumberTest` passed **1 scenario / 17 steps** in the requirement's sole agreed internal validation mode. It parsed **17 reactor modules**, **87 direct non-test Maven dependencies** plus **2 profile dependency declarations**, **473 tracked production Java sources** with JDK AST parsing, and **one versioned proto3 contract**; executed `scripts/check-module-layering.sh`; and exercised **12 missing/empty/malformed** plus **4 supported POSIX unreadable** fail-closed probes with exact repository paths and protected-input hash preservation. Regression gates passed: coordination **7 / 54**, control **2 / 19**, data **4 / 40**, repair **22 / 294**, EP-8 architecture **13 / 103**, source hygiene, and generated appendix freshness. The non-publishing EP-10 CI job is wired to run the gate and retain its evidence, but **CI has not run**. `REQ-CLUSTER-014` is now `@implemented-and-validated` only for this repository-rooted source/build architecture contract; it is not S3 behavior and proves no runtime side effect, broad healing, or production readiness. EP-10 and broad `017` remain partial; `006/007/015/016/018` remain not implemented.
> - **Focused `REQ-CLUSTER-027` periodic current-reference anti-entropy evidence (2026-07-14):** `ReqCluster027CucumberTest` passed exactly **2 scenarios / 36 steps**. The gate used real three-voter Ratis canonical exclusive current-reference pages, real filesystem targets, actual grpc-java repair reads with UUID-bound mTLS, consensus/filesystem inspection, serial page/target processing, scheduler cancellation and restart-from-first, query/probe/ensure/repair failures, current-reference races, and bounded counters. Existing implementation regressions passed: `ReqCluster024CucumberTest` **7 / 168**, repair control **22 / 294**, and data plane **4 / 40**. Reconciliation validation also passes `PhaseEp8ArchitectureContractCucumberTest` **13 / 103**, `ReqCluster014ArchitectureContractCucumberTest` **1 / 17**, and appendix freshness at **534 scenarios / 34 feature files**. Both `REQ-CLUSTER-027` scenarios are `@implemented-and-validated`; broad `017` remains partial because rebalance, automated orphan cleanup, and wider healing/topology coverage remain absent. `006/007/015/016/018` remain not implemented. The non-publishing EP-10 CI job is wired to run/upload this focused JSON and Surefire evidence, but **CI has not run**. The cursor is process-local, pages are not one frozen scan, payload remains outside Ratis, PA-6 plans remain planner output, and no production scale/readiness or ADR 0030 general chaos is claimed.
> - **Previously recorded broad Cucumber/requirements/specs counts, plus current generated inventory:** generated ARC42 appendix now covers **534 scenarios from 34 feature files**; single-node WebTestClient requirements runner **260 discovered / 234 passed / 26 skipped** from the previous broad gate; single-node AWS CLI requirements runner **72 discovered / 26 passed / 46 skipped** from the previous broad gate; specs runner **3 passed**; Phase 1 WebTestClient and AWS CLI runners **28 discovered / 9 passed / 19 skipped each**; Phase 2 WebTestClient spec runner **11 discovered / 7 passed / 4 skipped**; Phase 2 AWS CLI spec runner **11 discovered / 4 passed / 7 skipped**; Phase 5 WebTestClient **33 discovered / 32 passed / 1 skipped**; Phase 5 AWS CLI **33 discovered / 14 passed / 19 skipped**; Phase 5 multipart full-process restart runner **33 discovered / 1 passed / 32 skipped**; current Phase 3 pipeline-unit runner **35 discovered / 14 passed / 21 tag-filtered**, and focused local EC reconstruction **5 / 46**; Phase 3 WebTestClient runner **30 discovered / 8 passed / 22 skipped**; Phase 3 static specs runner **30 discovered / 7 passed / 23 skipped**; Phase 3 runtime backpressure specs runner **30 discovered / 1 passed / 29 skipped**; EP-4 space-management specs **10 passed**; EP-4 capacity WebTestClient and AWS CLI runners **6 discovered / 3 passed / 3 opposite-mode examples skipped each**; EP-5 storage migration specs **6 passed**; EP-5 SLO/alerting bundle validation included in operability; EP-1 security/identity WebTestClient **13 passed**; EP-1 security/identity AWS CLI/e2e **13 passed**; EP-1 backing-service specs **4 passed**; EP-2 metadata durability WebTestClient restart-simulation **21 discovered / 3 passed / 18 skipped**; EP-2 metadata durability full Spring restart **21 discovered / 18 passed / 3 skipped**; EP-5 operability **14 passed / 2 Docker-tagged scenarios filtered**; EP-5 delivery specs **2 passed**; EP-5 local release publication **1 passed / 2 static scenarios filtered**; EP-6 capacity components **3 passed / 6 filtered**, performance-capacity **8 passed / 1 connection scenario filtered**, connection cap **1 passed / 8 filtered**, CI load **4 passed / 5 filtered**, and soak **3 passed / 6 filtered**; opt-in EP-5 live alert delivery **1 passed / 15 tag-filtered**; S3 API semantic coverage report spec **1 passed**.
> - **Gherkin requirements/specs ARC42 appendix:** refreshed after promoting all five `REQ-PIPELINE-017` scenarios; `python3 scripts/generate-gherkin-requirements-appendix.py --check` passes and reports **534 scenarios from 34 feature files**. Exact status is represented from source tags: local pipeline `REQ-PIPELINE-017` is implemented-and-validated, while cluster `REQ-CLUSTER-015` remains not implemented, broad cluster `REQ-CLUSTER-017` and EP-10 remain partial, and `006/007/016/018` remain not implemented. The corrected EP-8 feature records the expanded production-module inventory and current complete-reactor `REQ-SUPPLY-001` as implemented-not-e2e-validated while preserving the dirty-tree fail-closed scenario and other EP-8 statuses. The appendix remains grouped by ADR 0020 stakeholder audience (`Business Need` vs `Ability`) and reports declared source tags rather than inventing observed results.
> - **EP-1 targeted Spring Security Reactive security slice plus specs sanity (2026-07-10):** `mvn -pl s3-reactive-api-adapter -Dtest=PhaseEp1SecurityIdentityRequirementsCucumberTest,PhaseEp1SecurityIdentityAwsCliCucumberTest,SigV4SecuritySpecsCucumberTest,Ep1SecurityServicesSpecsCucumberTest,SpecificationsCucumberTest test` passed with **40 Surefire tests/examples, 0 failures, 0 errors, 0 skipped** and no Spring Boot generated-password log. The Business Need runners execute all 13 EP-1 scenario examples in both WebTestClient and AWS CLI/e2e modes against a real RANDOM_PORT S3 API with `s3.security.enabled=true`: SigV4 auth, exact payload hashes, invalid auth rejection, deny-by-default, explicit deny, PublicAccessBlock/public ACL denial, expected bucket owner mismatch, durable redacted file audit, and an SSE-S3 encrypted-at-rest inspection slice. The path is wired through Spring Security Reactive (`SecurityWebFilterChain`, SigV4 `ServerAuthenticationConverter`, reactive authentication/authorization managers, and S3 XML security handlers). The maintainer-facing `specs/sigv4-verifier.feature` Ability runner still executes 7 component-spec examples for verifier/filter decisions; the existing specs runner still passes. This validates current REQ-SEC-001..009 scenarios plus REQ-SEC-010..013 durable backing-service specs, closing EP-1 for the declared local built-in scope. External identity federation remains future KA-4 scope.
> - **EP-2 metadata durability targeted restart-simulation slice (2026-07-10):** `mvn -pl s3-reactive-api-adapter -Dtest=PhaseEp2MetadataDurabilityCucumberTest test` passed with **21 discovered Cucumber scenarios, 3 executed, 0 failures, 0 errors, 18 skipped/excluded** and no Spring Boot generated-password log. The selected `@ep2-webclient-restart` Business Need scenarios validate storage-engine object tags, durable object ACL sidecars, and a combined bucket/object/multipart metadata restart-simulation path via WebTestClient and repository `reloadFromDisk()`.
> - **EP-2 metadata durability full Spring restart slice (2026-07-10):** `mvn -pl s3-reactive-api-adapter -am -Dtest=PhaseEp2MetadataDurabilityCucumberTest,PhaseEp2MetadataDurabilityFullRestartCucumberTest -Dsurefire.failIfNoSpecifiedTests=false test` passed with **42 discovered Cucumber scenarios, 21 executed, 0 failures, 0 errors, 21 skipped/excluded** and no Spring Boot generated-password log. The selected `@ep2-full-process-restart` Business Need scenarios stop and start two independent Spring application contexts with the same storage-engine filesystem root and prove bucket registry, multipart upload state, legal hold, object lock configuration, retention, object encryption, object restore state, object tags, object ACLs, CORS, notification, bucket object-lock, inventory-table, journal-table, ABAC, metadata, metadata-table, and the combined bucket/object-tag/object-ACL/multipart metadata path survive. The multipart validation also exposed and fixed that `ListMultipartUploads` was listing aborted uploads; the handler now filters to active uploads. The remaining skipped EP-2 `@in-memory-exemption` scenarios are legacy test-profile cases, not a supported single-node backend or Storage Engine durability gaps.
> - **Current adapter-module local gate with AWS CLI visible (2026-07-10, AWS CLI 2.34.32):** `mvn -pl s3-reactive-api-adapter test` passed with **470 Surefire tests/examples, 0 failures, 0 errors, 102 skipped** and no Spring Boot generated-password log. The gate includes actual AWS CLI-backed Cucumber execution, including the new EP-1 AWS CLI/e2e runner (`13 passed / 0 skipped`).
> - Phase 1 upload reliability: REQ-UPLOAD-001 and REQ-UPLOAD-002 use the 2026-07-02 validation-mode decision: bootstrap JUnit integration validation is formally accepted as the sole required runtime validation mode and is documented with rationale in `phase-1-upload-storage-engine.feature`; their `@webclient`/`@awscli` examples are supplementary and excluded from Phase 1 Cucumber runner execution by `not @bootstrap-integration-required`. REQ-UPLOAD-003 is no longer an executable Phase 1 target. REQ-UPLOAD-004 is `@implemented-and-validated` from the previously recorded dual WebTestClient/AWS CLI runtime evidence.
> - Phase 2 filesystem reliability remains **implemented-and-validated for the declared Phase 2 scope**, including the REQ-FS-006 same-key concurrency scenario after a real torn-reference defect was found, fixed, and stress re-validated 6/6 (see the dedicated section below). This does not claim distributed readiness, broader S3 semantic completion, or later-phase production readiness.
> - Phase 3 has implemented-and-validated pipeline-unit, WebTestClient, static-architecture, and runtime-boundary evidence for `REQ-PIPELINE-001..016` in their declared modes. `REQ-PIPELINE-014/015` validate the whole-object namespace and physical EC shards. Focused `REQ-PIPELINE-017` adds schema-3 reconstruction facts and local output-only bounded EC 4+2 decoding at **5 scenarios / 46 steps**, including all **15 four-of-six** combinations; no later repair/distributed/chaos scope is implied.
> - Phase 5 S3 semantic compatibility is **implemented-and-validated for 32 WebTestClient-executed scenarios** and **implemented-not-e2e-validated for REQ-S3-002-C multipart restart/durability** because that scenario uses a direct same-directory filesystem repository probe rather than a full process/Spring restart. Phase 5 AWS CLI validation runs the same feature through an AWS CLI runner for its `@awscli-required` subset (14 passed / 19 skipped in the latest focused gate).
> - **Single-node default backend correction (2026-07-14):** `REQ-PKG-005` and `DefaultBackendContextTest` validate that bare and packaged product runtimes default to Storage Engine. `spring.profiles.default=storage-engine`; blank backend selection reports Storage Engine; production in-memory adapters activate only under explicit `legacy-in-memory-test`. Focused results: KA-5 Cucumber 5/5 passed; default backend context 2/2 passed across the 17-project dependency reactor. The complete root `mvn test` reactor then passed all 18 projects (opt-in EP-10 real-process examples remained assumption-skipped), and the canonical Docker frontend/documentation build-twice gate passed with 527 scenarios. The first context smoke attempt exposed an invalid unknown-length repository overload in the test and was corrected to use the production pending-object write contract before the passing rerun.
> - **EP-5 operability probe, shutdown, backup/restore, DR, and alerting slice (2026-07-10):** `PhaseEp5OperabilityRequirementsCucumberTest` passed with 14 Business Need scenarios, 0 failures/errors, and 2 Docker-required scenarios tag-filtered, validating Admin API `/admin/live` liveness, `/admin/ready` storage-catalog readiness, fail-closed `503 not-ready` behavior when required catalogs are unavailable (`REQ-OPS-001..003`), `REQ-OPS-004` SIGTERM shutdown/recovery evidence, `REQ-OPS-009` SIGTERM draining of an active 524,288-byte streaming PutObject with restart byte-count/checksum verification, `REQ-OPS-010` draining of an active 524,288-byte multipart UploadPart followed by restart completion and final checksum verification, `REQ-OPS-011` draining of two concurrent 262,144-byte PutObjects with restart checksum verification for both, `REQ-OPS-012` draining of an active CompleteMultipartUpload with restart checksum verification of the assembled object, `REQ-OPS-013` cancellation and abort of an active UploadPart followed by restart verification that no object, active upload, or part artifacts remain, `REQ-OPS-014` abort overlapping an active CompleteMultipartUpload during SIGTERM with HTTP 204/NoSuchUpload and restart cleanup verification, `REQ-OPS-015` bounded mixed-load draining of three streaming writes and two throttled reads with response and restart checksum verification, `REQ-OPS-005` offline backup/restore evidence, `REQ-OPS-006` single-node DR objectives: RTO 30 seconds and RPO last completed offline backup, and `REQ-OPS-008` shipped SLO/alerting bundle coverage for Admin probes, S3 smoke, storage capacity, backup age, generated-password regressions, and manifest-schema regressions. `docs/runbooks/graceful-shutdown.md`, `docs/runbooks/backup-restore.md`, `docs/runbooks/disaster-recovery.md`, and `docs/runbooks/slo-alerts.md` record the validated procedures and their scope. The opt-in `PhaseEp5LiveAlertDeliveryRequirementsCucumberTest` also passed `REQ-OPS-021` with 1 executed and 15 tag-filtered scenarios, validating the exact shipped Prometheus rules with `promtool`, evaluating the Admin liveness alert against a failing probe, and delivering `MagratheaAdminLivenessProbeDown` through live Prometheus and Alertmanager containers to an operator webhook receiver. The EP-5 storage migration specs (`REQ-OPS-007`, `REQ-OPS-016..020`, `REQ-OPS-024`) also passed for the schema-2 typed-manifest baseline; focused `REQ-PIPELINE-017` now separately proves new EC writes use schema 3 with explicit reconstruction layout. Multipart upload sessions, bucket registry JSON, object configuration JSON, S3 object manifest references, object ACL sidecars, and bucket capacity ledgers declare schema version `1`. Legacy records without a version remain readable as compatibility version `0`, unsupported future schema version `999` is rejected, and the capacity ledger additionally rejects malformed versions while preserving durable accounting and clearing stale reservations. The focused CI gate including EP-1, EP-2, EP-3, EP-5, KA-5, and Phase 5 runners passed with 206 tests/examples, 0 failures/errors, and 77 skipped. Generated-password banner count: 0.
> - Phase 6 distributed-readiness policy remains **implemented-not-e2e-validated** for its broad modeled domain scope. EP-10 separately executes fixed whole-object `N=3/W=2` publication, bounded current-generation repair, and bounded periodic current-reference discovery on A/B/C. Local `REQ-PIPELINE-017` decoding is validated but is not distributed evidence. Distributed EC placement/transfer (`REQ-CLUSTER-015`), EC self-healing, rebalance, automated orphan cleanup, dynamic membership, wider topology behavior, and general distributed readiness remain absent. PA-6 plans are not execution evidence, and distributed production readiness is not claimed.
> - **Quality/security evidence slice (updated 2026-07-13, evidence only):** `mvn -B -Pcoverage clean verify --no-transfer-progress` produced the reactor-wide JaCoCo baseline at revision `c3fe8bb1` (48,191/60,631 instructions, 1,957/3,484 branches, 8,868/11,367 lines, 2,671/3,316 methods, and 570/635 classes covered). No coverage threshold is enforced. The historical EP-8 clean-revision (`209b3170b64d3311ba9b773fdb7bd5581519e682`) OWASP Dependency-Check 12.2.2 attempt failed closed after **190,000/364,891** NVD records with HTTP 429; `scanStatus=error`, `clean=null`, 8 errors. This is monitoring-only `unknown/error`: no vulnerability findings, clean-scan, zero-vulnerability, or remediation claim is permitted. `REQ-QUAL-001..004` remain `@partial` pending executable semantic validation and a complete assessment.
> - **JaCoCo is the current coverage baseline.** Clover/OpenClover is optional/legacy.
>
> The generated semantic S3 coverage matrix is available at `docs/api-coverage.md`. Its conservative baseline is 111 official operations, 108 with router mappings, 21 with explicit operation-linked `@implemented-and-validated` evidence, and 90 not yet eligible for a 100% completion claim.

## Summary

| Suite | Passed | Failed | Skipped / excluded | Total | Notes |
|---|---:|---:|---:|---:|---|
| AWS CLI S3 compatibility script | 46 | 82 | 0 | 128 | Stale standalone `test-aws-cli.sh` result; endpoint: `http://localhost:8080` |
| Single-node backend WebTestClient requirements | 234 | 0 | 26 | 260 | `SingleNodeBackendWebTestClientRequirementsCucumberTest` PASS in the 2026-07-09 full Maven gate; migrated ADR 0021 requirements replacing the legacy `object-store/` runner. |
| Single-node backend AWS CLI requirements | 26 | 0 | 46 | 72 | `SingleNodeBackendAwsCliRequirementsCucumberTest` PASS in the 2026-07-09 full Maven gate; same migrated single-node backend requirements filtered to `@awscli-required`. |
| Phase 2 WebTestClient specs | 7 | 0 | 4 | 11 | Isolated Phase 2 runner; REQ-FS-001 through REQ-FS-006 WebTestClient-required scope passed from `specs/phase-2-filesystem-reliability.feature`; skipped/excluded examples are tagged `@awscli`. |
| Phase 2 storage-engine AWS CLI specs | 4 | 0 | 7 | 11 | `Phase2StorageEngineAwsCliRequirementsCucumberTest` PASS in the latest full Maven gate against `specs/phase-2-filesystem-reliability.feature`; 4 `@awscli-required` examples executed, 7 skipped/excluded by tag filtering. |
| EP-2 metadata durability WebTestClient restart-simulation | 3 | 0 | 18 | 21 | `PhaseEp2MetadataDurabilityCucumberTest` PASS: object tags, object ACL, and combined bucket/object/multipart metadata scenarios executed against storage-engine profile and `reloadFromDisk()` restart simulation. |
| EP-2 metadata durability full Spring restart | 18 | 0 | 3 | 21 | `PhaseEp2MetadataDurabilityFullRestartCucumberTest` PASS: bucket registry, multipart upload state, legal hold, object lock configuration, retention, object encryption, object restore state, object tags, object ACL, eight bucket-config family scenarios, and combined bucket/object-tag/object-ACL/multipart metadata scenarios executed by stopping one Spring context and starting another against the same storage-engine filesystem root. |
| Phase 3 storage-engine reactive application gate | 159 | 0 | 0 | 159 | `mvn -B -pl storage-engine-reactive-application -am test` PASS; unit/application validation for REQ-PIPELINE-001 through REQ-PIPELINE-006. |
| Phase 3 pipeline-unit Cucumber specs | 14 | 0 | 21 | 35 | `Phase3PipelineUnitSpecsCucumberTest` PASS: executes selected REQ-PIPELINE-001..006 and REQ-PIPELINE-014..016 examples; the five focused `017` scenarios are tag-filtered into their own runner. Includes 256 MiB bounded single-pass reads, cleanup, instrumentation, typed namespaces, physical EC shards, and fault-injected verified-write rejection. |
| Focused `REQ-PIPELINE-017` local EC reconstruction | 5 | 0 | 0 | 5 scenarios / 46 steps | `ReqPipeline017EcReconstructionCucumberTest` passes schema-3 facts/compatibility, all 15 four-of-six EC 4+2 combinations, exact short-stripe output, bounded workspace/scheduler execution, fail-closed invalid requests, and no publication. CI is wired but has not run. |
| Phase 3 WebTestClient Cucumber specs | 8 | 0 | 22 | 30 | `Phase3ReactivePipelineCucumberTest` PASS: executes REQ-PIPELINE-001..005 selected examples plus typed whole-object and physical EC validation; REQ-PIPELINE-003 reads payload artifacts once after metadata-only validation. |
| Phase 3 static architecture Cucumber specs | 6 | 0 | 15 | 21 | `Phase3ReactivePipelineStaticArchitectureSpecsCucumberTest` PASS: enforces REQ-PIPELINE-007..012 for PutObject, fixed-window dedup, non-range/range GetObject, UploadPart/UploadPartCopy, and multipart part-store streaming constraints. |
| Phase 3 runtime backpressure boundary Cucumber spec | 1 | 0 | 20 | 21 | `Phase3RuntimeBackpressureSpecsCucumberTest` PASS: REQ-PIPELINE-013 applies and probes a four-buffer demand window across existing S3 object/multipart boundaries; not staged-pipeline e2e evidence. |
| Same-key concurrency unit test (`S3ObjectManifestReferenceStoreConcurrencyTest`) | 3 | 0 | 0 | 3 | Torn-reference impossibility under 16-thread same-key races with a concurrent reader; no lost updates in 16×100 read-compose-write increments; unrelated keys uncorrupted while a hot key is contended. |
| Phase 1 Cucumber runners (WebTestClient + AWS CLI) | 9 + 9 | 0 | 19 + 19 excluded by tag filter | 28 + 28 discovered | Each runner discovered 28 scenarios and executed 9 with 0 failures in the 2026-07-02 Podman gate; REQ-UPLOAD-004 validated independently in both modes after REQ-UPLOAD-003 removal, run twice with identical counts; feature-file tag refreshed to `@implemented-and-validated` and the feature-level `@partial` tag removed. |
| Phase 2 AWS CLI same-key stress re-validation | 6 runs / 6 passed | 0 | 0 | 6 fresh-JVM runs | Phase 2 AWS CLI runner re-run 6 times in fresh JVMs after the concurrency fix; REQ-FS-006 same-key passed 6/6 (previously the defect reproduced 4/4 in one session). |
| Phase 5 WebTestClient semantic compatibility requirements | 32 | 0 | 1 | 33 | `Phase5S3SemanticCompatibilityRequirementsCucumberTest` PASS; requirement source `s3-reactive-api-adapter/src/test/features/requirements/phase-5-s3-semantic-compatibility.feature`. |
| Phase 6 storage-engine domain distributed-readiness gate | 164 | 0 | 0 | 164 | `mvn -B -pl storage-engine-domain test --no-transfer-progress` PASS; modeled domain/unit validation for REQ-DIST-001 through REQ-DIST-006. |
| Domain quality bounded first pass | 464 | 0 | 0 | 464 | `mvn -B -pl storage-engine-domain,object-store-domain test --no-transfer-progress` PASS; storage-engine-domain: 172 tests; object-store-domain: 292 tests. Covers collection immutability exposure and additional `StoredObject` invariant tests only; not a complete domain redesign. |
| Runtime correctness bounded first pass | n/a | 0 | n/a | n/a | `mvn -B -pl object-store-reactive-repository-storage-engine-infrastructure,storage-engine-reactive-infrastructure,s3-reactive-api-adapter,bootstrap-application -am test --no-transfer-progress` PASS / BUILD SUCCESS; `mvn -B test --no-transfer-progress` PASS / BUILD SUCCESS. Covers storage-engine repository read-after-write regression, `FileSystemManifestRepository` checksum round-trips, and `BlockingFileSystemOperation` bounded-elastic filesystem scheduling. Broader staged pipeline/runtime backpressure evidence remains open. |
| Phase 1 upload reliability Cucumber runtime coverage | 9 + 9 executed | 0 | 19 + 19 excluded by tag filter | 28 + 28 discovered | 2026-07-02 Podman gate: WebTestClient and AWS CLI Phase 1 runners each executed 9 scenarios with 0 failures; REQ-UPLOAD-004 validated independently in both modes after REQ-UPLOAD-003 removal, confirmed by two identical runs (before and after the feature-file tag refresh). An earlier transient pass that depended on incorrectly reintroduced non-dedup multi-chunk persistence remains rejected as evidence. |
| Phase 1 upload reliability bootstrap validation (`bootstrap-application`) | 19 bootstrap tests total | 0 | 0 | 19 | Latest full Maven gate includes `bootstrap-application`: 19 tests, 0 failures, 0 errors, 0 skipped. Bootstrap evidence covers REQ-UPLOAD-001 restart safety, REQ-UPLOAD-002 manifest reload, REQ-UPLOAD-004 failed-upload atomicity, REQ-UPLOAD-005 read-after-write, and REQ-UPLOAD-006 corruption detection. The deleted REQ-UPLOAD-003 bootstrap test is not evidence. Under the 2026-07-02 validation-mode decision, bootstrap evidence is the agreed sole required runtime validation mode for REQ-UPLOAD-001/002; REQ-UPLOAD-004 also has independent Cucumber runtime validation in both modes. |
| Section E YAML catalog / backend-wiring / admin-API bounded first pass | `YamlStoragePolicyCatalogTest` (14), `YamlStorageDeviceCatalogTest` (10), `YamlDiskSetCatalogTest` (12), `MinioStandardIntegrationTest` (2), `StorageEngineMissingExternalConfigTest` (1), `StorageEngineBackendContextTest` (3), `AdminRouterTest` (9) | `mvn -B test` PASS | All Section E acceptance gates met: malformed/duplicate/unresolved catalog rejection, MINIO_STANDARD deterministic plans, fail-fast on missing backend config, mutually exclusive backend selection, admin API uses same catalog beans. |
| Maven adapter module local gate | 368 | 0 | 102 | 470 | `mvn -pl s3-reactive-api-adapter test` PASS with AWS CLI visible (`/usr/bin/aws`, AWS CLI 2.34.32); the run also checked that Spring Boot did not emit a generated-password log. This includes actual AWS CLI-backed Cucumber execution, including EP-1 AWS CLI/e2e validation. |
| EP-5 Admin API operability, graceful shutdown, backup/restore, DR, and SLO alerting | 14 | 0 | 2 | 16 | `PhaseEp5OperabilityRequirementsCucumberTest` PASS: Admin API `/admin/live` returns liveness status and link to readiness; `/admin/ready` returns readiness status with storage-policy, storage-device, and disk-set catalog components ready; missing catalogs return `503 not-ready`; SIGTERM shutdown exits without forced termination and preserves a committed S3 object across storage-engine process restart; single PutObject, multipart UploadPart, CompleteMultipartUpload, and two concurrent PutObject requests are drained with HTTP 200 before exit, a bounded mixed load of three writes and two reads is also drained, while cancelled-and-aborted and abort-wins-over-completion paths leave no multipart artifacts; the PutObject survives restart directly, while the drained part ETag completes the multipart upload after restart and the final byte count/checksum matches; offline backup/restore copies the storage root, simulates primary data loss, restores, and reads the committed S3 object back; single-node DR rehearsal validates RTO 30 seconds and RPO last completed offline backup; shipped Prometheus/Loki alert rules and `docs/runbooks/slo-alerts.md` cover SLO objectives, first-response links, generated-password regression detection, and manifest-schema regression detection. Generated-password count: 0. |
| EP-5 live Prometheus/Alertmanager delivery (opt-in Docker runner) | 1 | 0 | 15 | 16 | `PhaseEp5LiveAlertDeliveryRequirementsCucumberTest` PASS: shipped rule pack passes `promtool`; Prometheus evaluates the Admin liveness alert against a failing probe and Alertmanager delivers it to the temporary operator webhook. Generated-password and Netty leak counts: 0. |
| EP-5 delivery workflow and release identity specs | 2 | 0 | 1 | 3 | `PhaseEp5DeliverySpecsCucumberTest` validates mandatory full-root CI ordering, generated-document/source checks, SemVer coherence, OCI labels/tags, digest recording, and native-image experimental status. |
| EP-5 immutable local publication rehearsal | 1 | 0 | 2 | 3 | Docker-required `PhaseEp5LocalReleasePublicationCucumberIT` publishes `0.1.0`, `0.1`, and commit-SHA tags to an ephemeral OCI registry, verifies one digest, and validates overwrite refusal. |
| EP-5 JVM persistent-volume replacement | 1 | 0 | 15 | 16 | Docker-required `PhaseEp5JvmContainerReplacementRequirementsCucumberIT` validates non-root execution, SIGTERM, container recreation, healthy Admin probes, exact S3 readback, and OCI identity. |
| EP-5 storage-engine metadata schema migration specs | 7 | 0 | 0 | 7 | `PhaseEp5StorageMigrationSpecsCucumberTest` PASS for the schema-2 typed-manifest baseline; focused `REQ-PIPELINE-017` separately validates schema-3 EC writes with explicit reconstruction layout. Multipart upload sessions, bucket registry records, object configuration records, S3 object manifest references, object ACL sidecars, and bucket capacity ledgers declare schema version `1`. Legacy records without a version remain readable as compatibility version `0`; unsupported future version `999` is rejected. The capacity ledger also rejects malformed versions, preserves used/quota/rejection accounting, and clears stale reservations on reopen. |
| EP-6 capacity and overload semantics | 8 applicable scenario instances | 0 | 1 TCP-profile scenario filtered | 9 | `PhaseEp6PerformanceCapacityCucumberIT` passes under `-Xmx256m`: maximum object/multipart boundaries, early and streaming rejection, timeout cleanup, concurrency recovery, and rate refill. |
| EP-6 real TCP connection cap | 1 | 0 | 0 | 1 | `PhaseEp6ConnectionCapCucumberIT` holds four real sockets, rejects/closes the fifth, keeps accepted connections alive, and admits a replacement after release. |
| EP-6 deterministic CI load | 4 | 0 | 5 | 9 | `PhaseEp6CiLoadCucumberIT` executes the 45-second eight-worker workload and emits `target/ep6/results/ci/result.json` plus checksummed summary/log evidence. |
| EP-6 deterministic soak | 3 | 0 | 6 | 9 | `PhaseEp6SoakCucumberIT` actually executed for 900 seconds and emitted bounded-heap, checksum, idle-resource, and reproducibility evidence under `target/ep6/results/soak/`. |
| Source/build hygiene and packaging first pass | n/a | 0 | Docker bridge unavailable locally; validated with host network | n/a | `mvn -B validate --no-transfer-progress` PASS / BUILD SUCCESS; `bash scripts/check-source-hygiene.sh` PASS. Native host packaging validates with Oracle GraalVM 25: `mvn -Pnative -pl bootstrap-application -am -DskipTests native:compile` PASS, followed by a native process smoke check on ports 18080/18081 where `/admin/health` returned healthy JSON and no generated Spring Security password banner appeared. Native Docker packaging validates with `docker build --network=host -f Dockerfile.native -t magrathea-objectstorage:native .` PASS and container smoke validation against `/admin/health`, S3 ListBuckets XML/JSON, and S3 bucket/object PUT/GET; runtime image contains no `java`/`javac` and now defaults packaged single-node containers to storage-engine profile/catalog settings. Root JVM Docker packaging validates with `docker build --network=host -f Dockerfile -t magrathea-objectstorage:jvm .` PASS, appendix freshness/docs/UI regeneration gates, unmasked Maven packaging, non-root runtime ownership of `/app/data`, packaged storage-engine YAML catalogs, Admin health/live/ready smoke, S3 ListBuckets XML, bucket/object PUT/GET smoke, selected storage-engine backend log verification, and 0 generated-password banners in build/runtime logs. The sandbox's default Docker bridge still fails before project steps with veth setup errors, so host networking was used for Docker validation. Root `-Pcoverage` is documented as the canonical coverage profile; duplicate module-level coverage profile consolidation remains pending in `bootstrap-application/pom.xml`. |
| Module/layering architecture bounded first pass | n/a | 0 | n/a | n/a | `scripts/check-module-layering.sh` is wired into root-only `validate`; latest full `mvn -B --no-transfer-progress clean test -o` gate passed and includes the root validate/layering guard. Backend selection context-test evidence remains in place; package naming remains bridged by explicit scan roots. |
| Current ordinary root Maven gate | 1177 | 0 | 750 | 1927 | 2026-07-14 root `mvn clean test` PASS across 18/18 reactor projects; 128 Surefire XML reports. Opt-in EP-10 real-process acceptance, focused EP-8 supply-chain, and Docker-required modes were not activated. The dirty-working-tree result is supporting integration evidence only, not replacement semantic or acceptance supply-chain evidence. |
| S3 API semantic coverage report | 1 | 0 | 0 | 1 | `S3ApiSemanticCoverageReportSpecsCucumberTest` and `python3 scripts/generate-s3-api-coverage.py --check` PASS: all 111 official operations have one row; 108 have mapped handlers, 20 have explicit operation-linked implemented-and-validated evidence, and 91 remain pending completion or stronger evidence/classification. |
| Gherkin requirements/specs appendix generator check | n/a | 0 | n/a | 534 scenarios | `python3 scripts/generate-gherkin-requirements-appendix.py --check` PASS; generated ARC42 appendix is fresh: 534 scenarios from 34 feature files grouped by ADR 0020 stakeholder audience. |
| EP-8 architecture contract | 13 | 0 | 0 | 13 expanded scenarios | `PhaseEp8ArchitectureContractCucumberTest` PASS with 103 steps. ADR 0027 is accepted architecture-only; contract validation does not implement EP-10 behavior. |
| Historical EP-8 supply-chain evidence | 9 | 0 | 0 | 9 expanded scenarios | `PhaseEp8SupplyChainEvidenceCucumberIT` PASS with 103 steps against clean revision `209b3170b64d3311ba9b773fdb7bd5581519e682` and exact unpublished image ID `sha256:e5b5862948853a613f8b92adc8135fa81b84bdd9f36c9ae8d69b9a6bd6dd7966`; hashes apply only to that historical packet. Current complete-reactor `REQ-SUPPLY-001` is implemented-not-e2e-validated because the four cluster modules lack a new clean-revision application SBOM/license/image packet. |
| EP-10 opt-in shared real-process S3 gate (`REQ-CLUSTER-001..005/019/020`, `-Pep10-cluster-tests`) | 14 | 0 | 0 | 14 scenarios / 188 steps | Java 21 dedicated profile, not ordinary `mvn test`: seven shared requirements execute once through WebTestClient and once through AWS CLI against real A/B/C child JVMs. Includes the prior publication/failover/restart scope and bounded missing/corrupt current-generation repair. Focused repair-only `019/020`: 4 scenarios / 80 steps. |
| EP-10 focused mechanism gates (`REQ-CLUSTER-008..013`) | PASS | 0 | tag-filtered scenarios excluded per runner | Focused reports | Real Ratis control/TLS, grpc-java streaming/TLS/cancellation/deadline, cross-module publication, and post-repair data-plane regression pass. |
| EP-10 focused architecture contract (`REQ-CLUSTER-014`, 2026-07-14) | 1 | 0 | 0 | 1 scenario / 17 steps | `ReqCluster014ArchitectureContractCucumberTest` passed its sole internal source/build mode: 17 reactor modules, 87 direct non-test dependencies plus 2 profile declarations, 473 tracked production Java JDK ASTs, one versioned proto3 contract, layering-script execution, and 12 missing/empty/malformed plus 4 supported POSIX unreadable fail-closed probes with exact-path and input-hash preservation. Coordination 7/54, control 2/19, data 4/40, repair 22/294, EP-8 architecture 13/103, source hygiene, and appendix freshness passed. CI is wired but has not run. No S3 or runtime-side-effect evidence follows. |
| Historical EP-10 repair-control gate (`REQ-CLUSTER-021..026`, 2026-07-13) | 22 | 0 | 0 | 22 scenarios / 210 steps | Historical control/reconciliation result preserved exactly; it did not yet execute the complete `024` real-filesystem/gRPC matrix and is not the current status source. |
| EP-10 focused `REQ-CLUSTER-024` gate (2026-07-14) | 7 | 0 | 0 | 7 scenarios / 168 steps | `ReqCluster024CucumberTest` passes the complete bounded seven-point interruption catalogue with independent B-JVM crash/restart, actual B-to-C grpc-java/mTLS reads, token-specific filesystem effects and inspection, fencing, snapshot recovery, reply withholding, and live A-to-C leadership transfer. `024` is implemented-and-validated within this scope. |
| EP-10 post-`024` repair/data regressions (2026-07-14) | 22 + 4 | 0 | 0 | repair-control 22 / 294; data-plane 4 / 40 | Existing repair-control and data-plane suites pass; control/TLS regressions, module compilation, layering, source hygiene, and diff checks also pass. |
| EP-10 focused `REQ-CLUSTER-027` gate (2026-07-14) | 2 | 0 | 0 | 2 scenarios / 36 steps | `ReqCluster027CucumberTest` passes bounded fixed A/B/C periodic current-reference discovery, fail-closed exclusive paging, named local filesystem probes, existing consensus/fenced grpc-java mTLS repair, failures/retries/races, process-local restart-from-first, and bounded observability. Broad `017` remains partial; no rebalance, cleanup, wider healing/topology, PA-6 execution, ADR 0030 chaos, scale, or readiness claim follows. CI is wired but has not run. |
| EP-1 Spring Security Reactive targeted security slice plus specs sanity | 40 | 0 | 0 | 40 Surefire tests/examples | `mvn -pl s3-reactive-api-adapter -Dtest=PhaseEp1SecurityIdentityRequirementsCucumberTest,PhaseEp1SecurityIdentityAwsCliCucumberTest,SigV4SecuritySpecsCucumberTest,Ep1SecurityServicesSpecsCucumberTest,SpecificationsCucumberTest test` PASS. Business Need Cucumber executes all 13 EP-1 secured-mode scenario examples through Spring Security Reactive in both WebTestClient and AWS CLI/e2e modes, including exact payload-hash replay, payload-hash mismatch rejection, deny-by-default, explicit-deny-overrides-allow, PublicAccessBlock/public ACL denial, expected-owner mismatch, durable redacted file audit, and an SSE-S3 encrypted-at-rest inspection slice; Ability Cucumber executes 7 maintainer-facing verifier/filter component-spec examples; existing fs-concurrency specs still pass. EP-1 backing-service specs validate durable encrypted credentials/revocation, policy reload, tamper-evident audit, and durable key-management material. |
| EP-7 Admin API Cucumber | 18 | 0 | 17 tag-filtered | 35 discovered | `PhaseEp7AdminApiRequirementsCucumberTest` PASS with 132 executed steps for REQ-ADMIN-023..031. Includes backend/catalog/status, validation, capacity/quota, route-boundary, and six truthful 503 provider-not-configured examples. |
| EP-7 frontend Vitest | 86 | 0 | 0 | 86 | 14 files PASS: shell contracts/static boundaries, task-group composition, application/browser and appearance adapters, themes, template, operational feedback, and accessibility components. |
| EP-7 Playwright/axe/visual regression | 57 | 0 | 0 | 57 | Chromium PASS: 19 tests at each viewport width 360/768/1440, including 3/3 axe scans with zero violations and 12/12 Linux Chromium screenshot baselines. |
| EP-7 frontend lint/typecheck/build boundaries | PASS | 0 | n/a | n/a | ESLint passed with 0 warnings/errors; typecheck passed for 6 workspaces; template validation, deterministic extension removal, and product build passed for 5 packages/apps. Canonical Docker `frontend-packaging-validation` rerun PASS in 83.736s: dual product builds and deterministic path-sorted SHA-256 inventories matched; Product Shell digest `sha256:ee6a77dad38145fafe1d6ab691ea318eb922537a9951feeed58d35ebfbc91536`. |
| EP-7 live Admin API browser slice | 8 endpoint/status observations | 0 | n/a | 8 | Built UI used transport-only browser routing, with no JSON fixtures, against `bootstrap-application` on Admin port 8081: `200 /admin/health`, `200 /admin/live`, `200 /admin/backend-status`, `503 /admin/ready`, `503 /admin/storage-devices`, `503 /admin/reports/recovery`, `503 /admin/reports/garbage-collection`, and `503 /admin/reports/scrub`. The UI showed selected `in-memory`, dashboard unavailability, and three provider-not-configured data-hygiene states. The bare single-node runtime had no YAML catalogs, so configured storage-engine/degraded-device priority remains deterministic-fixture evidence. |
| JaCoCo coverage | See section | - | - | - | Canonical reactor report under `target/site/jacoco-aggregate`; threshold-free JSON baseline under `target/site/quality-evidence/summary.json`. |
| OWASP Dependency-Check 12.2.2 | n/a | Scan error | n/a | Incomplete | Current-revision NVD HTTP 429 after 190,000/364,891 records; `scanStatus=error`, `clean=null`, 8 errors. Monitoring-only unknown/error: no vulnerability-findings, clean-scan, zero-vulnerability, or remediation claim. |

## Current Verified Results

| Scope | Evidence | Result | Notes |
|---|---|---:|---|
| Phase 2 filesystem reliability (`storage-engine-reactive-infrastructure`, `s3-reactive-api-adapter`) | Spec source: `s3-reactive-api-adapter/src/test/features/specs/phase-2-filesystem-reliability.feature`; `mvn -B -pl s3-reactive-api-adapter -am test -Dsurefire.failIfNoSpecifiedTests=false`; `mvn -B test`; isolated Phase 2 WebTestClient spec runner; dedicated storage-engine AWS CLI Phase 2 validation | Targeted adapter gate PASS; full Maven test PASS; Phase 2 WebTestClient: 11 examples, 7 passed, 4 `@awscli` examples excluded/skipped; Phase 2 AWS CLI: 11 discovered, 4 passed, 7 skipped/excluded, 0 failed | Implemented evidence covers atomic chunk temp-file/fsync/rename writes with SHA-256 sidecar checksums, atomic manifest temp-file/fsync/rename writes with checksum trailer, read-time chunk/manifest checksum verification, `FileSystemRecoveryScanner` reporting/quarantine/idempotence, S3 XML mapping for storage-engine integrity errors, disabled-by-default write fault injection for interrupted chunk/manifest write tests, and defaulting `null`/blank storage class to `STANDARD` for storage-engine `PutObject`. Phase 2 is implemented-and-validated for declared scope only; distributed readiness, broader S3 semantic completion, and later phases remain pending. The REQ-FS-006 same-key scenario exposed a real torn-reference concurrency defect that was fixed and stress re-validated 6/6 (see the dedicated section below); the feature tag was restored `@partial` → `@implemented-and-validated`. |
| Streaming architecture specs — REQ-PIPELINE-007..012 (`s3-reactive-api-adapter`, `storage-engine-reactive-infrastructure`) | Spec source: `s3-reactive-api-adapter/src/test/features/specs/phase-3-reactive-pipeline.feature`; Cucumber static architecture runner: `Phase3ReactivePipelineStaticArchitectureSpecsCucumberTest`; fast static guard: `ReactiveUploadStreamingArchitectureTest` for REQ-PIPELINE-007/008 | Cucumber static architecture runner PASS: 9 discovered, 6 passed, 3 skipped. Phase 5 WebTestClient/AWS CLI semantic regressions also PASS after multipart streaming changes. | Implemented and validated: `S3ObjectOperationsHandler.putObject` computes ETag/length via a single-pass `UploadDigest` tee; `FixedWindowDedupStep` accumulates fixed windows incrementally; non-range/range GetObject streams content; `S3MultipartHandler.uploadPart` and `uploadPartCopy` pass DataBuffer streams to `S3MultipartPartStore`; the part store writes with `DataBufferUtils.write` and reads with `DataBufferUtils.read`. `DataBufferUtils.join` and whole-body/whole-part materialization are forbidden in the covered methods. |
| REQ-FS-006 same-key concurrency defect fix (`object-store-reactive-repository-storage-engine-infrastructure`) | Unit test: `S3ObjectManifestReferenceStoreConcurrencyTest`; Phase 2 AWS CLI runner stress re-validation (6 fresh-JVM runs); 2026-07-09 full Maven gate | Unit test PASS: 3 tests, 0 failures. Stress re-validation PASS: REQ-FS-006 same-key 6/6 (previously failed 4/4 in one session). Latest full Maven gate PASS. | Defect: a non-atomic find→save read-modify-write on the S3 object reference in `StorageEngineReactiveS3ObjectRepository.save(...)` / `S3ObjectManifestReferenceStore` caused torn references under concurrent same-key PUTs (checksum from one upload, body from another; observed as an AWS CLI CRC64NVME checksum mismatch). Fix: `S3ObjectManifestReferenceStore.commitLatest` serializes the whole read-compose-write cycle under a striped per-key `ReentrantLock` (64 stripes) and persists via temp file + `ATOMIC_MOVE` (`REPLACE_EXISTING`, with fallback if unsupported); the repository composes the complete reference inside one serialized per-key commit. Semantics: last-writer-wins, crash-safe. Feature tag restored `@partial` → `@implemented-and-validated` in `phase-2-filesystem-reliability.feature`. |
| Phase 3 reactive pipeline (`storage-engine-reactive-application`) | Shared `phase-3-reactive-pipeline.feature` plus pipeline-unit, WebTestClient, static-architecture, runtime-backpressure, and focused `ReqPipeline017EcReconstructionCucumberTest` runners | Declared EP-3 modes pass; focused `017` passes 5 scenarios / 46 steps and `Phase3PipelineUnitSpecsCucumberTest` remains green | `@implemented-and-validated` for the declared local EP-3 scope. ADR 0032 bounds `017` to schema-3 facts and one-stripe output-only reconstruction; no distributed placement/transfer, repair publication, daemon, rebalance, cleanup, or chaos claim follows. |
| Phase 5 S3 semantic compatibility (`s3-reactive-api-adapter`) | Requirement feature: `s3-reactive-api-adapter/src/test/features/requirements/phase-5-s3-semantic-compatibility.feature`; WebTestClient runner: `Phase5S3SemanticCompatibilityRequirementsCucumberTest`; AWS CLI runner: `Phase5S3SemanticCompatibilityAwsCliCucumberTest`; step glue under `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/` and `.../phase5awscli/`; focused gates listed in the summary. | Phase 5 WebTestClient runner PASS: 33 discovered, 32 passed, 1 skipped. Phase 5 AWS CLI runner PASS: 33 discovered, 14 passed, 19 skipped/excluded, 0 failures/errors. Phase 5 multipart full-process restart runner PASS: 33 discovered, 1 passed, 32 skipped. | `@implemented-and-validated` for declared Phase 5 WebTestClient semantics including UploadPartCopy copied-byte assembly and multipart XML error semantics. REQ-S3-002-C is `@implemented-not-e2e-validated` because multipart uploaded-part durability is validated through WebTestClient plus a direct same-directory filesystem repository probe, not a full process/Spring restart. REQ-S3-007 scenarios pass as explicit `@not-implemented` or `@config-only` classification for versioning, object lock, and lifecycle; no enforcement is claimed. Phase 5 AWS CLI validates the `@awscli-required` subset selected by its runner. |
| Phase 6 distributed readiness (`storage-engine-domain`) | Spec source: `s3-reactive-api-adapter/src/test/features/specs/phase-6-distributed-readiness.feature`; domain implementation: `storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/distributed/`; tests: `DistributedPlacementPlannerTest`, `QuorumPolicyTest`, `AntiEntropyPlannerTest`, `RebalancePlannerTest`, `DistributedReadinessReporterTest`; gates: `mvn -B -pl storage-engine-domain test --no-transfer-progress`, `mvn -B test --no-transfer-progress` | Storage-engine domain gate PASS: 164 tests, 0 failures, 0 errors, 0 skipped. Full Maven gate PASS / BUILD SUCCESS: 883 test cases observed, 0 failures, 0 errors, 11 skipped. | `@implemented-not-e2e-validated` for the broad modeled domain/unit scope. Deterministic placement, health-aware membership decisions, quorum decisions, anti-entropy/healing plans, rebalance plans, and readiness classification are modeled and unit validated. EP-10 separately validates fixed A/B/C `N=3/W=2` whole-object replication/publication, bounded current-generation repair, and bounded periodic current-reference anti-entropy; real dynamic membership, EC transfer, rebalance, automated orphan cleanup, wider healing/topology behavior, and general multi-node readiness remain absent. No public storage-engine object API endpoints were added; S3 object behavior remains exposed through S3-compatible APIs only. |
| Domain quality bounded first pass (`storage-engine-domain`, `object-store-domain`) | Tests: `object-store-domain/src/test/java/.../DomainCollectionImmutabilityTest.java`, `storage-engine-domain/src/test/java/.../DomainCollectionImmutabilityTest.java`, and additional `StoredObjectTest` restore invariant coverage; gates: `mvn -B -pl storage-engine-domain,object-store-domain test --no-transfer-progress`, `mvn -B test --no-transfer-progress` | Targeted domain gate PASS: storage-engine-domain 172 tests and object-store-domain 292 tests, 0 failures/errors. Full Maven gate PASS / BUILD SUCCESS. | Bounded first pass only: validates defensive collection copies/immutable exposure and strengthened `StoredObject` invariants. `StoredObject` remains mutable through controlled lifecycle methods; duplicate/ambiguous encryption class names, explicit `Bucket` deleted terminal state, and broad immutable aggregate/lifecycle redesign remain open. |
| Runtime correctness bounded first pass (PLAN section D) | Tests strengthened for `StorageEngineReactiveS3ObjectRepository`, `FileSystemManifestRepository`, and filesystem scheduling; gates: `mvn -B -pl object-store-reactive-repository-storage-engine-infrastructure,storage-engine-reactive-infrastructure,s3-reactive-api-adapter,bootstrap-application -am test --no-transfer-progress`, `mvn -B test --no-transfer-progress` | Targeted module gate PASS / BUILD SUCCESS. Full Maven gate PASS / BUILD SUCCESS. | Evidence covers real storage-engine repository read-through via manifest reference and `orchestrator.read(...)`, manifest round-trip of declared upload checksum and multipart part checksum results, and `BlockingFileSystemOperation` bounded-elastic scheduling for blocking filesystem work. Later EP-3 work has closed GetObject streaming and semantic multipart assembly/copy; broader staged pipeline/runtime backpressure evidence remains open. |
| Gherkin requirements/specs ARC42 appendix (`docs/arc42/generated/gherkin-requirements.adoc`) | `python3 scripts/generate-gherkin-requirements-appendix.py --check` | PASS: generated appendix is fresh; 534 scenarios from 34 feature files; all five local `REQ-PIPELINE-017` scenarios report implemented-and-validated | Documentation-generation evidence only. Cluster `REQ-CLUSTER-015` remains not implemented and broad cluster `017` remains partial. |
| Source/build hygiene gates (PLAN section A safe first pass) | `mvn -B validate --no-transfer-progress`; `bash scripts/check-source-hygiene.sh`; root JVM `docker build` and smoke | PASS / BUILD SUCCESS; PASS; Docker PASS with host networking | Evidence covers Maven validation and the source hygiene script that fails on source-tree `.class` files, root `com/`, root `META-INF`, and generated `META-INF/maven` outside ignored build output directories. `.gitignore`, `.dockerignore`, root Maven plugin/coverage-profile documentation, and Dockerfile runtime healthcheck support were updated. Root JVM Docker validation now passes with public ECR mirrored base images, full `scripts/` copy for Maven layering checks, no Maven fail-never mode, non-root writable `/app/data`, packaged storage-engine YAML catalogs, Admin `/admin/health`, `/admin/live`, `/admin/ready` ready status, S3 ListBuckets XML, bucket/object PUT/GET smoke, selected storage-engine backend log verification, and 0 generated-password banners. Duplicate module-level `coverage` profile consolidation in `bootstrap-application/pom.xml` remains pending. |
| Module/layering architecture guard (PLAN section B) | `scripts/check-module-layering.sh`; `mvn -B validate --no-transfer-progress`; `mvn -B test --no-transfer-progress` | PASS; PASS / BUILD SUCCESS; PASS / BUILD SUCCESS | The guard blocks application-to-infrastructure dependency inversions for object-store, storage-engine, and repository-application modules; preserves explicit `objectstore`/`objectstorage` package naming scan roots while the naming inconsistency remains; and checks explicit backend profile separation. Existing backend selection context tests still assert default in-memory beans versus storage-engine beans and fail-fast backend conflicts. Full Maven build succeeded after the ARC42 docs conversion fix changed generated appendix output from `NOTE:` admonition syntax to a plain paragraph. This is bounded first-pass evidence, not complete architecture cleanup or an ArchUnit/Java test claim. |
| Phase 5 domain planning (`storage-engine-domain`) | Commit `b0a5f74`; `PersistencePlannerMinioStandardTest` | 152 tests passing, 0 failures | Verifies deterministic `MINIO_STANDARD` persistence planning in the domain model. |
| Phase 5 YAML catalogs and MINIO_STANDARD integration (`storage-engine-reactive-infrastructure`) | Commit `0ec84cf`; `MinioStandardIntegrationTest` | 26 tests passing, 0 failures | Verifies YAML catalog/device integration and `MINIO_STANDARD` selection with S3 storage class `STANDARD`, dedup disabled, EC planning `4 data / 2 parity`, replication factor `1`, compression disabled, and encryption disabled by default; storage-engine runtime read/write wiring and physical EC shard placement remain pending for Phase 6/7. |
| Phase 8 backend Admin API (`admin-api-adapter`) | `mvn -B -pl admin-api-adapter -am test`; `AdminRouterTest` | 9 tests passing, 0 failures, build success | Verifies configuration-as-code/read-only admin catalog behavior for policies/devices/disk sets, structured validation responses for `POST /admin/storage-policies/validate`, non-persistence of validation, and runtime rejection of policy mutations. |
| Section E storage-engine configuration bounded first pass | `YamlStoragePolicyCatalogTest` (14), `YamlStorageDeviceCatalogTest` (10), `YamlDiskSetCatalogTest` (12), `MinioStandardIntegrationTest` (2), `StorageEngineMissingExternalConfigTest` (1), `StorageEngineBackendContextTest` (3), `AdminRouterTest` (9); `mvn -B test` PASS | 52 tests, 0 failures, 0 errors | `@implemented-and-validated` for declared Section E scope. YAML catalogs validate duplicate IDs, malformed YAML, domain-invalid configs, zero-device disk sets, blank device references, unresolved device references. MINIO_STANDARD loaded from YAML produces deterministic EC `4/2` plans. Storage-engine backend fails fast on missing config. Mutually exclusive `storage-engine` vs `single-node` profile selection proven. Admin API uses same catalog beans (unit-level). Gaps: no full Spring Boot integration test for admin API with real YAML catalogs; physical EC shard placement and multi-node topology remain modeled only. |
| Phase 1 upload reliability (`bootstrap-application` + Cucumber runners) | Bootstrap tests: `StorageEngineRestartSafetyTest`, `StorageEngineHttpReadAfterWriteTest`, `StorageEngineUploadAtomicityTest`, `StorageEngineIntegrityDetectionTest`; Phase 1 Cucumber runners (WebTestClient + AWS CLI) in the 2026-07-02 Podman gate, run twice (before/after the feature-file tag refresh) | Bootstrap evidence per previous Maven runs; Phase 1 runners: 28 scenarios discovered, 9 executed per mode, 0 failures, identical on both runs | `@implemented-and-validated` for REQ-UPLOAD-001/002 under accepted bootstrap-integration validation mode. REQ-UPLOAD-003 executable validation was removed because multi-durable-chunk non-dedup persistence is intentionally absent. REQ-UPLOAD-004 is now `@implemented-and-validated`: runtime-validated independently in both WebTestClient and AWS CLI modes, and the feature-file tag has been refreshed from `@implemented-not-e2e-validated` to `@implemented-and-validated` with the feature-level `@partial` tag removed. REQ-UPLOAD-005/006 retain previous Cucumber runtime evidence. |
| ADR 0021 single-node backend requirements migration (`s3-reactive-api-adapter`) | `SingleNodeBackendWebTestClientRequirementsCucumberTest` + `SingleNodeBackendAwsCliRequirementsCucumberTest`; migrated `requirements/single-node-backend-*.feature` files | WebTestClient: 260 discovered / 234 passed / 26 skipped; AWS CLI: 72 discovered / 26 passed / 46 skipped; 0 failures/errors | Replaces the legacy `features/object-store/` and `features/awscli/` runners. The migrated requirements explicitly scope these scenarios to the single-node in-memory backend, not the storage-engine backend. |

### Phase 2 Filesystem Reliability Scenario Status

| Requirement | Scenario | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-FS-001 | Interrupted chunk write | Isolated Phase 2 WebTestClient spec runner | ✅ Passed | Disabled-by-default write fault injection validates interrupted chunk writes do not publish incomplete chunks. |
| REQ-FS-002 | Interrupted manifest write | Isolated Phase 2 WebTestClient spec runner | ✅ Passed | Disabled-by-default write fault injection validates interrupted manifest writes do not publish incomplete manifests. |
| REQ-FS-003 | Durable artifact integrity and post-commit corruption handling | Isolated Phase 2 WebTestClient spec runner; dedicated storage-engine AWS CLI Phase 2 validation | ✅ Passed | Upload publication follows temporary-file fsync and incremental SHA-256 reread verification. GetObject opens payload artifacts once, exposes the committed integrity metadata, and leaves post-commit payload mismatch detection to the client; EP-4 scrubbing owns server-side at-rest detection and repair. |
| REQ-FS-004 | Corrupted manifest detection | Isolated Phase 2 WebTestClient spec runner; dedicated storage-engine AWS CLI Phase 2 validation | ✅ Passed | Manifest checksum trailer verification detects corruption and returns a mapped S3 XML error response. |
| REQ-FS-005 | Recovery scanner | Isolated Phase 2 WebTestClient spec runner | ✅ Passed | `FileSystemRecoveryScanner` reports/quarantines incomplete or corrupt state deterministically and idempotently. |
| REQ-FS-006 | Concurrent PUT of different keys | Isolated Phase 2 WebTestClient spec runner; dedicated storage-engine AWS CLI Phase 2 validation | ✅ Passed | Concurrency scenario passed for different object keys. |
| REQ-FS-006 | Concurrent PUT of the same key | Isolated Phase 2 WebTestClient spec runner; dedicated storage-engine AWS CLI Phase 2 validation; `S3ObjectManifestReferenceStoreConcurrencyTest`; 6× fresh-JVM AWS CLI stress re-runs | ✅ Passed (defect found → fixed → 6/6 stress-validated) | A real torn-reference defect (non-atomic find→save read-modify-write in `StorageEngineReactiveS3ObjectRepository.save(...)` / `S3ObjectManifestReferenceStore`) reproduced 4/4 in one session as an AWS CLI CRC64NVME checksum mismatch. Fixed by `commitLatest` per-key striped locking (64 stripes) plus temp file + `ATOMIC_MOVE` persistence; last-writer-wins, crash-safe. Re-validated: Phase 2 AWS CLI runner 6/6 in fresh JVMs; new unit test passes 3/3. Feature tag restored `@partial` → `@implemented-and-validated`. |
| Phase 2 mode summary | WebTestClient and AWS CLI declared scope | WebTestClient + AWS CLI | ✅ Passed for declared scope | WebTestClient: 11 examples total, 7 passed, 4 `@awscli` examples skipped/excluded. AWS CLI: 4 scenarios/examples, 4 passed, 0 failed, 0 skipped. |

### Phase 3 Reactive Pipeline Unit/Application Requirement Status

| Requirement | Scenario | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-PIPELINE-001 | Write pipeline stage order, canonical chunk publication, and events | Pipeline-unit + WebTestClient Cucumber | ✅ Implemented-and-validated | Proves stage order, publication order, UUID chunk filenames, SHA-256 sidecars, canonical filesystem-node readability, clean recovery scan, and exact readback. |
| REQ-PIPELINE-002 | Bounded demand/backpressure/no whole-object aggregation | Pipeline-unit + WebTestClient Cucumber | ✅ Implemented-and-validated | Both modes stream a deterministic 256 MiB demand-controlled body through the production staged pipeline and real filesystem with measured live source-buffer retention and request signals bounded by four, ordered manifest/length validation, exact streamed SHA-256 readback, and anti-aggregation source guards. WebTestClient uses the dedicated 1 MiB-window `PIPELINE` catalog policy. |
| REQ-PIPELINE-003 | Read pipeline manifest artifact order | Pipeline-unit + WebTestClient Cucumber | ✅ Implemented and validated | Both modes use the deterministic 256 MiB fixture. Filesystem artifacts are opened once and emitted as bounded 64 KiB blocks after metadata validation; downstream demand controls reads and incremental test-side digests confirm exact bytes without response aggregation. |
| REQ-PIPELINE-004 | Failure propagation, cleanup, later stages stopped | Pipeline-unit + WebTestClient Cucumber | ✅ Implemented-and-validated | Pipeline-unit covers chunk-persistence fault, manifest-persistence fault, and upstream-body failure; WebTestClient independently covers both persistence-stage faults. All cases validate one deterministic failure, lifecycle-owned cleanup, no later-stage success, no manifest/object reference, and later absence. |
| REQ-PIPELINE-005 | Cancellation event and cleanup | Pipeline-unit + WebTestClient-runner loopback HTTP Cucumber | ✅ Implemented-and-validated | Both modes cancel after at least two unpublished chunks and prove cancellation event propagation, stable upstream demand, DataBuffer release, lifecycle-owned cleanup, no manifest/object/reference, and later 404. |
| REQ-PIPELINE-006 | Instrumentation event metadata/correlation/no payload leakage | Pipeline-unit Cucumber | ✅ Implemented-and-validated for declared mode | Proves payload-free independently attachable stage observers without changing upstream demand. |
| REQ-PIPELINE-007..012 | Static streaming constraints for PutObject, dedup, GetObject, UploadPart/UploadPartCopy, and part storage | Cucumber static architecture specs + `ReactiveUploadStreamingArchitectureTest` for 007/008 | ✅ Implemented-and-validated | PutObject uses a single-pass tee, dedup emits bounded windows, GetObject streams non-range and range responses, UploadPart/UploadPartCopy stream DataBuffers into part storage, and part files are written/read with DataBufferUtils streaming APIs. |
| REQ-PIPELINE-013 | Finite runtime demand at existing S3 streaming boundaries | Runtime-backpressure Cucumber runner | ✅ Implemented-and-validated for adapter-boundary scope | Twelve ordered 64 KiB buffers traverse object, range, multipart write/copy, and part-file read paths with each observed upstream request signal capped at four buffers; no staged lifecycle/event claim. |
| REQ-PIPELINE-017 | Schema-3 facts and bounded local EC 4+2 reconstruction | Focused pipeline-unit Cucumber (`ReqPipeline017EcReconstructionCucumberTest`) | ✅ Implemented-and-validated for exactly five local scenarios | Passes 5 scenarios / 46 steps; covers all 15 four-of-six survivor combinations, short stripes, committed checksum/length validation, one-stripe workspace, dedicated one-worker/16-queue scheduling, fail-closed inputs, and no publication. It excludes repair publication, daemon/scanner, distributed transfer, Ratis jobs, rebalance, cleanup, and chaos. |
| Phase 3 mode summary | Reactive pipeline requirements | Pipeline-unit/WebTestClient selections (REQ-PIPELINE-001..006, 014..016); static architecture Cucumber (REQ-PIPELINE-007..012, 016); runtime boundary Cucumber (REQ-PIPELINE-013); focused local reconstruction (`017`) | ✅ EP-3 implemented and validated | All declared local EP-3 scenarios pass their required modes. The internal staged-pipeline/reconstruction contracts do not require an AWS CLI runner; externally observable S3 upload/read integrity remains shared with the Phase 1 AWS CLI scenarios. |

### EP-4 Space Management and Data Hygiene Status

| Requirement | Scenario | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-SCRUB-001 | Typed and transformed artifact integrity scrubbing | Cucumber Ability + filesystem inspection | ✅ Implemented-and-validated | Covers whole objects, dedup chunks, multipart parts, and EC data shards with NONE, COMPRESS, CRYPT, and COMPRESS→CRYPT chains. SHA-256 is calculated incrementally over the final persisted representation before read-time decompression/decryption; corrupt data and sidecars are quarantined while healthy bytes remain unchanged. |
| REQ-SCRUB-002 | Opt-in periodic scheduling and latest-report retention | Cucumber Ability + static configuration inspection | ✅ Implemented-and-validated | Scheduling is disabled by default and exposes configurable initial delay, interval, and repair policy. Each run atomically replaces the in-memory latest report. |
| REQ-GC-001..004 | Whole-object, shared dedup, multipart, and EC reclamation | Cucumber Ability + repository integration + filesystem inspection | ✅ Implemented-and-validated | Durable pending markers are prepared before S3 reference detachment and resumed after restart. Typed reclamation preserves shared dedup bytes until the final live manifest disappears, removes matching content-address entries, handles abort/expiry part cleanup, and never invents chunks for plain objects. Delete, overwrite, retry, and restart paths are idempotent. |
| REQ-QUOTA-001/002 | Durable logical-byte quota and concurrent reservations | Shared Business Need through WebTestClient and real AWS CLI, plus Admin API inspection | ✅ Implemented-and-validated | Admin configures per-bucket quota and reports used/reserved/quota/rejection state. Streamed writes reserve declared bytes, resize to measured bytes before publication, and atomically serialize single-process reservations; exactly one of two competing 2 MiB uploads commits. Committed usage survives restart and failed reservations are released. |
| REQ-CAPACITY-001 | Filesystem exhaustion failure atomicity | Shared Business Need through WebTestClient and real AWS CLI + bootstrap integration | ✅ Implemented-and-validated | Deterministic post-temp-write ENOSPC and recognized native filesystem exhaustion map to S3 `InsufficientStorage` (HTTP 507), clean unpublished data, publish no manifest/reference, preserve an existing object, and emit redacted backend/root/requested/available capacity fields. |
| EP-4 mode summary | Declared single-node space management and hygiene scope | Ability Cucumber, WebTestClient, AWS CLI, repository and bootstrap integration | ✅ EP-4 implemented-and-validated | Distributed quota coordination is deferred to EP-10. EP-4 scrubbing does not claim repair; local output-only EC reconstruction is separately validated by `REQ-PIPELINE-017`, while publication/self-healing remains absent. |

### Phase 5 S3 Semantic Compatibility Requirement Status

Evidence:
- Requirement source: `s3-reactive-api-adapter/src/test/features/requirements/phase-5-s3-semantic-compatibility.feature`.
- WebTestClient runner: `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/Phase5S3SemanticCompatibilityRequirementsCucumberTest.java`.
- Step glue: `s3-reactive-api-adapter/src/test/java/com/example/magrathea/s3api/cucumber/requirements/Phase5S3SemanticCompatibilitySteps.java`.
- Phase 5 WebTestClient validation passed: 33 discovered, 32 passed, 0 failures, 0 errors, 1 skipped (full-process restart scenario selected by its dedicated runner).
- Full Maven validation passed: `mvn -B test --no-transfer-progress` BUILD SUCCESS, 0 failures/errors.

| Requirement | Scenario area | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-S3-001 | PutObject/HeadObject ETag format and consistency | WebTestClient Cucumber | ✅ Implemented-and-validated | RFC-compatible quoted lowercase MD5 ETags are computed and persisted through the S3 object `etag` domain field and repository translators. |
| REQ-S3-002 | Multipart upload/copy, completion and XML error semantics | WebTestClient + AWS CLI subset + full-process restart Cucumber | ✅ Implemented-and-validated for declared multipart semantics | UploadPart returns real part MD5 ETags; CompleteMultipartUpload returns multipart ETags with the `-{partCount}` suffix and readable assembled objects; UploadPartCopy copies source bytes into persisted part bodies; XML error semantics are covered for malformed copy-source, unknown upload ID, malformed complete XML, invalid part references, and abort/complete conflicts. |
| REQ-S3-002-C | Multipart uploaded-part durability probe | WebTestClient plus direct filesystem same-directory repository probe | ⚠️ Implemented-not-e2e-validated | Filesystem-backed multipart upload repository exists for durable multipart state, but this validation is not a full process/Spring restart e2e scenario. |
| REQ-S3-003 | Byte-range GET and unsatisfiable range | WebTestClient Cucumber | ✅ Implemented-and-validated | Range GET returns 206 Partial Content with `Content-Range`; unsatisfiable ranges return 416 `InvalidRange`. |
| REQ-S3-004 | Conditional GET/HEAD headers | WebTestClient Cucumber | ✅ Implemented-and-validated | Covers `If-Match`, `If-None-Match`, `If-Modified-Since`, and `If-Unmodified-Since`. |
| REQ-S3-005 | CopyObject ETag | WebTestClient Cucumber | ✅ Implemented-and-validated | CopyObject returns the destination ETag instead of a placeholder. |
| REQ-S3-006 | Object tagging lifecycle and inline `x-amz-tagging` | WebTestClient Cucumber | ✅ Implemented-and-validated | Object tags persist through the S3 object `objectTags` domain field and object tagging endpoints. |
| REQ-S3-007 | Versioning, object lock, and lifecycle classification | WebTestClient Cucumber | ✅ Validated as not-implemented/config-only classification | Scenarios intentionally document unsupported/config-only behavior. Versioning, object-lock, lifecycle, and replication enforcement are not claimed complete. |
| Phase 5 mode summary | Declared Phase 5 S3 semantic compatibility scope | WebTestClient + AWS CLI subset + full-process restart runner | ✅/⚠️ Honest declared-scope result | Declared semantic scenarios are `@implemented-and-validated` except REQ-S3-002-C, which remains `@implemented-not-e2e-validated`; the Phase 5 AWS CLI runner validates the `@awscli-required` subset. |

Implemented outputs recorded for Phase 5: RFC-compatible quoted lowercase MD5 ETags for PutObject/HeadObject; ETag and object tag persistence in the S3 object domain model and repository translators; Range GET and conditional GET/HEAD behavior; CopyObject destination ETag propagation; real multipart part/final ETags; UploadPartCopy copied-byte final assembly; S3-shaped multipart XML errors; filesystem-backed multipart upload state; and explicit unsupported/config-only S3 responses.

### Phase 6 Distributed Readiness Modeled Domain Requirement Status

Evidence:
- Spec source: `s3-reactive-api-adapter/src/test/features/specs/phase-6-distributed-readiness.feature`.
- Production domain implementation: `storage-engine-domain/src/main/java/com/example/magrathea/storageengine/domain/distributed/`.
- Semantic unit tests: `DistributedPlacementPlannerTest`, `QuorumPolicyTest`, `AntiEntropyPlannerTest`, `RebalancePlannerTest`, and `DistributedReadinessReporterTest`.
- Targeted validation passed: `mvn -B -pl storage-engine-domain test --no-transfer-progress` — 164 tests, 0 failures, 0 errors, 0 skipped.
- Full validation passed: `mvn -B test --no-transfer-progress` — BUILD SUCCESS; 883 test cases observed, 0 failures, 0 errors, 11 skipped.

| Requirement | Scenario area | Validation mode | Status | Notes |
|---|---|---|---|---|
| REQ-DIST-001 | Modeled placement across failure domains and insufficient-domain failure | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Deterministic modeled topology/placement planner selects replicas across nodes and failure domains and reports insufficient-domain failures. |
| REQ-DIST-002 | Write/read quorum and integrity-aware read failure | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Explicit write/read quorum decision model exists, including integrity-aware read quorum behavior for corrupt or missing replicas. |
| REQ-DIST-003 | Health-aware membership placement and exclusions | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Placement uses `HEALTHY`, `DEGRADED`, and `DOWN` node states with explicit exclusion and risk reasons. |
| REQ-DIST-004 | Anti-entropy findings and planned healing tasks | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Anti-entropy planner produces missing, corrupt, and unrecoverable findings plus planned healing tasks. |
| REQ-DIST-005 | Safe rebalance planning and failed-copy retry classification | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Rebalance planner produces observable move plans and failed-copy retry results without reducing quorum. |
| REQ-DIST-006 | Readiness classification that avoids distributed-production claims | Domain/unit validation | ⚠️ Implemented-not-e2e-validated | Readiness reporter never claims `distributed-production-ready` for local, simulated, or default filesystem mode and lists missing capabilities. |
| Phase 6 mode summary | Broad distributed-readiness modeled domain scope | Domain/unit, with separate bounded EP-10 runtime/mechanism slices | ⚠️ Broad scope remains modeled/unit validated only | EP-10 separately validates fixed A/B/C whole-object publication, bounded current-generation repair, and bounded periodic discovery. Local EC decoding (`REQ-PIPELINE-017`) is separate prerequisite evidence; distributed EC placement/transfer, self-healing, rebalance, cleanup, dynamic membership, wider topology coverage, and general distributed readiness remain absent. |

Implemented outputs recorded for Phase 6: pure domain distributed-readiness model for topology/node health, placement decisions, quorum policy, replica observations, anti-entropy findings/healing tasks, rebalance plans/task results, and readiness reports. No public storage-engine object API endpoints were added; S3 object behavior remains exposed through S3-compatible APIs only. Distributed production readiness remains not achieved.

### AWS CLI Test Status

| Feature | Status | Note |
|---|---|---|
| Single-node backend WebTestClient requirements | ✅ 260 discovered, 234 passed, 26 skipped/excluded | `SingleNodeBackendWebTestClientRequirementsCucumberTest` validates the migrated ADR 0021 single-node backend requirement files through WebTestClient. |
| Single-node backend AWS CLI requirements | ✅ 72 discovered, 26 passed, 46 skipped/excluded | `SingleNodeBackendAwsCliRequirementsCucumberTest` validates the `@awscli-required` subset of the same migrated single-node backend requirement files. |
| Phase 2 WebTestClient specs | ✅ 11 total, 7 passed, 4 `@awscli` examples excluded/skipped | Isolated Phase 2 runner validates REQ-FS-001 through REQ-FS-006 for the WebTestClient-required scope from `specs/phase-2-filesystem-reliability.feature`, including interrupted chunk/manifest write fault injection, corruption detection, recovery scanning, and concurrency scenarios. |
| Phase 2 storage-engine AWS CLI specs | ✅ 11 discovered, 4 passed, 7 skipped/excluded | Dedicated AWS CLI validation passed for the `@awscli-required` Phase 2 examples from `specs/phase-2-filesystem-reliability.feature`: REQ-FS-003, REQ-FS-004, REQ-FS-006 different keys, and REQ-FS-006 same key. |
| Phase 5 AWS CLI requirements | ✅ 33 discovered, 14 passed, 19 skipped/excluded | Phase 5 AWS CLI runner validates the `@awscli-required` subset in `phase-5-s3-semantic-compatibility.feature`; remaining examples are skipped by tag/profile filtering. |
| EP-1 security/identity AWS CLI/e2e requirements | ✅ 13 passed, 0 skipped | `PhaseEp1SecurityIdentityAwsCliCucumberTest` validates REQ-SEC-001..009 against the shared EP-1 Business Need feature with AWS CLI/raw signed HTTP e2e steps and a real RANDOM_PORT server. |
| EP-1 local backing-service specs | ✅ 4 passed, 0 skipped | `Ep1SecurityServicesSpecsCucumberTest` validates REQ-SEC-010..013: encrypted durable access-key storage/revocation, durable policy reload with explicit deny, tamper-evident audit, and durable key-management/SSE material. |
| AWS CLI Cucumber scenarios | ✅ Current dedicated AWS CLI runners green | Single-node backend, Phase 1 upload, Phase 2 filesystem reliability, Phase 5 semantic compatibility, and EP-1 security/identity AWS CLI runners all pass their selected subsets in the latest local gates. |
| `test-aws-cli.sh` standalone | ⚠️ 46/82 passed | Standalone script; most failures are connection errors (server not running at test time), not logic failures. |

## AWS CLI S3 Compatibility

Bucket: `magrathea-cli-test-1780641560-106784`

| Check | Status | Notes |
|---|---|---|
| ListBuckets | ❌ Failed | Expected success, exit code 254 |
| CreateBucket | ❌ Failed | Expected success, exit code 254 |
| HeadBucket existing | ❌ Failed | Expected success, exit code 254 |
| PutObject | ❌ Failed | Expected success, exit code 254 |
| PutObject header capture | ✅ Passed | Headers documented |
| HeadObject existing | ❌ Failed | Expected success, exit code 254 |
| PutObjectAcl | ❌ Failed | Expected success, exit code 254 |
| GetObjectAcl | ❌ Failed | Expected success, exit code 254 |
| PutObjectTagging | ❌ Failed | Expected success, exit code 254 |
| GetObjectTagging | ❌ Failed | Expected success, exit code 254 |
| DeleteObjectTagging | ❌ Failed | Expected success, exit code 254 |
| GetObjectAttributes | ❌ Failed | Expected success, exit code 254 |
| GetObject | ❌ Failed | Expected success, exit code 254 |
| GetObject content matches | ❌ Failed | Expected success, exit code 2 |
| ListObjects | ❌ Failed | Expected success, exit code 254 |
| ListObjectsV2 | ❌ Failed | Expected success, exit code 254 |
| GetBucketLocation | ❌ Failed | Expected success, exit code 255 |
| PutBucketAcl | ❌ Failed | Expected success, exit code 254 |
| GetBucketAcl | ❌ Failed | Expected success, exit code 254 |
| PutBucketTagging | ❌ Failed | Expected success, exit code 254 |
| GetBucketTagging | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketTagging | ❌ Failed | Expected success, exit code 254 |
| GetBucketVersioning initial | ❌ Failed | Expected success, exit code 254 |
| PutBucketVersioning Enabled | ❌ Failed | Expected success, exit code 254 |
| GetBucketVersioning enabled | ❌ Failed | Expected success, exit code 254 |
| ListObjectVersions | ❌ Failed | Expected success, exit code 254 |
| CopyObject | ❌ Failed | Expected success, exit code 254 |
| HeadObject copy existing | ❌ Failed | Expected success, exit code 254 |
| DeleteObjects | ❌ Failed | Expected success, exit code 254 |
| HeadObject copy after DeleteObjects | ✅ Passed | Expected failure |
| PutObject PARANOIC_MODE | ❌ Failed | Expected success, exit code 254 |
| HeadObject PARANOIC_MODE | ❌ Failed | Expected success, exit code 254 |
| GetObjectAttributes PARANOIC_MODE | ❌ Failed | Expected success, exit code 254 |
| DeleteObject PARANOIC_MODE | ❌ Failed | Expected success, exit code 254 |
| DeleteObject | ❌ Failed | Expected success, exit code 254 |
| PutObject SSE-S3 AES256 | ❌ Failed | Expected success, exit code 254 |
| HeadObject SSE-S3 AES256 | ❌ Failed | Expected success, exit code 254 |
| DeleteObject SSE-S3 | ❌ Failed | Expected success, exit code 254 |
| PutObject STANDARD storage class | ❌ Failed | Expected success, exit code 254 |
| HeadObject STANDARD | ❌ Failed | Expected success, exit code 254 |
| GetObjectAttributes STANDARD | ❌ Failed | Expected success, exit code 254 |
| DeleteObject STANDARD | ❌ Failed | Expected success, exit code 254 |
| HeadObject after DeleteObject | ✅ Passed | Expected failure |
| PutBucketCors | ❌ Failed | Expected success, exit code 254 |
| GetBucketCors | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketCors | ❌ Failed | Expected success, exit code 254 |
| GetBucketCors nonexistent | ✅ Passed | Expected failure |
| GetBucketCors after delete | ✅ Passed | Expected failure |
| PutBucketLifecycleConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketLifecycleConfiguration | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketLifecycleConfiguration | ❌ Failed | Expected success, exit code 252 |
| GetBucketLifecycleConfiguration nonexistent | ✅ Passed | Expected failure |
| GetBucketLifecycleConfiguration after delete | ✅ Passed | Expected failure |
| PutBucketPolicy | ❌ Failed | Expected success, exit code 254 |
| GetBucketPolicy | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketPolicy | ❌ Failed | Expected success, exit code 254 |
| GetBucketPolicy nonexistent | ✅ Passed | Expected failure |
| GetBucketPolicy after delete | ✅ Passed | Expected failure |
| PutBucketEncryption | ❌ Failed | Expected success, exit code 252 |
| GetBucketEncryption | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketEncryption | ❌ Failed | Expected success, exit code 254 |
| GetBucketEncryption nonexistent | ✅ Passed | Expected failure |
| GetBucketEncryption after delete | ✅ Passed | Expected failure |
| PutBucketLogging | ❌ Failed | Expected success, exit code 252 |
| GetBucketLogging | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketLogging | ❌ Failed | Expected success, exit code 252 |
| GetBucketLogging nonexistent | ✅ Passed | Expected failure |
| GetBucketLogging after delete | ✅ Passed | Expected failure |
| PutBucketWebsite | ❌ Failed | Expected success, exit code 254 |
| GetBucketWebsite | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketWebsite | ❌ Failed | Expected success, exit code 254 |
| GetBucketWebsite nonexistent | ✅ Passed | Expected failure |
| GetBucketWebsite after delete | ✅ Passed | Expected failure |
| PutBucketNotification | ❌ Failed | Expected success, exit code 254 |
| GetBucketNotification | ❌ Failed | Expected success, exit code 254 |
| GetBucketNotification nonexistent | ✅ Passed | Expected failure |
| PutBucketReplication | ❌ Failed | Expected success, exit code 254 |
| GetBucketReplication | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketReplication | ❌ Failed | Expected success, exit code 254 |
| GetBucketReplication nonexistent | ✅ Passed | Expected failure |
| GetBucketReplication after delete | ✅ Passed | Expected failure |
| PutBucketRequestPayment | ❌ Failed | Expected success, exit code 254 |
| GetBucketRequestPayment | ❌ Failed | Expected success, exit code 254 |
| GetBucketRequestPayment nonexistent | ✅ Passed | Expected failure |
| PutBucketOwnershipControls | ❌ Failed | Expected success, exit code 254 |
| GetBucketOwnershipControls | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketOwnershipControls | ❌ Failed | Expected success, exit code 254 |
| GetBucketOwnershipControls nonexistent | ✅ Passed | Expected failure |
| GetBucketOwnershipControls after delete | ✅ Passed | Expected failure |
| PutPublicAccessBlock | ❌ Failed | Expected success, exit code 254 |
| GetPublicAccessBlock | ❌ Failed | Expected success, exit code 254 |
| DeletePublicAccessBlock | ❌ Failed | Expected success, exit code 254 |
| GetPublicAccessBlock nonexistent | ✅ Passed | Expected failure |
| GetPublicAccessBlock after delete | ✅ Passed | Expected failure |
| PutBucketAccelerateConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketAccelerateConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketAccelerateConfiguration nonexistent | ✅ Passed | Expected failure |
| PutBucketAnalyticsConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketAnalyticsConfiguration | ❌ Failed | Expected success, exit code 254 |
| ListBucketAnalyticsConfigurations | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketAnalyticsConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketAnalyticsConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| GetBucketAnalyticsConfiguration missing id | ✅ Passed | Expected failure |
| DeleteBucketAnalyticsConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| DeleteBucketAnalyticsConfiguration missing id | ✅ Passed | Expected failure |
| PutBucketAnalyticsConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| ListBucketAnalyticsConfigurations nonexistent bucket | ✅ Passed | Expected failure |
| PutBucketInventoryConfiguration | ❌ Failed | Expected success, exit code 252 |
| GetBucketInventoryConfiguration | ❌ Failed | Expected success, exit code 254 |
| ListBucketInventoryConfigurations | ❌ Failed | Expected success, exit code 254 |
| DeleteBucketInventoryConfiguration | ❌ Failed | Expected success, exit code 254 |
| GetBucketInventoryConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| GetBucketInventoryConfiguration missing id | ✅ Passed | Expected failure |
| DeleteBucketInventoryConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| DeleteBucketInventoryConfiguration missing id | ✅ Passed | Expected failure |
| PutBucketInventoryConfiguration nonexistent bucket | ✅ Passed | Expected failure |
| ListBucketInventoryConfigurations nonexistent bucket | ✅ Passed | Expected failure |
| DeleteBucket | ❌ Failed | Expected success, exit code 254 |
| HeadBucket after DeleteBucket | ✅ Passed | Expected failure |
| GetObject nonexistent | ✅ Passed | Expected failure |
| HeadObject nonexistent | ✅ Passed | Expected failure |
| GetBucketLocation nonexistent | ✅ Passed | Expected failure |
| GetBucketVersioning nonexistent | ✅ Passed | Expected failure |
| GetBucketAcl nonexistent | ✅ Passed | Expected failure |
| GetBucketTagging nonexistent | ✅ Passed | Expected failure |
| CopyObject nonexistent source | ✅ Passed | Expected failure |
| PutObject nonexistent bucket | ✅ Passed | Expected failure |
| GetObjectAcl nonexistent | ✅ Passed | Expected failure |

## Maven Surefire Results

> The detailed module-level table below is retained as a previous Phase 10 breakdown. The current aggregate evidence is the 2026-07-14 ordinary root gate summarized above: `mvn -B --no-transfer-progress clean test` passed with BUILD SUCCESS across **18/18 reactor projects**, and **128 Surefire XML reports record 1,927 discovered tests/examples, 1,177 passed/executed, 0 failures, 0 errors, and 750 skipped/tag-filtered**. Opt-in EP-10 real-process acceptance, focused EP-8 supply-chain, and Docker-required modes were not activated; their separate evidence is not replaced by these ordinary Maven totals. The dirty-working-tree root pass is not acceptance supply-chain evidence. Some rows below remain historical snapshots; use the Summary and Current Verified Results sections above for current validation evidence.

| Module | Report | Tests | Failures | Errors | Skipped | Status |
|---|---|---:|---:|---:|---:|---|
| object-store-domain | com.example.magrathea.objectstore.domain.BucketAccelerateConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketAnalyticsConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketConfigurationTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketEncryptionConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketIntelligentTieringConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketInventoryConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketLifecycleConfigurationTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketLoggingConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketMetricsConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketNotificationConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketOwnershipControlsTest.txt | 2 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketPolicyTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketReplicationConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketRequestPaymentConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketTest.txt | 54 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.BucketWebsiteConfigurationTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.ChecksumAlgorithmTest.txt | 5 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.ContentDescriptorTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.CorsConfigurationTest.txt | 7 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.EncryptionConfigurationTest.txt | 10 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.EncryptionTypeTest.txt | 2 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.MultipartUploadTest.txt | 9 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.ObjectKeyTest.txt | 11 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.ObjectStoreEventTest.txt | 45 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.PartNumberTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.PublicAccessBlockConfigurationTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.RegionTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.S3ObjectTest.txt | 49 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.StorageClassTest.txt | 7 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.UploadIdTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-domain | com.example.magrathea.objectstore.domain.UploadPartTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| object-store-reactive-application | com.example.magrathea.reactive.application.service.CucumberTest.txt | 18 | 0 | 0 | 0 | ✅ Passed |
| s3-reactive-api-adapter | com.example.magrathea.s3api.awscli.SingleNodeBackendAwsCliRequirementsCucumberTest.txt | 72 | 0 | 0 | 46 | ✅ Passed (26 executed) |
| s3-reactive-api-adapter | com.example.magrathea.s3api.cucumber.requirements.SingleNodeBackendWebTestClientRequirementsCucumberTest.txt | 260 | 0 | 0 | 26 | ✅ Passed (234 executed) |
| admin-api-adapter | com.example.magrathea.admin.web.AdminRouterTest.txt | 9 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.aggregate.StoredObjectTest.txt | 14 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.service.EffectivePolicyResolverTest.txt | 10 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.service.PersistencePlannerMinioStandardTest.txt | 11 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.service.PersistencePlannerTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.service.VirtualDeviceResolverTest.txt | 13 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.DedupConfigTest.txt | 10 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.DiskSetTest.txt | 13 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.ErasureCodingConfigTest.txt | 6 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.ObjectManifestTest.txt | 12 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.ReplicationConfigTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.StorageClassIdTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.StorageDeviceTest.txt | 29 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-domain | com.example.magrathea.storageengine.domain.valueobject.StoragePolicyTest.txt | 11 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-application | com.example.magrathea.storageengine.application.service.ReactiveStorageOrchestratorTest.txt | 4 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-infrastructure | com.example.magrathea.storageengine.infrastructure.filesystem.FileSystemStorageConsistencyTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-infrastructure | com.example.magrathea.storageengine.infrastructure.yaml.MinioStandardIntegrationTest.txt | 2 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-infrastructure | com.example.magrathea.storageengine.infrastructure.yaml.YamlDiskSetCatalogTest.txt | 8 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-infrastructure | com.example.magrathea.storageengine.infrastructure.yaml.YamlStorageDeviceCatalogTest.txt | 6 | 0 | 0 | 0 | ✅ Passed |
| storage-engine-reactive-infrastructure | com.example.magrathea.storageengine.infrastructure.yaml.YamlStoragePolicyCatalogTest.txt | 10 | 0 | 0 | 0 | ✅ Passed |
| object-store-reactive-repository-storage-engine-infrastructure | com.example.magrathea.objectstorage.repository.storageengine.adapter.StorageEngineReactiveS3ObjectRepositoryTest.txt | 1 | 0 | 0 | 0 | ✅ Passed |
| bootstrap-application | com.example.magrathea.bootstrap.DefaultBackendContextTest.txt | 2 | 0 | 0 | 0 | ✅ Passed |
| bootstrap-application | com.example.magrathea.bootstrap.StorageEngineBackendContextTest.txt | 3 | 0 | 0 | 0 | ✅ Passed |
| bootstrap-application | com.example.magrathea.bootstrap.StorageEngineMissingExternalConfigTest.txt | 1 | 0 | 0 | 0 | ✅ Passed |
| **Previous total (Phase 10 quality gate — all 10 modules; superseded by current aggregate above)** |  | **768** | **0** | **0** | **0** | **✅ Passed at that time** |

## Coverage

**JaCoCo** is the current coverage baseline. The canonical gate is `mvn -B -Pcoverage clean verify --no-transfer-progress`. It publishes one reactor-wide HTML/XML/CSV report under `target/site/jacoco-aggregate` and a threshold-free JSON summary under `target/site/quality-evidence/summary.json`. Module-local `.exec` files are aggregate inputs, not competing reports.

**Clover/OpenClover** is optional/legacy. Report HTML (if generated): `target/site/clover/index.html`.

### Reactor-wide JaCoCo baseline (2026-07-12, revision `c3fe8bb1`)

The latest ordinary full-root gate passed 18/18 reactor projects. Its 128 Surefire XML reports contained 1,927 discovered tests/scenarios, 1,177 executed, 0 failures, 0 errors, and 750 skipped/tag-filtered. That dirty-working-tree `mvn clean test` run is supporting integration evidence only; it did not activate the opt-in EP-10 acceptance profile, refresh supply-chain artifacts, or refresh the JaCoCo baseline below. The coverage figures remain the historical 2026-07-12 `c3fe8bb1` baseline, where JaCoCo analyzed all 12 then-current production modules and included cross-module execution data from the S3/Cucumber test module.

| Counter | Covered | Missed | Coverage |
|---|---:|---:|---:|
| Instructions | 48,191 | 12,440 | 79.48% |
| Branches | 1,957 | 1,527 | 56.17% |
| Lines | 8,868 | 2,499 | 78.02% |
| Complexity | 3,311 | 1,801 | 64.77% |
| Methods | 2,671 | 645 | 80.55% |
| Classes | 570 | 65 | 89.76% |

Lowest production modules by instruction coverage were `object-store-reactive-infrastructure` (20.01%), `bootstrap-application` (61.93%), and `object-store-reactive-application` (64.38%). The lowest package was `storage-engine-reactive-infrastructure/.../chaos` (0/254 instructions), followed by `admin-api-adapter/.../admin` (3/23) and `object-store-reactive-infrastructure/.../persistence` (413/2,064).

Nine modules produced `.exec` files. The three class-bearing port/infrastructure modules without module-local execution files were still represented and received coverage from execution data produced elsewhere: `storage-engine-reactive-repository-application`, `object-store-reactive-repository-application`, and `object-store-reactive-infrastructure`. No JaCoCo threshold is enforced; successful generation is descriptive evidence, not a sufficiency claim.

### Dependency vulnerability evidence (updated 2026-07-13)

The opt-in gate is `scripts/run-dependency-check.sh`. It runs OWASP Dependency-Check 12.2.2 for the production reactor, fails verification for any unsuppressed finding with CVSS >= 7.0, and fails closed when the scan is incomplete. Supply `NVD_API_KEY` through a CI secret or a non-committed environment value; the Maven profile reads `NVD_API_KEY` at execution time and masks it in logs. Do not place the key in `pom.xml`, a command-line `-D` option, or committed shell files.

Secure rerun example (the key file must be access-restricted and outside the repository):

```bash
NVD_API_KEY="$(< /secure/path/nvd-api-key)" scripts/run-dependency-check.sh
```

Canonical complete-scan reports are `target/dependency-check-report.json` and `target/dependency-check-report.html`. The normalized analysis is `target/site/dependency-check-analysis.json`; failed-scan evidence is `target/dependency-check-scan-error.json` and `target/dependency-check-scan.log`. The committed suppression policy is `config/dependency-check-suppressions.xml`.

The historical EP-8 clean-revision run for `209b3170b64d3311ba9b773fdb7bd5581519e682` downloaded 190,000 of 364,891 NVD records and then received HTTP 429. Retained evidence records `scanStatus=error`, `clean=null`, and 8 errors. This result is deliberately monitoring-only `unknown/error`; absent or zero counters must **not** be interpreted as vulnerability findings, zero vulnerabilities, or a clean scan. EP-8 performed no dependency remediation or suppression change. The later dirty-working-tree root test did not refresh this assessment.

CI always runs architecture and application supply-chain evidence. Full image, hardened-runtime, OWASP, and evidence-contract validation is scheduled or explicitly enabled through `workflow_dispatch`; artifacts and error evidence are retained even when fail-closed validation exits non-zero. Neither the evidence scripts nor CI jobs publish an application or image.

`REQ-QUAL-001..004` remain `@partial`: the artifacts provide implementation-informed evidence, but the scenarios do not yet have executable semantic validation and the OWASP assessment is incomplete. EP-8 architecture/evidence wiring completion does not alter that status.

> **Coverage note (2026-07-02):** `FixedWindowDedupStep` has no dedicated unit test; its streaming/windowing behavior is validated via the static architecture test `ReactiveUploadStreamingArchitectureTest` and the Cucumber e2e suites.
>
> **Test-only hygiene (2026-07-02):** dangling storage-root symlink cleanup was added in `RequirementsTestApp` and `Phase2StorageEngineAwsCliTestApp` (test scaffolding only; no production code affected).

---

## Previous Phase 10 Quality Gates

**Date:** 2026-06-12 &nbsp; **HEAD:** `351d088`

> This section is retained as historical Phase 10 evidence. The latest counted aggregate before Phase 5 is the Phase 3 validation snapshot above: `mvn -B test` passed with 833 tests, 0 failures, 0 errors, and 11 skipped. The supplied Phase 5 full Maven gate also passed (`mvn -B test --no-transfer-progress`, BUILD SUCCESS, 0 failures/errors), with no restated total count.

### `mvn validate`

✅ BUILD SUCCESS — all module POMs valid.

### `mvn test` — Full Multi-Module Run (previous Phase 10 snapshot)

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| storage-engine-domain | 149 | 0 | 0 | 0 |
| admin-api-adapter | 9 | 0 | 0 | 0 |
| object-store-domain | 287 | 0 | 0 | 0 |
| object-store-reactive-application | 18 | 0 | 0 | 0 |
| s3-reactive-api-adapter (WebTestClient Cucumber) | 239 | 0 | 0 | 0 |
| s3-reactive-api-adapter (AWS CLI Cucumber) | 26 | 0 | 0 | 0 |
| storage-engine-reactive-application | 4 | 0 | 0 | 0 |
| storage-engine-reactive-infrastructure | 29 | 0 | 0 | 0 |
| object-store-reactive-repository-storage-engine-infrastructure | 1 | 0 | 0 | 0 |
| bootstrap-application | 6 | 0 | 0 | 0 |
| **PREVIOUS TOTAL** | **768** | **0** | **0** | **0** |

### JaCoCo Coverage (`mvn -Pcoverage test jacoco:report`)

| Module | Instruction Coverage | Branch Coverage |
|---|---:|---:|
| object-store-domain | 81% | 64% |
| storage-engine-reactive-application | 82% | 74% |
| s3-reactive-api-adapter | 77% | 30% |
| storage-engine-reactive-infrastructure | 54% | 42% |
| storage-engine-domain | 59% | 45% |
| admin-api-adapter | 69% | 46% |
| bootstrap-application | 58% | 29% |
| object-store-reactive-application | 18% | 20% |
| object-store-reactive-repository-storage-engine-infrastructure | 13% | 5% |

> **Note:** Low coverage in `object-store-reactive-application` and `object-store-reactive-repository-storage-engine-infrastructure` reflects that these modules primarily contain port interfaces and thin adapters where runtime behaviour is exercised through integration/Cucumber tests that run against the assembled application rather than isolated units.

Historical Phase 10 quality gates passed with no failures, errors, or skipped tests. The latest supplied full Maven gate for this report, `mvn -B test --no-transfer-progress`, passed with BUILD SUCCESS and 0 failures/errors; the total test count was not restated in the supplied Phase 5 evidence. The Phase 2 WebTestClient skipped/excluded examples are `@awscli` examples covered by the dedicated AWS CLI validation where applicable, not fault-injection validation gaps. Phase 3 reactive pipeline validation is unit/application only and is not yet WebTestClient/AWS CLI/e2e validated. Phase 5 semantic compatibility now has a dedicated AWS CLI runner for its selected subset, and EP-1 security/identity now has WebTestClient plus AWS CLI/e2e evidence for REQ-SEC-001..009.

---

## S3 Semantic Coverage Matrix

> This table classifies each S3 API family by semantic status, not route count.
> **Route mapped** = HTTP route exists; **Stateful behavior** = creates/reads/updates/deletes durable state with observable follow-up; **AWS CLI scenario** = verified via AWS CLI Cucumber or `test-aws-cli.sh`; **Storage-engine scenario** = verified against storage-engine backend; **Semantic status** = overall classification.

### Current Evidence-Based Family Classification

| Family | Operation (examples) | Route mapped | Stateful behavior | AWS CLI scenario | Storage-engine scenario | Semantic status | Notes |
|---|---|---|---|---|---|---|---|
| Object CRUD | PutObject, GetObject, HeadObject, DeleteObject, CopyObject, Range GET, conditional GET/HEAD | Yes | Partial — WebTestClient-validated ETag, range, conditional, copy, and object state semantics for declared Phase 5 scope | Partial pass: existing AWS CLI increment covers put default headers, get content, head, delete, idempotent delete, `STANDARD` storage class via object attributes, and slash-containing object keys; no new Phase 5 AWS CLI runner | Absent | Partial / WebTestClient semantic coverage for declared Phase 5 subset | Phase 5 validates quoted lowercase MD5 ETags, ETag persistence, Range GET 206/416 behavior, conditional GET/HEAD headers, and CopyObject destination ETag through WebTestClient. Storage-engine profile and Phase 5 AWS CLI subsets have focused validation; broader parity remains scoped. |
| Bucket baseline | CreateBucket, HeadBucket, DeleteBucket, ListBuckets, ListObjects, ListObjectsV2 | Yes | Partial — in-memory only | Partial pass: AWS CLI bucket operations increment covers create-bucket, list-buckets, head-bucket, get-bucket-location, get-bucket-versioning, put-bucket-versioning, delete-bucket, duplicate-bucket 409, head-bucket 404, delete-bucket 404, list-objects, and list-objects-v2 | Absent | Stubbed / Partial | Bucket create/head/location/versioning/delete and failure cases now have targeted AWS CLI Cucumber coverage; prefix/delimiter edge cases, continuation tokens, and storage-engine indexes remain unverified |
| Multipart upload | CreateMultipartUpload, UploadPart, UploadPartCopy, CompleteMultipartUpload, AbortMultipartUpload, ListParts, ListMultipartUploads | Yes | Partial — WebTestClient validates real part MD5 ETags, UploadPartCopy copied bytes, final assembly, multipart final ETag suffix, XML error semantics, and filesystem-backed multipart state probe | Partial pass: AWS CLI Cucumber validates the selected multipart lifecycle and UploadPartCopy copied-byte assembly subset | Absent | Partial / WebTestClient semantic coverage for declared Phase 5 subset | UploadPart returns real part MD5 ETags, UploadPartCopy persists copied source bytes, and CompleteMultipartUpload returns `-{partCount}` multipart ETags with readable assembled objects. Multipart restart/durability is implemented-not-e2e-validated for REQ-S3-002-C because that specific check is a same-directory filesystem repository probe; REQ-S3-002-E separately validates part-body completion after a full Spring restart. |
| Bucket configuration | CORS, Lifecycle, Website, Logging, Notification, Replication, Encryption, Versioning, Tagging, etc. | Yes | Partial — config storage or explicit unsupported/config-only classification only | Failing in stale standalone script; no new Phase 5 AWS CLI runner | Absent | Config-only / Stubbed / Not implemented where explicitly declared | Phase 5 validates explicit unsupported/config-only classification for versioning, object lock, and lifecycle. No background job execution or enforcement is claimed. |
| Object metadata/tagging/ACL | PutObjectTagging, GetObjectTagging, GetObjectAttributes, PutObjectAcl, GetObjectAcl, PutBucketTagging, GetBucketTagging, DeleteBucketTagging, PutBucketAcl, GetBucketAcl | Yes | Partial — object tags persist in the S3 object domain model for Phase 5; ACL grant enforcement remains stubbed/partial | Partial pass: AWS CLI third increment covers bucket ACL read/write, object ACL read/write, bucket tagging CRUD, object tagging CRUD, and object attributes (ETag + ObjectSize); no new Phase 5 AWS CLI runner | Absent | Partial / WebTestClient semantic coverage for object tagging | Phase 5 validates object tag persistence through `objectTags`, object tagging endpoints, and inline `x-amz-tagging`. Storage-engine backend remains unverified; ACL grant enforcement remains stubbed. |
| Versioning/delete markers | ListObjectVersions, versioned GET/HEAD/DELETE | Yes | Explicit unsupported/not-implemented classification for Phase 5 scope | Failing in stale standalone script; no new Phase 5 AWS CLI runner | Absent | Not implemented / Stubbed | Phase 5 passing scenarios document unsupported/not-implemented classification. Version IDs, delete markers, latest-version resolution, and enforcement remain unimplemented. |
| Access/security controls | BucketPolicy, PublicAccessBlock, OwnershipControls | Yes | Config storage only | Failing (server not running at test time) | Absent | Config-only / Stubbed | Authorization enforcement absent |
| Analytics/inventory/metrics | Bucket analytics, inventory, metrics, intelligent-tiering | Yes | Config storage only | Failing (server not running at test time) | Absent | Config-only | No background report generation |
| Advanced/specialized | SelectObjectContent, RestoreObject, etc. | Some | None verified | Missing | Absent | Stubbed / Out of scope | Likely out of scope without explicit design |
| Admin/storage-engine APIs | StoragePolicy/device/disk-set catalog reads; policy validation | Yes | Read-only configuration-as-code catalogs; validation is non-persistent | Not applicable | Admin adapter tests pass; selected-backend S3 scenarios still absent | Partial backend Admin API implemented | `/admin/**` is separate from S3 coverage. `mvn -B -pl admin-api-adapter -am test` passes 9 tests. Policy/device/disk-set catalogs are read-only at runtime; create/update/delete policy requests are rejected. |

### AWS CLI Cucumber vs WebTestClient Cucumber Parity

| Dimension | Single-node WebTestClient requirements | Phase 2 WebTestClient specs | AWS CLI Cucumber |
|---|---|---|---|
| Scenarios/examples | 260 discovered: 234 passed, 26 skipped/excluded | 11 total: 7 passed, 4 `@awscli` examples excluded/skipped | Single-node: 72 discovered, 26 passed, 46 skipped/excluded; Phase 2 storage-engine: 11 discovered, 4 passed, 7 skipped/excluded; Phase 5: 33 discovered, 14 passed, 19 skipped/excluded |
| Tag / runner | `@webclient-required` via `SingleNodeBackendWebTestClientRequirementsCucumberTest` | Isolated Phase 2 runner | `@awscli-required` via dedicated AWS CLI runners |
| Driver | Spring WebTestClient | Spring WebTestClient | `aws s3api` CLI |
| Status | Passing for migrated ADR 0021 single-node backend scope | Implemented-and-validated for WebTestClient-required Phase 2 scope, including interrupted-write fault injection, corruption detection, recovery scanning, and concurrency | Selected AWS CLI subsets pass for single-node backend, Phase 1 upload, Phase 2 storage-engine reliability, and Phase 5 semantic compatibility; parity remains scoped by scenario tags and runner filters |
| Parity goal | Shared requirement text remains canonical | Maintain Phase 2 coverage and keep shared requirement text as source of truth | Continue using shared features with runner-specific glue/config/tags rather than duplicating requirements |

---

## Route-Level S3 Operation Coverage

> ⚠️ The table below documents which S3 operations have routes mapped and appear in `test-aws-cli.sh`.
> **A ✅ here means the route exists and the CLI script invokes it — it does NOT mean the operation is semantically implemented.**
> Most operations failed during the 2026-06-05 standalone run because the server was not running (exit code 254 = connection refused).

### Route and Script Coverage (formerly: "Implemented S3 Operation Coverage")

> Renamed to reflect actual meaning: **route mapped + script invoked ≠ semantically implemented.**

## Route and Script Coverage Table

| Operation | CLI command | Covered by AWS CLI script |
|---|---|---|
| ListBuckets | `aws s3api list-buckets` | ✅ |
| CreateBucket | `aws s3api create-bucket` | ✅ |
| HeadBucket | `aws s3api head-bucket` | ✅ |
| PutObject | `aws s3api put-object` | ✅ |
| HeadObject | `aws s3api head-object` | ✅ |
| GetObject | `aws s3api get-object` | ✅ |
| GetObjectAcl | `aws s3api get-object-acl` | ✅ |
| PutObjectAcl | `aws s3api put-object-acl` | ✅ |
| GetObjectTagging | `aws s3api get-object-tagging` | ✅ |
| PutObjectTagging | `aws s3api put-object-tagging` | ✅ |
| DeleteObjectTagging | `aws s3api delete-object-tagging` | ✅ |
| GetObjectAttributes | `aws s3api get-object-attributes` | ✅ |
| ListObjects | `aws s3api list-objects` | ✅ |
| ListObjectsV2 | `aws s3api list-objects-v2` | ✅ |
| GetBucketLocation | `aws s3api get-bucket-location` | ✅ |
| GetBucketAcl | `aws s3api get-bucket-acl` | ✅ |
| PutBucketAcl | `aws s3api put-bucket-acl` | ✅ |
| GetBucketTagging | `aws s3api get-bucket-tagging` | ✅ |
| PutBucketTagging | `aws s3api put-bucket-tagging` | ✅ |
| DeleteBucketTagging | `aws s3api delete-bucket-tagging` | ✅ |
| GetBucketVersioning | `aws s3api get-bucket-versioning` | ✅ |
| PutBucketVersioning | `aws s3api put-bucket-versioning` | ✅ |
| ListObjectVersions | `aws s3api list-object-versions` | ✅ |
| CopyObject | `aws s3api copy-object` | ✅ |
| PutObject PARANOIC_MODE | `aws s3api put-object --storage-class PARANOIC_MODE` | ✅ |
| HeadObject PARANOIC_MODE | `aws s3api head-object` | ✅ |
| GetObjectAttributes PARANOIC_MODE | `aws s3api get-object-attributes` | ✅ |
| DeleteObject PARANOIC_MODE | `aws s3api delete-object` | ✅ |
| PutObject SSE-S3 AES256 |  | ✅ |
| HeadObject SSE-S3 AES256 |  | ✅ |
| DeleteObject SSE-S3 |  | ✅ |
| PutObject STANDARD storage class |  | ✅ |
| HeadObject STANDARD |  | ✅ |
| GetObjectAttributes STANDARD |  | ✅ |
| DeleteObject STANDARD |  | ✅ |
| DeleteObject | `aws s3api delete-object` | ✅ |
| DeleteObjects | `aws s3api delete-objects` | ✅ |
| PutBucketCors | `aws s3api put-bucket-cors` | ✅ |
| GetBucketCors | `aws s3api get-bucket-cors` | ✅ |
| DeleteBucketCors | `aws s3api delete-bucket-cors` | ✅ |
| PutBucketLifecycleConfiguration | `aws s3api put-bucket-lifecycle-configuration` | ✅ |
| GetBucketLifecycleConfiguration | `aws s3api get-bucket-lifecycle-configuration` | ✅ |
| DeleteBucketLifecycleConfiguration | `aws s3api delete-bucket-lifecycle-configuration` | ✅ |
| PutBucketPolicy | `aws s3api put-bucket-policy` | ✅ |
| GetBucketPolicy | `aws s3api get-bucket-policy` | ✅ |
| DeleteBucketPolicy | `aws s3api delete-bucket-policy` | ✅ |
| PutBucketEncryption | `aws s3api put-bucket-encryption` | ✅ |
| GetBucketEncryption | `aws s3api get-bucket-encryption` | ✅ |
| DeleteBucketEncryption | `aws s3api delete-bucket-encryption` | ✅ |
| PutBucketLogging | `aws s3api put-bucket-logging` | ✅ |
| GetBucketLogging | `aws s3api get-bucket-logging` | ✅ |
| DeleteBucketLogging | `aws s3api delete-bucket-logging` | ✅ |
| PutBucketWebsite | `aws s3api put-bucket-website` | ✅ |
| GetBucketWebsite | `aws s3api get-bucket-website` | ✅ |
| DeleteBucketWebsite | `aws s3api delete-bucket-website` | ✅ |
| PutBucketNotification | `aws s3api put-bucket-notification-configuration` | ✅ |
| GetBucketNotification | `aws s3api get-bucket-notification-configuration` | ✅ |
| PutBucketReplication | `aws s3api put-bucket-replication` | ✅ |
| GetBucketReplication | `aws s3api get-bucket-replication` | ✅ |
| DeleteBucketReplication | `aws s3api delete-bucket-replication` | ✅ |
| PutBucketRequestPayment | `aws s3api put-bucket-request-payment` | ✅ |
| GetBucketRequestPayment | `aws s3api get-bucket-request-payment` | ✅ |
| PutBucketOwnershipControls | `aws s3api put-bucket-ownership-controls` | ✅ |
| GetBucketOwnershipControls | `aws s3api get-bucket-ownership-controls` | ✅ |
| DeleteBucketOwnershipControls | `aws s3api delete-bucket-ownership-controls` | ✅ |
| PutPublicAccessBlock | `aws s3api put-public-access-block` | ✅ |
| GetPublicAccessBlock | `aws s3api get-public-access-block` | ✅ |
| DeletePublicAccessBlock | `aws s3api delete-public-access-block` | ✅ |
| PutBucketAccelerateConfiguration | `aws s3api put-bucket-accelerate-configuration` | ✅ |
| GetBucketAccelerateConfiguration | `aws s3api get-bucket-accelerate-configuration` | ✅ |
| PutBucketAnalyticsConfiguration |  | ✅ |
| GetBucketAnalyticsConfiguration |  | ✅ |
| DeleteBucketAnalyticsConfiguration |  | ✅ |
| ListBucketAnalyticsConfigurations |  | ✅ |
| PutBucketInventoryConfiguration |  | ✅ |
| GetBucketInventoryConfiguration |  | ✅ |
| DeleteBucketInventoryConfiguration |  | ✅ |
| ListBucketInventoryConfigurations |  | ✅ |
| DeleteBucket | `aws s3api delete-bucket` | ✅ |

### Failure Tests

| Check | Status | Notes |
|---|---|---|
| GetBucketCors nonexistent | ✅ | Expected failure |
| GetBucketCors after delete | ✅ | Expected failure |
| GetBucketLifecycleConfiguration nonexistent | ✅ | Expected failure |
| GetBucketLifecycleConfiguration after delete | ✅ | Expected failure |
| GetBucketPolicy nonexistent | ✅ | Expected failure |
| GetBucketPolicy after delete | ✅ | Expected failure |
| GetBucketEncryption nonexistent | ✅ | Expected failure |
| GetBucketEncryption after delete | ✅ | Expected failure |
| GetBucketLogging nonexistent | ✅ | Expected failure |
| GetBucketLogging after delete | ✅ | Expected failure |
| GetBucketAnalyticsConfiguration nonexistent bucket | ✅ | Expected failure |
| GetBucketAnalyticsConfiguration missing id | ✅ | Expected failure |
| DeleteBucketAnalyticsConfiguration nonexistent bucket | ✅ | Expected failure |
| DeleteBucketAnalyticsConfiguration missing id | ✅ | Expected failure |
| PutBucketAnalyticsConfiguration nonexistent bucket | ✅ | Expected failure |
| ListBucketAnalyticsConfigurations nonexistent bucket | ✅ | Expected failure |
| GetBucketInventoryConfiguration nonexistent bucket | ✅ | Expected failure |
| GetBucketInventoryConfiguration missing id | ✅ | Expected failure |
| DeleteBucketInventoryConfiguration nonexistent bucket | ✅ | Expected failure |
| DeleteBucketInventoryConfiguration missing id | ✅ | Expected failure |
| PutBucketInventoryConfiguration nonexistent bucket | ✅ | Expected failure |
| ListBucketInventoryConfigurations nonexistent bucket | ✅ | Expected failure |

| Check | Status | Notes |
|---|---|---|
| GetObject nonexistent | ✅ | Expected failure |
| HeadObject nonexistent | ✅ | Expected failure |
| GetBucketLocation nonexistent | ✅ | Expected failure |
| GetBucketVersioning nonexistent | ✅ | Expected failure |
| GetBucketAcl nonexistent | ✅ | Expected failure |
| GetBucketTagging nonexistent | ✅ | Expected failure |
| CopyObject nonexistent source | ✅ | Expected failure |
| PutObject nonexistent bucket | ✅ | Expected failure |
| GetObjectAcl nonexistent | ✅ | Expected failure |

### Cucumber AWS CLI Test Status

| Category | Status | Note |
|---|---|---|
| Single-node `@webclient-required` scenarios | ✅ 260 discovered, 234 passed, 26 skipped/excluded | Migrated ADR 0021 single-node backend requirements pass via WebTestClient. |
| Phase 2 WebTestClient specs | ✅ 11 total, 7 passed, 4 `@awscli` examples excluded/skipped | REQ-FS-001 through REQ-FS-006 WebTestClient-required scope passes, including interrupted-write fault injection, corruption detection, recovery scanning, and concurrency scenarios. |
| Phase 2 storage-engine AWS CLI specs | ✅ 11 discovered, 4 passed, 7 skipped/excluded | Dedicated Phase 2 AWS CLI validation passes for its `@awscli-required` examples: REQ-FS-003, REQ-FS-004, REQ-FS-006 different keys, and REQ-FS-006 same key. |
| `@awscli-required` scenarios | ✅ Dedicated AWS CLI runners green for selected subsets | Single-node backend (72 discovered / 26 passed / 46 skipped), Phase 1 upload (28 / 9 / 19), Phase 2 filesystem reliability (11 / 4 / 7), and Phase 5 semantic compatibility (25 / 12 / 13) pass their selected AWS CLI subsets in the latest full Maven gate. |
| `test-aws-cli.sh` standalone | ⚠️ 46/82 passed | Independent script; most failures are connection errors (server not started at test time). |

## Roadmap

Operations not yet semantically implemented are tracked in `PLAN.md` under the *S3 API Semantic Completion Plan* (S3-P0 through S3-P4).
