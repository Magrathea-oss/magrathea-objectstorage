# Repository Agent Instructions

These instructions apply to all future agents working in this repository. They define the repository-level policy for requirement writing, Gherkin authoring, and test validation.

## 1. Project-specific agent rules

- Write all repository instructions, requirement text, reports, and documentation in English.
- Treat executable requirements as product documentation, not as thin transport checks.
- Before each implementation phase listed in `PLAN.md`, create or refresh the real requirement scenarios for that phase first.
- Keep requirement scenarios aligned with observable object storage behavior: fixture paths, object paths, storage roots, headers, backend selection, validation modes, and externally visible outcomes.
- Do not treat generated or runner-specific test scaffolding as the source of a requirement. The `.feature` file is the source of truth.

## 2. Requirement-first testing policy

- Gherkin scenarios must describe real requirements and business/technical intent.
- Avoid anemic request/status-code checks when writing production requirements.
- Status-code-only checks are protocol smoke tests only. Tag them with `@protocol-smoke` and do not classify them as production requirements.
- Requirement scenarios must be written or refreshed before implementation work for the corresponding phase begins.
- Implementation, runner glue, and validation code must follow the requirement text, not the other way around.

## 3. Completion evidence and requirement status policy

- **Completion claims require code + semantic tests.** An API, feature, route family, or requirement may be described as done only when production code implements the promised behavior and semantic tests validate the observable outcomes.
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
- If a feature is declared supported but is placeholder or config-only, create or update a shared requirement feature classified as `Business Need` when it is part of S3 compatibility, including extended S3 APIs that are declared supported. Tag the scenarios with `@partial`, `@config-only`, `@placeholder`, or `@not-implemented` as appropriate.
- Do not mark a capability done until the behavior is implemented and validated by both agreed validation modes where applicable, for example WebTestClient and AWS CLI for shared S3 compatibility scenarios.
- `@protocol-smoke` scenarios and status-code-only checks cannot upgrade a requirement or feature status.

## 4. Gherkin taxonomy and authoring rules

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
- Use `Business Need` for externally promised S3 server behavior through S3-compatible APIs, including extended S3 APIs if they are declared supported.
- Use `Ability` for storage-engine internals and other internal or operational capabilities that support S3 needs.
- Use `Business Need` or `Ability` for admin-only capabilities depending on whether they are user-visible admin product requirements or internal operational support requirements.
- Admin-only storage-engine features should carry tags such as `@admin-api`, `@storage-policy`, `@storage-device`, `@disk-set`, and `@backend-status`; do not tag them as `@s3-api` unless they are validated through actual S3-compatible endpoints.
- Use `Feature` for protocol smoke checks, regression checks, or legacy technical checks that are not product-level S3 compatibility promises.
- Each scenario should include a stable requirement identifier when it represents a production requirement.
- Scenarios must describe realistic preconditions, actions, validation mode, and observable outcomes.
- Use domain language for capabilities such as durability, integrity, restart safety, streaming, observability, backend behavior, and storage layout.

## 5. Storage-engine external boundary

- The storage-engine must not expose a parallel external API for S3 object behaviors.
- If a behavior is part of S3 semantics, the external API must be S3-compatible endpoints; the storage-engine remains an internal implementation behind the object-store/storage-engine ACL.
- S3 client requirements must be modeled and validated through S3 APIs, using WebTestClient and AWS CLI runners against shared feature files where both validation modes are required.
- Storage-engine requirements are internal or operational `Ability` requirements unless they describe admin-only configuration or state that has no S3 API counterpart.
- External access to storage-engine capabilities exists only through the Admin panel/Admin API, and only for requirements with no S3 API equivalent, such as storage policy definitions, storage devices, disks/disk sets/topology, backend status, and admin validation/reporting.
- The Admin API/UI may expose storage policies, storage devices, disk sets/topology, backend status, and validation/reporting; it must not become an alternate object API.
- Do not invent public storage-engine endpoints for object upload, read, delete, list, multipart, metadata, tagging, ACL, bucket, or other S3 object semantics.

## 6. Single shared feature / dual runner policy

- A `.feature` file is the single source of truth for a requirement.
- Do not duplicate the same feature text into separate WebTestClient and AWS CLI folders.
- Future WebTestClient and AWS CLI validation must reuse the same shared feature resource.
- Runner-specific differences belong in duplicated or adapted step definitions, glue code, runner configuration, tags, profiles, or validation adapters, not in duplicated requirement text.
- When both WebTestClient and AWS CLI validation are required, keep the requirement scenario shared and express runner selection through validation mode, tags, or runner configuration.

## 7. Functional vs non-functional requirement classification

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
- Do not use `@protocol-smoke` scenarios as substitutes for functional or non-functional production requirements.

## 8. Fixture/path/header policy

Requirement scenarios must use realistic and reviewable details, including where relevant:

- fixture paths and fixture file names;
- object keys and object paths;
- storage roots and backend identifiers;
- HTTP headers and metadata headers;
- content type, content length, checksums, ETags, ranges, and conditional request headers;
- validation modes such as WebTestClient, AWS CLI, restart validation, filesystem inspection, or metadata inspection;
- observable outcomes such as persisted bytes, metadata, headers, errors, logs, metrics, restart behavior, and integrity checks.

Avoid placeholder paths and abstract examples when realistic fixtures or object names would make the requirement clearer.

## 9. ARC42/Gherkin appendix reporting policy

Generated Gherkin requirements reporting must become an ARC42 appendix. The appendix should group scenarios by:

- requirement ID;
- functional and/or non-functional classification;
- capability area;
- rule;
- tags;
- validation mode;
- declared support status and validated implementation status.

Reports must not invent test results or implementation status. Use observed runner output, tracked implementation state, or clearly mark unknown or pending items.

## 10. Definition of done for future tests

A future test or validation change is done only when:

- the corresponding shared `.feature` requirement exists or has been refreshed first;
- the scenario describes real behavior and observable outcomes, not only status codes;
- each production requirement scenario has functional and/or non-functional classification tags;
- smoke-only checks are tagged `@protocol-smoke`;
- realistic fixtures, paths, headers, backend details, validation modes, and expected outcomes are present where relevant;
- WebTestClient and AWS CLI validation reuse the shared feature text instead of duplicating it;
- runner-specific behavior is isolated to glue, step definitions, runner configuration, or validation adapters;
- generated requirements reporting can place the scenario in the ARC42 appendix with requirement ID, classification, capability area, rule, tags, validation mode, declared support status, and validated implementation status.
