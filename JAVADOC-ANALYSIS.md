# Javadoc Analysis Report

## Summary

| Metric | Value |
|-------|-------|
| **Total HTML files** | **926** |
| **Total directories** | 66 |
| **Total size (HTML)** | ~14.7 MB |
| **Total size (all assets)** | ~19 MB |
| **Average HTML size** | ~2.4 KB |
| **Largest file** | `index-all.html` (1.86 MB) |
| **Frame-based navigation** | **No** — modern javadoc 21 (no `<frame>`/`<frameset>`) |

## Asset Inventory

| Type | Count | Files |
|------|-------|-------|
| CSS | 2 | `stylesheet.css`, `script-dir/jquery-ui.min.css` |
| JS | 10 | `script.js`, `search.js`, `search-page.js`, `jquery-3.7.1.min.js`, `jquery-ui.min.js`, `member-search-index.js`, `module-search-index.js`, `package-search-index.js`, `tag-search-index.js`, `type-search-index.js` |
| SVG | 2 | `copy.svg`, `link.svg` |
| PNG | 2 | `resources/glass.png`, `resources/x.png` |
| Legal docs | 5 | `LICENSE`, `ADDITIONAL_LICENSE_INFO`, `ASSEMBLY_EXCEPTION`, `jquery.md`, `jqueryUI.md` |

## Directory Structure

66 directories under `apidocs/`, organized by Java package structure:

```
apidocs/
├── index.html                 — Overview page
├── allclasses-index.html      — All classes index
├── allpackages-index.html     — All packages index
├── overview-tree.html         — Class hierarchy tree
├── index-all.html             — Full index (1.86 MB)
├── help-doc.html              — Help
├── constant-values.html       — Constants
├── com/example/magrathea/
│   ├── objectstore/domain/      — Domain aggregates, events, value objects
│   ├── s3api/                   — DTOs (command/query), config, web adapters
│   ├── admin/                   — Admin web layer
│   ├── bootstrap/               — Bootstrap application
│   ├── storageengine/           — Domain, application, infrastructure
│   ├── reactive/                — Reactive infrastructure/application
│   └── objectstorage/           — Repository, reactive repository
├── legal/                      — License files
├── resources/                  — Static resources (glass.png, x.png)
├── script-dir/                 — jQuery UI assets
├── script.js, search.js, ...
```

## Package Distribution (Top 10 by HTML count)

| HTML count | Package |
|-----------:|---------|
| 86 | `com.example.magrathea.s3api.dto.query` |
| 83 | `com.example.magrathea.s3api.dto.query/class-use` |
| 78 | `com.example.magrathea.storageengine.domain.valueobject` |
| 75 | `com.example.magrathea.storageengine.domain.valueobject/class-use` |
| 62 | `com.example.magrathea.s3api.dto.command` |
| 62 | `com.example.magrathea.objectstore.domain.event` |
| 61 | `com.example.magrathea.objectstore.domain.valueobject` |
| 59 | `com.example.magrathea.s3api.dto.command/class-use` |
| 59 | `com.example.magrathea.objectstore.domain.event/class-use` |
| 58 | `com.example.magrathea.objectstore.domain.valueobject/class-use` |

## HTML Structure Analysis

Each page follows the **javadoc 21** format:

- **HTML5 doctype** (`<!DOCTYPE HTML>`)
- **Meta description** — contains package/record/class declaration info (machine-parseable)
- **Navigation bar** — standard top nav with Overview, Package, Class, Use, Tree, Index, Help links
- **Content region** — semantic `<main>` element with structured sections:
  - Declaration (`<dl class="notes">` / `<pre>` with record/class signature)
  - Description (`<div class="block">` for Javadoc text)
  - Method summary tables (`<table class="summary-table">`)
  - Constructor/method detail sections
- **No frame-based navigation** — all pages use flexbox layout with `<div class="flex-box">`

## Complexity Assessment

**Rating: MEDIUM** (facile)

### Why MEDIUM:
1. **No frames** — every page is a complete standalone HTML document, no split-frame complexity
2. **Structured meta descriptions** — `<meta name="description">` contains machine-readable declaration info (package, record/class name)
3. **Semantic HTML5** — consistent structure across all 926 pages
4. **Large volume** — 926 files × ~2.4 KB avg = manageable but requires batch processing
5. **Index files** — `index-all.html` (1.86 MB) and `allclasses-index.html` (173 KB) provide ready-made class/package listings
6. **class-use directories** — 15 class-use directories with 200+ HTML files (cross-reference pages), can be inferred from JSON

### What would be needed for JSON conversion:
- **Parser** — a lightweight HTML parser (e.g., JSoup in Java, or Cheerio/Parsely in Node.js) to extract:
  - Package name from meta description or nav
  - Class/record/interface name from `<title>` or `<header>`
  - Declaration signature from `<pre class="language-java">` or `<dl class="notes">`
  - Javadoc description from `<div class="block">`
  - Method/constructor details from summary tables
  - Field/constant values
- **Output schema** — JSON structure with:
  - `package`, `className`, `kind` (record/class/interface/enum)
  - `signature` (full declaration with generics)
  - `description` (Javadoc text)
  - `methods`, `fields`, `constructors` arrays
  - `seeAlso` links
- **Index files** — parse `allclasses-index.html` to build a master lookup table
- **class-use pages** — can be skipped in favor of inferring relationships from the JSON

### Recommended approach:
1. Parse `allclasses-index.html` → build class-to-package mapping
2. For each class HTML file, extract structured data using CSS selector-based parsing
3. Output a single JSON array or per-package JSON files
4. Use `index-all.html` as a fallback for missing references

## Conclusion

The conversion from HTML to JSON is **feasible with moderate effort**. The absence of frame-based navigation and the consistent HTML5 structure makes parsing straightforward. The main effort is the volume (926 files) and handling edge cases in javadoc formatting. A dedicated batch parser (JSoup with custom selectors) would complete the job efficiently.
