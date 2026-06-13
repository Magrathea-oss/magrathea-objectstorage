# AGENTS.md

These instructions apply to all future agents working in this repository. The reusable policy section is intended to be portable to other software repositories; the project-specific section overrides, narrows, or specializes it for Magrathea Object Storage.

## A. Reusable Agent Policy for Software Projects

### A.1 Language and source-of-truth rules

- Write repository instructions, requirement text, reports, and documentation in English unless a repository-specific section explicitly requires quoted domain text in another language.
- Treat executable requirements as product documentation, not as thin transport checks.
- Do not treat generated or runner-specific test scaffolding as the source of a requirement. The `.feature` file is the source of truth.
- Implementation, runner glue, and validation code must follow the requirement text, not the other way around.

### A.2 Requirement-first testing policy

- Before each implementation phase listed in the project plan, create or refresh the real requirement scenarios for that phase first.
- Gherkin scenarios must describe real requirements and business or technical intent.
- Requirement scenarios must be written or refreshed before implementation work for the corresponding phase begins.
- Avoid anemic request/status-code checks when writing production requirements.

### A.3 Evidence-based completion and requirement status policy

- **Completion claims require code plus semantic tests.** An API, feature, route family, or requirement may be described as done only when production code implements the promised behavior and semantic tests validate the observable outcomes.
- Route existence, handler names, route inventories, README text, generated API lists, status-code smoke tests, or Cucumber status-only checks are not evidence of semantic completion.
- If an API or feature is declared supported but the implementation is placeholder, configuration-only, partial, or unvalidated, downgrade the documentation, route inventory, API matrix, and requirement report to an explicit requirement/backlog status instead of calling it complete.
- Use these status tags for requirements and feature/API reports:
  - `@implemented-and-validated`: production behavior is implemented and semantic tests pass in all agreed validation modes for the declared scope.
  - `@implemented-not-e2e-validated`: production behavior exists, but agreed end-to-end or black-box validation is missing, incomplete, stale, or not yet run.
  - `@partial`: a meaningful subset exists, but scope gaps, semantic gaps, backend gaps, or validation gaps remain.
  - `@config-only`: configuration can be stored, returned, or validated, but enforcement, background jobs, side effects, or runtime behavior are not implemented.
  - `@placeholder`: route, handler, schema, or documentation scaffolding exists without meaningful semantic behavior.
  - `@absent`: no route, handler, implementation, or executable requirement exists for the declared capability.
  - `@not-implemented`: the capability is declared in scope or externally relevant, but the repository intentionally does not implement it yet or returns documented unsupported/not-implemented behavior.
- Do not mark a capability done until the behavior is implemented and validated by all agreed validation modes where applicable, for example an HTTP client runner and a CLI runner against shared requirement scenarios.

### A.4 Semantic Gherkin authoring and taxonomy

- Prefer semantic Gherkin over comments. The feature text should explain intent; comments should be rare and limited to conventions that cannot be expressed in Gherkin.
- Requirement features may use expressive Gherkin keywords and structures, including:
  - `Business Need`
  - `Ability`
  - `Feature`
  - `Rule`
  - `Scenario Outline`
  - `Examples`
  - data tables
  - doc strings
- Use `Business Need` for externally promised product behavior through an external API or user-facing interface.
- Use `Ability` for internal, operational, platform, or backend capabilities that support product needs.
- Use `Business Need` or `Ability` for administrator-only capabilities depending on whether they are user-visible admin product requirements or internal operational support requirements.
- Use `Feature` for protocol smoke checks, regression checks, or legacy technical checks that are not product-level compatibility promises.
- Each scenario should include a stable requirement identifier when it represents a production requirement.
- Scenarios must describe realistic preconditions, actions, validation mode, and observable outcomes.
- Use domain language for capabilities such as durability, integrity, restart safety, streaming, observability, backend behavior, and storage layout when those concepts are relevant to the project.

### A.5 Protocol-smoke separation

- Status-code-only checks are protocol smoke tests only.
- Tag status-code-only checks with `@protocol-smoke` and do not classify them as production requirements.
- `@protocol-smoke` scenarios and status-code-only checks cannot upgrade a requirement or feature status.
- Do not use `@protocol-smoke` scenarios as substitutes for functional or non-functional production requirements.

