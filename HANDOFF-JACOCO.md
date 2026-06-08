# Handoff: JaCoCo Migration — pom.xml changes

## Completed by java-infra-coder

### Step 1 — Converter library check
Verified `bootstrap-application/src/main/scripts/lib/` — no Clover-specific references.
Files present: `converter.js`, `parser-asciidoc.js`, `parser-html.js`, `parser-markdown.js`, `post-processor.js`.
All generic — **compatible with JaCoCo**.

### Step 2 — pom.xml changes (requires java-scaffolder)

The pom.xml files need edits that are owned by `java-scaffolder`. Requested changes:

#### A. Parent `pom.xml`
1. **Remove** Clover plugin from `<build><plugins>` — delete the `org.openclover:clover-maven-plugin` block (including its XML comment)
2. **Replace** profile `coverage` with JaCoCo:
   - Remove `org.openclover:clover-maven-plugin` from profile
   - Add `org.jacoco:jacoco-maven-plugin:0.8.12` with 3 executions:
     - `jacoco-prepare` (initialize, goal: prepare-agent)
     - `jacoco-report` (verify, goal: report, output dir: `site/jacoco`)
     - `jacoco-aggregate` (verify, goal: report-aggregate)
3. **Add** new profile `legacy-clover` that preserves the original Clover configuration for backward compatibility

#### B. `bootstrap-application/pom.xml`
1. **Execution `copy-reports-to-static`** — change paths:
   - `*/target/clover/*.html` → `*/target/site/jacoco/*.html`
   - Output dir `docs/clover` → `docs/jacoco`
2. **Execution `convert-clover-to-json`** — rename to `convert-jacoco-to-json`:
   - `find */target/site/clover` → `find */target/site/jacoco`
   - `sed 's|.*/target/site/clover/||'` → `sed 's|.*/target/site/jacoco/||'`
   - Output dir `docs/clover-json` → `docs/jacoco-json`

### Step 3 — Rebuild
After pom.xml changes, run:
```bash
mvn compile -pl bootstrap-application -am -DskipTests
```

### Step 4 — Report
Write `/home/paperboy/workspace/magrathea-objectstorage/JACOCO-DONE.md` with summary of changes.
