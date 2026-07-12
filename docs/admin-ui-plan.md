# EP-7 Admin UI — Implemented Scope and Remaining Backlog

> **Status (2026-07-12):** `REQ-ADMIN-001` through `REQ-ADMIN-031` are implemented and validated. The Product Shell, Product Extension contract, Object Storage Admin Application, tested Admin Control Plane contracts, and deterministic packaging are delivered for that declared scope. **EP-7 overall remains `@partial`** because the authoritative `PLAN.md` also requires credential/tenant administration and real operational report providers that are not present.

## Delivered architecture

### Magrathea Product Shell

The reusable `@magrathea/product-shell` package owns only product-neutral concerns:

- application frame, responsive and keyboard-operable navigation, layout, and accessibility foundations;
- semantic Design Tokens and reusable Shell Primitives;
- localization infrastructure and product-neutral defaults;
- explicit Product Extension composition and failure isolation;
- common loading, empty, unavailable, unauthorized, error, offline, and not-found states.

Static boundary tests enforce that Object Storage routes, endpoints, models, clients, labels, and domain logic do not enter the shell.

### Product Extension contract

A Product Extension explicitly contributes navigation, routes, screens, localization, permissions, and integrations. The Object Storage implementation lives in `magrathea-ui/packages/object-storage-extension`; deployable compositions live under `magrathea-ui/apps`. The independent `magrathea-example` product and reusable template demonstrate that products compose from the same shell rather than copying shell source.

Removing an extension deterministically removes its navigation, route, permission, localization, and screen without changing Product Shell source. Extension failure is isolated from the shell and healthy extensions.

### Object Storage Admin Application

The implemented Object Storage application presents:

| Area | Implemented behavior and boundary |
|---|---|
| Dashboard | Distinguishes liveness, readiness, unavailable catalogs, selected backend, and catalog sources. |
| Storage policies | Read-only configuration-as-code list/detail including `MINIO_STANDARD`; proposed policy validation is structured and non-persistent; mutation remains unavailable. |
| Storage devices and disk sets | Read-only capacity, health, eligibility, membership, and topology views. |
| Capacity/quota | Displays the existing bucket accounting contract; it does not expose object access. |
| Operational reports | Presents provider availability truthfully. With no configured provider, recovery, garbage collection, scrub, audit, metrics, and traces return and display `503 report-provider-not-configured` / `availability: not-configured`. |
| S3 diagnostic | Uses a separately configured S3 Data Plane client for HeadObject; no Admin or storage-engine object route exists. |
| Navigation and accessibility | Direct-link/history behavior, keyboard navigation, responsive operation, and axe checks are validated at 360, 768, and 1440 pixels. |

## Admin Control Plane and S3 Data Plane boundary

The Admin Control Plane exposes only administrative capabilities with no S3 equivalent: health/readiness, backend and catalog status, read-only catalogs, non-persistent validation, capacity/quota accounting, route inventory, and truthful report-provider availability.

It does **not** expose object or bucket creation, listing, reading, writing, deletion, multipart operations, metadata, tagging, ACL, or versioning. Those semantics remain exclusively on the S3 Data Plane. The diagnostic HeadObject request uses S3 as an ordinary client and receives no privileged storage-engine bypass.

## Validation evidence

- All `REQ-ADMIN-001` through `REQ-ADMIN-031` scenarios are tagged `@implemented-and-validated` in the shared EP-7 requirement/spec files.
- Vitest passed **72/72 tests** across 14 files.
- Playwright/axe passed **39/39 tests**: 13 scenarios at each Chromium viewport width of **360**, **768**, and **1440** pixels.
- Deterministic extension-removal validation passed.
- Dual-product packaging reproducibly built isolated `object-storage` and `magrathea-example` distributions from one Product Shell artifact.
- The canonical Docker target `frontend-packaging-validation` passed its build-twice, path-sorted SHA-256 comparison for both products and documentation assets.
- `PhaseEp7AdminApiRequirementsCucumberTest` passed **18 executed scenarios / 132 steps** for `REQ-ADMIN-023` through `REQ-ADMIN-031`; its Surefire report records 35 discovered scenarios, 17 tag-filtered, and no failures or errors.
- The focused Maven gate passed.

## Explicitly not delivered

The following authoritative EP-7 backlog is not implemented by the validated requirement slice:

- credential administration UI or Admin API;
- tenant administration UI or Admin API;
- real recovery, garbage-collection, scrub, audit, metrics, or traces report providers.

The implemented unavailable-provider contract is deliberate and truthful, but it is not equivalent to a provider that returns real operational records. EP-1 backing services and EP-4 mechanisms do not automatically constitute EP-7 Admin report-provider integrations.

## Completion decision

The `REQ-ADMIN-001..031` bounded scope is complete and validated. EP-7 **Complete Admin Panel** remains `@partial` under the authoritative plan until the explicitly planned credential/tenant administration and real operational report-provider integrations are either implemented and semantically validated or formally removed from EP-7 scope by an approved plan/ADR change.
