# ADR 0026 — Magrathea Product Shell and Product Extension contracts

Date: 2026-07-12

## Status

Accepted — implemented and semantically validated for the EP-7 scope defined by `REQ-ADMIN-001` through `REQ-ADMIN-031`.

This status covers the Product Shell and Product Extension contracts, the Object Storage Admin Application behavior described by those requirements, and the Admin Control Plane contracts exercised by the EP-7 tests. It does not assert that credential or tenant administration exists. It also does not assert that real recovery, garbage-collection, scrub, audit, metrics, or traces report providers exist: their implemented contract intentionally returns HTTP 503 with `report-provider-not-configured` and availability `not-configured` when no provider is configured.

## Context

EP-7 requires an admin user experience without turning product-specific frontend code into a reusable platform by accident. A product-neutral shell is useful across Magrathea products, but Object Storage concepts and API integrations are not product-neutral.

The former embryonic `magrathea-ui` mixed shell layout, localization, routing, and documentation presentation with Object Storage dashboard cards, routes, screens, and API calls. The late EP-7 implementation replaces that mixed structure with a workspace containing a Product Shell package, independently registered product extensions, deployable product applications, a product template, and explicit build manifests.

The Object Storage Admin Application now presents Admin Control Plane evidence for liveness/readiness, backend and catalog status, read-only configuration catalogs, non-persistent policy validation, storage devices, disk-set topology, capacity/quota accounting, and operational-report availability. Its S3 HeadObject diagnostic uses a separately configured S3 Data Plane client boundary rather than an Admin or storage-engine object route.

The Admin Control Plane exposes administrative contracts but remains intentionally narrower than the planned operational ambitions sometimes associated with EP-7. The repository has no demonstrated credential-administration or tenant-administration capability. Operational-report routes are availability contracts, not evidence that recovery, garbage collection, scrub, audit, metrics, or tracing providers have been implemented.

## Decision

Adopt and retain a reusable, product-neutral **Magrathea Product Shell** with explicit **Product Extension** contracts.

The Magrathea Product Shell owns only:

- the product-neutral application frame, navigation, and layout;
- shared semantic **Design Tokens** and product-neutral theming contracts;
- reusable **Shell Primitives** for shell-level interaction and presentation;
- localization infrastructure and product-neutral shell messages;
- route and Product Extension composition;
- documentation integration;
- common loading, empty, unavailable, unauthorized, error, offline, and not-found states.

A Product Extension contributes product-owned navigation entries, routes, screens, permissions, localization resources, and integrations through explicit registration contracts. Registration and composition remain separate from extension implementation. Extension failure is isolated so that the shell and healthy extensions remain usable.

The **Object Storage Admin Application** is a Product Extension. It owns every Object Storage domain screen and Object Storage API client, including the implemented screens and clients for health/readiness, backend status, storage policies and validation, storage devices, disk sets/topology, capacity accounting, operational-report availability, observability availability, and the permitted S3 diagnostic. Object Storage models, decisions, endpoint knowledge, request/response mapping, and orchestration remain outside the Product Shell.

Object Storage domain and API logic are prohibited in the Product Shell. Product-specific cards, routes, labels, permissions, API paths, clients, response models, and localization enter through the Object Storage Product Extension rather than becoming shell defaults.

The **Admin Control Plane** must not become an alternate object API. Object and bucket semantics—including creating, listing, reading, writing, deleting, tagging, versioning, ACL, metadata, and multipart behavior—remain exclusively on the **S3 Data Plane**. Admin Control Plane capabilities remain limited to administrative concerns without an S3 API equivalent, such as read-only configuration catalogs, non-persistent validation, backend status, capacity/quota administration, and truthful operational-report availability.

An admin-side S3 diagnostic must call the S3 Data Plane as an ordinary S3 client. It must not bypass that plane through the Admin Control Plane, a storage-engine port, or a private storage-engine route. The validated diagnostic contract does not constitute credential-profile administration or tenant administration.

Operational-report routes must report actual provider evidence. When no real provider is configured, recovery, garbage-collection, scrub, audit, metrics, and traces routes return HTTP 503 with `report-provider-not-configured` and `availability: not-configured`; the UI presents that state without generated records, inferred findings, sample charts, or healthy defaults.

Frontend distributions are composed from one Product Shell artifact and product-specific manifests. Deterministic removal of an extension must remove its routes, navigation, permissions, localization, and screen without changing Product Shell source. Docker remains the canonical frontend and documentation packaging-validation path.

## Consequences

- The Product Shell can be reused without importing Object Storage concepts or endpoint knowledge.
- The Object Storage Admin Application owns its routes, screens, clients, permissions, and localization behind an explicit extension registration.
- A minimal `magrathea-example` product demonstrates reuse independently of the Object Storage extension.
- Extension removal and extension failure have explicit, validated lifecycle behavior.
- Design-token, shell-state, localization, responsive-navigation, keyboard, and accessibility behavior are shared without making Object Storage semantics shell defaults.
- Read-only catalog and non-persistent validation behavior remain visibly configuration-as-code; the UI does not imply unsupported mutation.
- Admin-side S3 diagnostics retain S3 authentication, authorization, protocol, and availability constraints and do not create a privileged object path.
- Operational-report panels may legitimately remain unavailable. An implemented 503 not-configured contract is not an implemented recovery, GC, scrub, audit, metrics, or traces provider.
- Credential and tenant administration remain outside the evidenced implementation and must not be inferred from EP-7 requirement status.
- Product and documentation assets are regenerated through a reproducible Docker-driven path rather than committed or host-generated frontend output.

