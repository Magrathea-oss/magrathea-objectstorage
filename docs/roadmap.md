# Magrathea ObjectStore — Public Roadmap

> **Status:** Derived from [`PLAN.md`](../PLAN.md) (updated 2026-07-12). Completion claims are limited to the declared and validated scope; this roadmap is not a promise of delivery dates.

---

## Phase 1 — Foundation (Prototype Quality)

| Area | Status | Key deliverables |
|---|---|---|
| S3 API surface | ✅ Mapped (~95 routes) | `S3PathRouter`, handler classes, XML serialization |
| Object CRUD (Put/Get/Head/Delete) | ✅ Stateful + WebTestClient-validated | Read-after-write, slash keys, metadata, ETag, CopyObject |
| Bucket lifecycle | ✅ Stateful + WebTestClient-validated | Create/Head/Delete/List, ListObjects V1/V2 |
| Multipart upload lifecycle | ✅ Stateful | Initiate/UploadPart/CompleteMultipartUpload/Abort — part bodies not persisted (see EP-3) |
| Upload durability & restart safety | ✅ `@implemented-and-validated` | REQ-UPLOAD-001/002/004/005/006 — object bytes, manifests, and object references survive restart |
| Filesystem reliability | ✅ `@implemented-and-validated` | Atomic publication, SHA-256 checksums, recovery scanner, SHA-256 sidecar/trailer checksums |
| Storage-engine backend | ✅ Wired for object bytes + manifests + references | Bucket registry, multipart state, and per-object config metadata remain in-memory (see EP-2) |
| Admin API (read-only catalogs) | ✅ Delivered | Storage policies, devices, disk sets — YAML-backed, read-only, non-persistent validation |
| MIT License | ✅ Delivered | [`LICENSE`](../LICENSE), ADR 0019 |

---

## Phase 2 — Enterprise Production Readiness (EP-0 .. EP-11)

| Phase | Priority | Status | Description |
|---|---|---|---|
| **EP-2** Complete Metadata Durability | Blocker | `@implemented-and-validated` | Declared storage-engine metadata families survive restart; handler-local configuration state moved behind durable repositories |
| **EP-3** Reactive Streaming Completion | Blocker | `@implemented-and-validated` | Bounded single-pass upload/read pipelines, multipart assembly, conditional chunking, whole-object units, and physical EC shards |
| **EP-1** Security & Identity | Blocker | `@implemented-and-validated` for built-in scope | SigV4, deny-by-default authorization, durable audit, SSE-S3, credentials, policy and key services |
| **EP-4** Space Management & Data Hygiene | High | `@implemented-and-validated` for single-node scope | Typed GC, dedup reachability, quotas, ENOSPC behavior, and transform-aware periodic scrubbing |
| **EP-5** Operability & Delivery | High | `@implemented-and-validated` for `0.1.0` preview | Full CI gate, SemVer/OCI release workflow, persistent-volume replacement, backup/DR, schema compatibility, SLOs, probes, and graceful shutdown |
| **EP-6** Performance & Capacity Validation | High | `@implemented-and-validated` for single-node envelope | Enforced object/admission/timeout/connection limits, deterministic 45-second CI load, 15-minute soak, bounded-memory and reproducible result manifests |
| **EP-7** Complete Admin Panel | High | `@partial` | Backend read-only catalogs done; full Vue UI (dashboard, policies, devices, disk sets, recovery/GC reports, observability views) — requires frontend workflow handoff |
| **EP-8** Cluster Architecture ADR & Supply Chain | Medium | `@absent` | ADR deciding inter-node transport, membership, consistency model, failure-domain topology; SBOM, CVE scanning, image hardening |
| **EP-10** S3 Cluster (Multi-Node) | High | `@absent` | Node membership, replica/EC-shard placement, quorum writes/reads, manifest replication, anti-entropy healing, rebalance |
| **EP-9** WebDAV API Adapter | Future | `@absent` | Optional WebDAV protocol adapter; delegates to S3 services (INV-4) |
| **EP-11** SMB Gateway (Samba VFS) | Future | `@absent` | Optional Samba VFS C module; maps file ops to S3 semantics (INV-4) |