### A.6 Single shared feature with multiple runners and glue

- A `.feature` file is the single source of truth for a requirement.
- Do not duplicate the same feature text into separate runner folders.
- Multiple validation modes, such as an HTTP client runner, a CLI runner, restart validation, filesystem inspection, metadata inspection, or contract validation, must reuse the same shared feature resource whenever they validate the same requirement.
- Runner-specific differences belong in duplicated or adapted step definitions, glue code, runner configuration, tags, profiles, or validation adapters, not in duplicated requirement text.
- When multiple validation modes are required, keep the requirement scenario shared and express runner selection through validation mode, tags, profiles, runner configuration, or adapters.

### A.7 Functional and non-functional requirement classification

- Every production requirement scenario must be classified as functional, non-functional, or both when justified.
- Use tags for classification and quality attributes, for example:
  - `@functional-requirement`
  - `@non-functional-requirement`
  - `@durability`
  - `@restart-safety`
  - `@streaming`
  - `@integrity`
  - `@observability`
- A scenario may carry both `@functional-requirement` and `@non-functional-requirement` only when both classifications are explicit and justified by the scenario text.

### A.8 Fixture, path, header, and observable-outcome realism

Requirement scenarios must use realistic and reviewable details, including where relevant:

- fixture paths and fixture file names;
- object keys, resource identifiers, routes, or domain paths;
- storage roots, backend identifiers, profile names, tenant identifiers, or environment selectors;
- HTTP headers, metadata headers, content type, content length, checksums, entity tags, ranges, and conditional request headers;
- validation modes such as HTTP client runner, CLI runner, restart validation, filesystem inspection, metadata inspection, log inspection, metric inspection, or contract validation;
- observable outcomes such as persisted bytes, metadata, headers, errors, logs, metrics, restart behavior, and integrity checks.

Avoid placeholder paths and abstract examples when realistic fixtures, object names, resource identifiers, or domain examples would make the requirement clearer.

### A.9 Generated requirements appendix and reporting policy

Generated Gherkin requirements reporting should become an architecture documentation appendix when the repository maintains architecture documentation. The appendix should group scenarios by:

- requirement ID;
- functional and/or non-functional classification;
- capability area;
- rule;
- tags;
- validation mode;
- declared support status and validated implementation status.

Reports must not invent test results or implementation status. Use observed runner output, tracked implementation state, or clearly mark unknown or pending items.

### A.10 Definition of done for future tests

A future test or validation change is done only when:

- the corresponding shared `.feature` requirement exists or has been refreshed first;
- the scenario describes real behavior and observable outcomes, not only status codes;
- each production requirement scenario has functional and/or non-functional classification tags;
- smoke-only checks are tagged `@protocol-smoke`;
- realistic fixtures, paths, headers, backend details, validation modes, and expected outcomes are present where relevant;
- all agreed runners and validation modes reuse the shared feature text instead of duplicating it;
- runner-specific behavior is isolated to glue, step definitions, runner configuration, profiles, or validation adapters;
- generated requirements reporting can place the scenario in the architecture appendix with requirement ID, classification, capability area, rule, tags, validation mode, declared support status, and validated implementation status.

## B. Project-Specific Policy: Magrathea Object Storage

### B.1 Magrathea-specific requirement focus

- Keep requirement scenarios aligned with observable object storage behavior: fixture paths, object paths, storage roots, headers, backend selection, validation modes, and externally visible outcomes.
- Magrathea requirements must distinguish S3-compatible external API behavior, storage-engine internals, and administrator-only operational capabilities.
- If a Magrathea feature is declared supported but is placeholder or config-only, create or update a shared requirement feature classified as `Business Need` when it is part of S3 compatibility, including extended S3 APIs that are declared supported. Tag the scenarios with `@partial`, `@config-only`, `@placeholder`, or `@not-implemented` as appropriate.

### B.2 S3 compatibility taxonomy and Gherkin classification

