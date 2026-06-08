# JaCoCo Replacement Analysis — Clover → JaCoCo Migration

## Current Clover Configuration

### Parent POM (`pom.xml`)

Two locations:

1. **Plugin declaration** (lines 48–52) — configuration-only, no executions:
   ```xml
   <plugin>
       <groupId>org.openclover</groupId>
       <artifactId>clover-maven-plugin</artifactId>
       <version>4.5.2</version>
       <configuration>
           <generateHtml>true</generateHtml>
           <flushOnApplicationStop>true</flushOnApplicationStop>
           <includes>**/*.java</includes>
           <excludes>**/*Test*.java,**/*IT*.java</excludes>
       </configuration>
   </plugin>
   ```

2. **`coverage` profile** (lines 185–202) — active executions:
   ```xml
   <profile>
       <id>coverage</id>
       <build>
           <plugins>
               <plugin>
                   <groupId>org.openclover</groupId>
                   <artifactId>clover-maven-plugin</artifactId>
                   <version>4.5.2</version>
                   <executions>
                       <execution>
                           <id>clover</id>
                           <phase>verify</phase>
                           <goals>
                               <goal>instrument</goal>
                               <goal>aggregate</goal>
                               <goal>clover</goal>
                               <goal>check</goal>
                           </goals>
                       </execution>
                   </executions>
               </plugin>
           </plugins>
       </build>
   </profile>
   ```

### `bootstrap-application/pom.xml` — Clover-dependent exec scripts

Two exec scripts reference Clover-specific output paths:

1. **`copy-reports-to-static`** (line 180–181):
   - Copies `*/target/clover/*.html` → `static/docs/clover/`

2. **`convert-clover-to-json`** (lines 189–201):
   - Finds HTML files from `*/target/site/clover/*.html`
   - Converts to JSON via `html-to-json.mjs`

### Output Paths

| Tool    | HTML output                            | Other artifacts                  |
|---------|----------------------------------------|----------------------------------|
| Clover  | `*/target/site/clover/*.html`          | `*/target/clover/*.html`         |
| JaCoCo  | `*/target/site/jacoco/*.html`          | `*/target/site/jacoco/*.xml`     |

---

## What JaCoCo Replacement Requires

### 1. Parent POM — replace Clover with JaCoCo in `coverage` profile

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <phase>initialize</phase>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <!-- optional: coverage check -->
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

Also remove the Clover plugin declaration from the main `<build><plugins>` section (lines 48–52), since JaCoCo should only be activated via the profile.

### 2. `bootstrap-application/pom.xml` — update exec scripts

Two changes needed:

| Script                   | Old path                         | New path                         |
|--------------------------|----------------------------------|----------------------------------|
| `copy-reports-to-static` | `*/target/clover/*.html`         | `*/target/site/jacoco/*.html`    |
| `convert-clover-to-json` | `*/target/site/clover/*.html`    | `*/target/site/jacoco/*.html`    |

And rename the execution IDs from `convert-clover-to-json` to `convert-jacoco-to-json`.

### 3. Optional: HTML-to-JSON converter compatibility

JaCoCo HTML reports have a different DOM structure than Clover. The `html-to-json.mjs` script may need adjustment if it parses specific HTML elements. If it simply converts any HTML to JSON, no change is needed.

---

## Complexity Assessment

**Rating: MEDIUM**

| Factor | Impact |
|--------|--------|
| Plugin replacement in parent POM | **Easy** — straightforward XML swap |
| Remove Clover from `<build><plugins>` | **Easy** — delete lines 48–52 |
| Update `bootstrap-application/pom.xml` exec scripts | **Easy** — 2 path changes + rename |
| JaCoCo agent approach vs Clover instrumentation | **Medium** — different mechanism (runtime agent vs compile-time instrument). May affect how `mvn test` runs without the profile. |
| HTML-to-JSON script compatibility | **Medium** — depends on whether `html-to-json.mjs` parses Clover-specific HTML elements or handles generic HTML |
| Coverage check threshold format | **Medium** — Clover `<check>` uses its own XML config; JaCoCo `<rules>/<rule>/<limit>` uses a different schema |
| `flushOnApplicationStop` / Clover-specific config | **N/A** — JaCoCo doesn't need these |

### Summary of files to modify

| File | Changes needed |
|------|----------------|
| `pom.xml` | Replace Clover plugin with JaCoCo in `coverage` profile; remove Clover from `<build><plugins>` |
| `bootstrap-application/pom.xml` | Update 2 exec scripts (paths + IDs) |
| `src/main/scripts/html-to-json.mjs` | Possibly update if it depends on Clover HTML structure |

### Estimated effort

~15–20 minutes for a developer familiar with Maven and JaCoCo.
