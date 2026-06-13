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

## 3. Gherkin authoring rules

- Prefer semantic Gherkin over comments. The feature text should explain intent; comments should be rare and limited to conventions that cannot be expressed in Gherkin.
- Requirement features may use expressive Gherkin keywords and structures, including:
  - `Business Need`
  - `Ability`
  - `Rule`
  - `Scenario Outline`
  - `Examples`
  - data tables
  - doc strings
- Each scenario should include a stable requirement identifier when it represents a production requirement.
- Scenarios must describe realistic preconditions, actions, validation mode, and observable outcomes.
- Use domain language for capabilities such as durability, integrity, restart safety, streaming, observability, backend behavior, and storage layout.

## 4. Single shared feature / dual runner policy

- A `.feature` file is the single source of truth for a requirement.
- Do not duplicate the same feature text into separate WebTestClient and AWS CLI folders.
- Future WebTestClient and AWS CLI validation must reuse the same shared feature resource.
- Runner-specific differences belong in duplicated or adapted step definitions, glue code, runner configuration, tags, profiles, or validation adapters, not in duplicated requirement text.
- When both WebTestClient and AWS CLI validation are required, keep the requirement scenario shared and express runner selection through validation mode, tags, or runner configuration.

## 5. Functional vs non-functional requirement classification

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

## 6. Fixture/path/header policy

Requirement scenarios must use realistic and reviewable details, including where relevant:

- fixture paths and fixture file names;
- object keys and object paths;
- storage roots and backend identifiers;
- HTTP headers and metadata headers;
- content type, content length, checksums, ETags, ranges, and conditional request headers;
- validation modes such as WebTestClient, AWS CLI, restart validation, filesystem inspection, or metadata inspection;
- observable outcomes such as persisted bytes, metadata, headers, errors, logs, metrics, restart behavior, and integrity checks.

Avoid placeholder paths and abstract examples when realistic fixtures or object names would make the requirement clearer.

## 7. ARC42/Gherkin appendix reporting policy

Generated Gherkin requirements reporting must become an ARC42 appendix. The appendix should group scenarios by:

- requirement ID;
- functional and/or non-functional classification;
- capability area;
- rule;
- tags;
- validation mode;
- implementation status.

Reports must not invent test results or implementation status. Use observed runner output, tracked implementation state, or clearly mark unknown or pending items.

## 8. Definition of done for future tests

A future test or validation change is done only when:

- the corresponding shared `.feature` requirement exists or has been refreshed first;
- the scenario describes real behavior and observable outcomes, not only status codes;
- each production requirement scenario has functional and/or non-functional classification tags;
- smoke-only checks are tagged `@protocol-smoke`;
- realistic fixtures, paths, headers, backend details, validation modes, and expected outcomes are present where relevant;
- WebTestClient and AWS CLI validation reuse the shared feature text instead of duplicating it;
- runner-specific behavior is isolated to glue, step definitions, runner configuration, or validation adapters;
- generated requirements reporting can place the scenario in the ARC42 appendix with requirement ID, classification, capability area, rule, tags, validation mode, and implementation status.