**Enterprise readiness exit criteria:**

- [x] EP-1: SigV4 authentication, deny-by-default authorization, audit logging, and real SSE validated for built-in scope
- [x] EP-2: Declared metadata families survive restart in storage-engine mode
- [x] EP-3: GetObject and multipart paths stream with bounded memory
- [x] EP-4: Typed GC, dedup reachability, quotas, ENOSPC, and scrubbing validated for single-node scope
- [x] EP-5: `0.1.0` preview CI/delivery gates, backup/restore, probes, shutdown, and persistent-volume replacement validated
- [x] EP-6: Single-node load/soak limits documented and reproducibly validated under `-Xmx256m` (non-benchmark scope)
- [ ] EP-7: Complete admin panel delivered and validated
- [ ] EP-8: Cluster architecture ADR accepted; supply-chain gates wired
- [ ] EP-10: Cluster behavior validated by multi-node e2e scenarios
- [ ] EP-9 (optional): WebDAV adapter, if built, meets binding constraints
- [ ] EP-11 (optional): SMB gateway, if built, meets binding constraints

---

## Phase 3 — Killer-App Track (KA-1 .. KA-6)

| Phase | Start condition | Status | Description |
|---|---|---|---|
| **KA-1** Positioning & Licensing | Immediate | `@partial` ✅ LICENSE + ADR done; positioning & roadmap published (this document) |
| **KA-2** Ecosystem Conformance | After minimal EP-1 (SigV4) | `@absent` | s3-tests gate, SDK/tool matrix, full checksum matrix |
| **KA-3** Data-Lake Readiness & S3 Tables | After EP-1 + EP-3 | `@absent` | Conditional PUT, presigned URLs, Iceberg/Spark workloads; separate S3 Tables adapter + table-store BC |
| **KA-4** Eventing, Tiering, Multi-Site, Federation | After EP-1/EP-2/EP-4; multi-site after EP-10 | `@absent` | Notification delivery (built-in backend), lifecycle execution, cold-tier, cross-cluster replication; OIDC + LDAP + Kerberos federation |
| **KA-5** Distribution | After EP-5 (single-binary ADR may start earlier) | `@absent` | Single-binary (GraalVM/jlink), Grafana dashboards, CLI (very low priority), Helm (very low priority) |
| **KA-6** Public Proof | Last; after EP-6 + KA-2 | `@absent` | Reproducible benchmarks vs MinIO/Garage/SeaweedFS; published s3-tests pass-rate matrix |

**Killer-app exit criteria** (all unchecked):

- [ ] KA-1: MIT LICENSE + positioning published ✅ (LICENSE/ADR done; positioning published this document)
- [ ] KA-2: s3-tests pass-rate published with honest known-failures
- [ ] KA-3: Iceberg/Spark validated (conditional PUT + presigned URLs working)
- [ ] KA-3: S3 Tables adapter + table-store BC delivering namespaces/tables
- [ ] KA-4: Notification delivery (built-in backend) + lifecycle execution + multi-site validated
- [ ] KA-4: OIDC + LDAP + Kerberos federation validated incl. at least one legacy-path e2e
- [ ] KA-5: Single-binary distribution shipped and air-gap installable
- [ ] KA-6: Public reproducible benchmarks published

---

## How to Read This Roadmap

- **Status tags** follow the AGENTS.md convention: `@implemented-and-validated`, `@implemented-not-e2e-validated`, `@partial`, `@config-only`, `@placeholder`, `@absent`, `@not-implemented`.
- No capability is marked done until the production code implements promised behavior and semantic tests validate observable outcomes.
- Route counts, handler names, and README claims are not completion evidence — only passing requirement scenarios count.
- The authoritative evidence table lives in [`PLAN.md`](../PLAN.md) (Baseline Evidence section). This roadmap is a planning artifact, not a status report.
- EP phases must reach `@implemented-and-validated` before the corresponding enterprise readiness exit criteria are considered met.
- KA phases depend on EP prerequisites and never bypass INV-1..INV-5 or the evidence discipline.