- Use `Business Need` for externally promised S3 server behavior through S3-compatible APIs, including extended S3 APIs if they are declared supported.
- Use `Ability` for storage-engine internals and other internal or operational capabilities that support S3 needs.
- Use `Business Need` or `Ability` for admin-only capabilities depending on whether they are user-visible admin product requirements or internal operational support requirements.
- Admin-only storage-engine features should carry tags such as `@admin-api`, `@storage-policy`, `@storage-device`, `@disk-set`, and `@backend-status`; do not tag them as `@s3-api` unless they are validated through actual S3-compatible endpoints.
- Use `Feature` for protocol smoke checks, regression checks, or legacy technical checks that are not product-level S3 compatibility promises.
- S3 compatibility scenarios should include S3 domain details such as bucket names, object keys, metadata headers, ETags, ranges, conditional request headers, multipart state, ACL/tagging/versioning details, or documented unsupported behavior when relevant.

### B.3 Storage-engine external boundary

- The storage-engine must not expose a parallel external API for S3 object behaviors.
- If a behavior is part of S3 semantics, the external API must be S3-compatible endpoints; the storage-engine remains an internal implementation behind the object-store/storage-engine ACL.
- Storage-engine requirements are internal or operational `Ability` requirements unless they describe admin-only configuration or state that has no S3 API counterpart.
- External access to storage-engine capabilities exists only through the Admin panel/Admin API, and only for requirements with no S3 API equivalent, such as storage policy definitions, storage devices, disks/disk sets/topology, backend status, and admin validation/reporting.
- The Admin API/UI may expose storage policies, storage devices, disk sets/topology, backend status, and validation/reporting; it must not become an alternate object API.
- Do not invent public storage-engine endpoints for object upload, read, delete, list, multipart, metadata, tagging, ACL, bucket, or other S3 object semantics.

### B.4 Magrathea validation modes and profiles

- S3 client requirements must be modeled and validated through S3 APIs, using WebTestClient and AWS CLI runners against shared feature files where both validation modes are required.
- WebTestClient and AWS CLI validation must reuse the same shared feature resource; runner-specific behavior belongs in step definitions, glue code, runner configuration, Maven profiles, tags, or validation adapters.
- Do not mark an S3 compatibility capability done until the behavior is implemented and validated by both agreed validation modes where applicable, especially WebTestClient and AWS CLI for shared S3 compatibility scenarios.
- The storage-engine profile/property path is the canonical way to exercise the storage-engine backend end to end. Storage-engine-profile tests must prove externally visible S3 behavior through the selected backend rather than bypassing the S3 API for object semantics.
- Backend-selection scenarios should identify the selected backend, profile/property value, storage roots, YAML catalog inputs, and observable startup, failure, or request behavior.

### B.5 Admin API, storage policies, devices, disk sets, and backend status

- The Admin API may expose storage policy, storage device, disk set/topology, backend status, and validation/reporting capabilities because they have no S3 API equivalent.
- Admin API requirements for storage policies, storage devices, disk sets/topology, and backend status must state whether behavior is read-only catalog access, request-body validation, non-persistent validation, runtime status reporting, or future mutation support.
- Do not describe admin configuration routes as complete enforcement unless production code enforces the configured policy and semantic tests validate the runtime side effects.
- Use explicit status tags such as `@config-only`, `@partial`, `@implemented-not-e2e-validated`, or `@implemented-and-validated` for admin/storage-engine capabilities based on observed implementation and validation.

### B.6 Docker-driven documentation and static asset regeneration

- Generated bootstrap static resources and generated documentation assets must not be treated as source-of-truth inputs.
- Docker-driven documentation/frontend packaging is the canonical regeneration path for built documentation and UI/static assets.
- When generated requirements reports, ARC42/ADR exports, documentation JSON, or frontend documentation assets are affected, use or preserve deterministic Dockerfile-driven regeneration rather than relying on host-generated static resources.
- Do not commit generated/static assets unless the project plan or a specific task explicitly requires them and the generated/source boundary remains documented.

### B.7 Magrathea ARC42/Gherkin appendix specifics

- Generated Gherkin requirements reporting must become an ARC42 appendix for Magrathea.
- The ARC42 appendix should group scenarios by requirement ID, functional and/or non-functional classification, capability area, rule, tags, validation mode, declared support status, and validated implementation status.
- Magrathea reports must distinguish S3 API compatibility requirements, storage-engine internal abilities, admin-only capabilities, protocol smoke checks, and legacy/regression checks.
- Appendix status must be based on observed runner output, tracked implementation state, or explicit pending/unknown markers; do not infer completion from route inventories, generated API lists, README claims, or status-only smoke checks.