## Alternatives Considered

### Keep one Object Storage-specific frontend

Rejected because it couples shell concerns to Object Storage routes, domain cards, and API clients and prevents demonstrated reuse by another product.

### Put shared Object Storage API clients in the shell

Rejected because API reuse does not make domain or endpoint knowledge product-neutral. Object Storage clients belong to the Object Storage Product Extension.

### Expose object operations through the Admin Control Plane

Rejected because it creates an alternate object API, bypasses S3-compatible behavior and validation, and violates the S3 Data Plane boundary. Admin-side S3 diagnostics use S3 as a client.

### Let extensions modify shell internals directly

Rejected because implicit coupling makes ownership unclear and prevents deterministic composition and removal. Product Extension contracts are explicit.

### Return generated operational data when providers are absent

Rejected because samples, filesystem inference, generated metrics, and healthy defaults would be presented as runtime evidence. The accepted contract fails truthfully with a typed unavailable response.

## Evidence

Late EP-7 repository and validation evidence observed on 2026-07-12:

- `s3-reactive-api-adapter/src/test/features/requirements/phase-ep7-admin-panel.feature` and `s3-reactive-api-adapter/src/test/features/specs/phase-ep7-product-shell.feature` classify `REQ-ADMIN-001` through `REQ-ADMIN-031` as `@implemented-and-validated`.
- `magrathea-ui/packages/product-shell`, `magrathea-ui/packages/object-storage-extension`, and `magrathea-ui/packages/magrathea-example` establish separate shell and extension source boundaries; `magrathea-ui/apps` and `magrathea-ui/templates` provide deployable compositions and a reusable product template.
- `cd magrathea-ui && npm test` passed 14 Vitest files and **72/72 tests**, covering shell contracts, static boundaries, extension composition, application behavior, browser adapters, themes, templates, and accessibility components.
- The Playwright/axe acceptance suite passed **39/39 tests**: 13 browser scenarios in each configured Chromium viewport at **360**, **768**, and **1440** pixels. The suite covers the EP-7 admin UI, keyboard and responsive behavior, direct navigation/history, truthful unavailable states, S3 diagnostic boundaries, and real-browser axe checks.
- `magrathea-ui/scripts/validate-extension-removal.mjs` passed deterministic extension-removal validation: removing `magrathea-example` removes its navigation, route, permission, localization, and screen while the Product Shell source remains unchanged.
- `magrathea-ui/scripts/build-products.mjs` reproducibly builds isolated `object-storage` and `magrathea-example` distributions from one Product Shell artifact; both manifests identify the same shell version and content digest while retaining only their registered product routes and localization.
- `scripts/validate-frontend-packaging.sh` builds both product distributions and documentation assets twice and compares path-sorted SHA-256 inventories. The canonical `docker build --network=host --target frontend-packaging-validation -t magrathea-frontend-packaging-validation:local .` validation passed.
- `admin-api-adapter/src/main/java/com/example/magrathea/admin/web/AdminRouter.java` and its EP-7 application/infrastructure ports implement backend-status, read-only catalog, non-persistent validation, capacity/quota, route-inventory, and operational-report availability contracts without adding an Admin object API.
- The focused Maven EP-7 Admin API Cucumber run passed. `PhaseEp7AdminApiRequirementsCucumberTest` executed **18 scenarios and 132 steps** for `REQ-ADMIN-023` through `REQ-ADMIN-031` with no failures; the Surefire report records 35 discovered scenarios, 17 intentionally excluded by the Admin API tag filter, and zero failures or errors.
- The operational-report scenarios validate six HTTP 503 responses with `report-provider-not-configured` and `availability: not-configured`. They validate truthful absence of providers; they are not evidence of real recovery, garbage-collection, scrub, audit, metrics, or traces implementations.
- The focused frontend, Admin API, deterministic extension-removal, reproducible dual-product packaging, and canonical Docker packaging-validation gates passed for the declared EP-7 requirement scope.

## Related Requirements

- `REQ-ADMIN-001`–`REQ-ADMIN-013`: Object Storage Admin Application behavior and browser accessibility/responsiveness.
- `REQ-ADMIN-014`–`REQ-ADMIN-022`: Product Shell, Product Extension, design-system, localization, resilience, and deterministic packaging contracts.
- `REQ-ADMIN-023`–`REQ-ADMIN-031`: Admin Control Plane semantic contracts and route boundary.
- Repository invariant: the Admin Control Plane is not an alternate object API; object and bucket semantics remain on S3-compatible endpoints.
