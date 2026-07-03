# Magrathea ObjectStore — Market Positioning

> **Status:** `@partial` — initial version published 2026-07-03. Competitive differentiators are named; supporting benchmarks and conformance matrices are tracked under KA-2/KA-6 and remain pending.
>
> **License:** MIT — see [`LICENSE`](../LICENSE) and [`docs/adr/0019-adopt-the-mit-license.md`](../docs/adr/0019-adopt-the-mit-license.md).

## Target Audiences

| Segment | Priority | Key needs |
|---|---|---|
| Enterprise | Primary | Durable, performant, fully manageable S3 store with admin panel, observability, and support for tiering/replication |
| Single user / small deployment | Primary | Self-contained, air-gapped installable, easy to run, MIT-licensed, no external dependencies |
| Government / military | Co-primary | Air-gapped operation, Kerberos/SPNEGO authentication, FIPS-style constraints, PKI suite integration, self-contained binary, protocol gateway (SMB, WebDAV) for legacy applications |

---

## Competitive Landscape

### MinIO (AGPL → proprietary dual-license)

| Dimension | MinIO | Magrathea |
|---|---|---|
| License | AGPL / proprietary | **MIT** — no adoption friction, no viral copyleft concerns |
| Admin console | Community console feature-gated / removed; enterprise console requires subscription | **Complete admin panel in scope (EP-7)** — full dashboard, storage policy/device/disk-set management, recovery/GC reports, observability views. Not an alternate object API (INV-3). |
| Policy model | JSON / bucket-policy based | **YAML policy-driven storage classes** — `StoragePolicy` / `EffectiveStoragePolicy` with deterministic chunk/dedup/EC/compression/encryption plans; configuration-as-code YAML catalogs |
| Protocol gateways | Gateway mode (S3 → other S3); no WebDAV/SMB | **WebDAV adapter (EP-9) + SMB/VFS gateway (EP-11)** — legacy application compatibility for government/military |
| Requirements as compliance | — | **Executable Gherkin requirements as living compliance evidence** — each requirement scenario carries status tags, validation modes, and observable outcomes; ARC42 appendix generated from shared features |
| Single binary | Yes (Go) | **Planned (KA-5)** — GraalVM native-image / jlink+CDS evaluation; air-gapped offline install |
| S3 conformance | Market leader | **Planned (KA-2)** — s3-tests gate + SDK matrix; honest known-failure reporting |

### Ceph RGW (LGPL)

| Dimension | Ceph RGW | Magrathea |
|---|---|---|
| Architecture | Multi-service, multi-daemon; requires Ceph cluster (MON/OSD/MGR/RGW) | **Single-process Spring Boot WebFlux application** — two backends (in-memory / storage-engine filesystem), selectable at runtime |
| Complexity | High — requires Ceph cluster expertise, heavyweight deployment | **Low** — one JAR, one config, no external daemons; YAML-based configuration-as-code |
| Admin surface | CLI (`radosgw-admin`) + limited dashboard | **Full admin API + planned Vue panel (EP-7)** — REST JSON API for policies/devices/disk-sets/backend-status |
| Protocol gateways | NFS Ganesha (NFS→RGW) | **WebDAV + SMB gateway planned** — broader legacy protocol coverage |
| License | LGPL / proprietary | MIT |
| S3 conformance | High | Planned (KA-2) |

### Garage (AGPL)

| Dimension | Garage | Magrathea |
|---|---|---|
| Architecture | Rust, single-binary, multi-node cluster by design | **Java/Spring WebFlux** — multi-node planned (EP-10), but cluster is an evolution not the starting assumption |
| License | AGPL | MIT |
| Admin UI | Web dashboard included | Planned Vue panel (EP-7) |
| Protocol support | S3 + WebDAV | S3 + planned WebDAV + planned SMB |
| Single binary | Yes (Rust) | Planned (KA-5) |
| S3 conformance | High (tested against s3-tests) | Planned (KA-2) |

### SeaweedFS (MIT)

| Dimension | SeaweedFS | Magrathea |
|---|---|---|
| License | MIT | MIT |
| Architecture | Go, multi-volume server; FUSE mount, S3 API via Filer | **Java/Spring WebFlux** — no FUSE; S3-native; planned WebDAV + SMB for legacy access |
| Admin UI | Web UI via Filer | Planned Vue panel (EP-7) |
| S3 conformance | Moderate | Planned (KA-2) |
| Ecosystem | S3 + FUSE + Cloud Drive (HDFS-style) | S3-native; legacy protocol bridges via WebDAV/SMB |

---

## Magrathea Differentiators

1. **YAML policy-driven storage classes** — `StoragePolicy` as a first-class domain concept with deterministic `StepPlan` (DEDUP→COMPRESS→CRYPT→ERASURE_CODING→REPLICATION→STORE). Configuration-as-code YAML catalogs for policies, devices, disk sets/topology. No JSON bucket-policy-only lock-in.

2. **Complete admin panel** — Backend read-only catalog APIs delivered; full Vue UI planned (EP-7). Not an alternate object API (AGENTS.md B.3). Contrasts with MinIO's community console feature-gating.

3. **WebDAV + SMB gateway combination** — Two optional protocol adapters for legacy application compatibility (government/military target). WebDAV planned as EP-9; SMB/VFS as EP-11. Both delegate to the same reactive S3 services (INV-4).

4. **Executable Gherkin requirements as living compliance evidence** — Every requirement is a `.feature` file with status tags, validation modes (WebTestClient + AWS CLI), and observable outcomes. The ARC42 appendix is generated from shared features. This creates auditable, reproducible compliance documentation — valuable for enterprise procurement and government certifications.

5. **Self-contained by default, Maven-executable (INV-5)** — Every integration ships a simplified built-in backend. No external Kafka, Prometheus, LDAP, or KVM is required to demonstrate capability. Air-gapped offline installation is a hard requirement for government/military.

6. **MIT license** — No AGPL adoption friction. No dual-license trap. Free for any use, including commercial redistribution and closed-source integration.

---

## Government / Military Positioning

| Requirement | Approach |
|---|---|
| Air-gapped operation | Single-binary distribution (KA-5); Maven-executable gates (INV-5); no mandatory network calls at runtime |
| Kerberos / SPNEGO authentication | Identity federation (KA-4): OIDC + LDAP/AD + Kerberos/SPNEGO. PKINIT ties into the Magrathea PKI suite project |
| FIPS-style constraints | FIPS-compatible crypto provider evaluation in EP-1/KA-4 ADRs |
| Legacy protocol access (SMB/CIFS) | Samba VFS module (EP-11) — optional, maps file ops to S3 semantics |
| PKI integration | Certificate-provisioning port in identity-access BC; local-CA fallback per INV-5; real PKI is the separate Magrathea PKI suite |
| Compliance documentation | Executable Gherkin requirements as auditable compliance evidence |

---

## Open Source / Community Positioning

- **MIT license** — maximum adoption surface; suitable for embedding, redistribution, and closed-source use
- **Java/Spring ecosystem** — large developer pool; enterprise infrastructure teams already familiar with the stack
- **Single-binary target (KA-5)** — developer-friendly one-jar deployment; no external daemons
- **Reactive-first (INV-1)** — bounded memory, non-blocking I/O; suitable for high-throughput workloads on modest hardware
- **YAML configuration-as-code** — fits DevOps/GitOps workflows; no database setup required

---

## Roadmap Reference

See [`docs/roadmap.md`](roadmap.md) for the public phased roadmap derived from [`PLAN.md`](../PLAN.md).
