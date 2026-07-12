@spec @quality-evidence @non-functional-requirement @security @observability @partial
Ability: Reactor-wide quality and dependency security evidence
  Maintainers need one reproducible evidence set for production-code coverage and
  dependency vulnerability risk so that coverage gaps, vulnerable dependencies,
  suppressed findings, and incomplete scans remain visible instead of being mistaken
  for a clean build.

  The initial coverage baseline is descriptive evidence. It does not impose an
  arbitrary line, branch, instruction, method, or class coverage threshold.
  Dependency risk is governed separately by the explicit CVSS policy below.

  Rule: One aggregate JaCoCo report represents the production reactor

    @REQ-QUAL-001 @non-functional-requirement @quality-evidence @test-coverage @jacoco @observability @partial
    Scenario: Aggregate coverage includes production modules and cross-module Cucumber execution
      Given the root Maven reactor contains all Magrathea production modules
      And unit, component, integration, and shared Cucumber runners may execute production classes from modules other than the runner module
      When the canonical reactor verification generates JaCoCo coverage evidence
      Then exactly one canonical aggregate report set is published at "target/site/jacoco-aggregate"
      And the report set contains machine-readable "jacoco.xml" and "jacoco.csv" reports and human-readable "index.html"
      And every production reactor module with instrumentable production classes is represented, including modules with zero covered instructions
      And execution data from unit, component, integration, and cross-module Cucumber runs is merged before the aggregate reports are generated
      And coverage of one production class is not counted more than once when multiple runners execute it
      And runner-local JaCoCo output is treated only as aggregate input and not as a competing canonical report

    @REQ-QUAL-002 @non-functional-requirement @quality-evidence @test-coverage @baseline @machine-readable @observability @partial
    Scenario: Machine-readable coverage summary reports an honest threshold-free baseline
      Given the canonical aggregate JaCoCo XML and CSV reports have been generated
      When the quality evidence summary is generated at "target/site/quality-evidence/summary.json"
      Then the summary identifies the reactor revision and the canonical JaCoCo report paths
      And the summary records covered and missed counts for instructions, branches, lines, methods, and classes
      And the summary records per-module covered and missed counts as well as reactor totals
      And missed counts and zero-coverage modules remain visible rather than being omitted or converted to covered results
      And the initial baseline records that no JaCoCo quality threshold is enforced
      And the summary does not label coverage clean, passing, or sufficient merely because report generation completed

  Rule: Dependency vulnerability evidence is machine-readable, reviewable, and policy-driven

    @REQ-QUAL-003 @non-functional-requirement @security @dependency-vulnerability @owasp-dependency-check @cvss @supply-chain @partial
    Scenario: Dependency-Check applies the declared CVSS and scan-completeness policy
      Given OWASP Dependency-Check scans the resolved dependencies of every production reactor module
      And the vulnerability policy fails verification for any unsuppressed dependency finding with CVSS score 7.0 or greater
      And unsuppressed findings below CVSS 7.0 remain reportable evidence even though they do not fail that CVSS gate
      And each accepted suppression requires a narrowly matched identifier, rationale, owner, and expiry or review date
      When the canonical reactor verification performs dependency analysis
      Then machine-readable JSON is published at "target/dependency-check-report.json"
      And a human-readable HTML report is published at "target/dependency-check-report.html"
      And the JSON and HTML reports cover the same reactor scan and identify the Dependency-Check and vulnerability-data versions
      And suppressed findings remain distinguishable from unsuppressed findings in the retained evidence
      And an expired, unmatched, or overly broad suppression does not silently convert a finding into a clean result
      And the verification result follows the declared CVSS policy rather than the mere existence of report files

    @REQ-QUAL-004 @non-functional-requirement @security @observability @dependency-vulnerability @scan-integrity @nvd @machine-readable @partial
    Scenario Outline: Vulnerability analysis never fabricates a clean result when NVD evidence is unavailable
      Given Dependency-Check starts with NVD data state "<nvd_state>"
      And network access to vulnerability data is "<network_state>"
      When dependency vulnerability evidence and the quality evidence summary are generated
      Then the dependency scan status is "<scan_status>"
      And verification handling is "<verification_handling>"
      And the generated analysis separately records unsuppressed findings, suppressed findings, and scan errors
      And the analysis records an evidence timestamp, source revision, dependency inventory identity, and vulnerability-data timestamp
      And scan errors include actionable NVD update, cache, analyzer, or network diagnostics without being counted as vulnerability findings
      And a complete scan with no unsuppressed findings may report zero unsuppressed findings without erasing suppressed findings
      And an incomplete or failed scan is not described as clean, passing, or zero-vulnerability evidence
      And the machine-readable summary and human-readable report communicate the same scan status

      Examples:
        | nvd_state                               | network_state | scan_status | verification_handling   |
        | current local data                      | available     | complete    | apply the CVSS policy   |
        | valid cached data within declared age   | unavailable   | complete    | apply the CVSS policy   |
        | absent, stale beyond policy, or corrupt | unavailable   | error       | fail as scan incomplete |
