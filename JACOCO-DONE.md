# JaCoCo Migration Report

## Changes Applied

### A. Parent `pom.xml` — Modifications 1 & 2 (Completed)

**Modification 1:** Removed Clover plugin from `<build><plugins>`.
- The `org.openclover:clover-maven-plugin` block (with inline configuration) was removed from the base build plugins section.
- Cucumber reporting now follows directly after the compiler plugin.

**Modification 2:** Replaced the `coverage` profile with JaCoCo + `legacy-clover`.
- Old `coverage` profile (Clover) was replaced with:
  - **`coverage`** profile using `org.jacoco:jacoco-maven-plugin:0.8.12` with `prepare-agent` (initialize) and `report` (verify) executions.
  - **`legacy-clover`** profile using `org.openclover:clover-maven-plugin:4.5.2` with instrument/aggregate/clover/check goals, preserving backward compatibility for teams still using Clover.

### B. `bootstrap-application/pom.xml` — Modifications 3 & 4 (Handed off)

**Modification 3:** `copy-reports-to-static` exec script.
- Changed `*/target/clover/*.html` → `*/target/site/jacoco/*.html`
- Changed output directory `docs/clover` → `docs/jacoco`

**Modification 4:** `convert-clover-to-json` → `convert-jacoco-to-json`.
- Execution ID changed from `convert-clover-to-json` to `convert-jacoco-to-json`
- Source path changed from `*/target/site/clover` to `*/target/site/jacoco`
- Output directory changed from `docs/clover-json` to `docs/jacoco-json`

**Note:** Modifications 3 & 4 were delegated to `java-infra-coder` (via parent `java-planner`) because `java-scaffolder` lacks the `application-code-generation` capability required for editing `bootstrap-application/pom.xml`.

## Next Steps
- Verify `bootstrap-application/pom.xml` changes once `java-infra-coder` completes.
- Run `mvn compile -pl bootstrap-application -am -DskipTests` to confirm the build passes.
