# Javadoc Parser Issue Report

## 1. How the parser currently recognizes tables

File: `bootstrap-application/src/main/scripts/lib/parser-html.js`

The parser has a dedicated `parseTable()` function that processes `<table>` elements:

```js
function parseTable(tableNode) {
  const rows = tableNode.querySelectorAll('tr');
  if (!rows.length) return null;

  // Headers from <th> in first row
  const firstRowCells = rows[0].querySelectorAll('th, td');
  const headers = firstRowCells.map(cell => cell.innerHTML?.trim() || '');

  // Data rows (skip first row if it was all <th>)
  const isHeaderRow = rows[0].querySelectorAll('th').length > 0;
  const dataStart = isHeaderRow ? 1 : 0;
  const dataRows = [];

  for (let i = dataStart; i < rows.length; i++) {
    const cells = rows[i].querySelectorAll('td, th');
    const row = cells.map(cell => cell.innerHTML?.trim() || '');
    if (row.length) dataRows.push(row);
  }

  return { type: 'table', headers, rows: dataRows };
}
```

In the main walk loop, `<table>` elements trigger `parseTable()`:

```js
if (tag === 'table') {
  const table = parseTable(node);
  if (table) addBlock(currentSection, table);
  continue;
}
```

Additionally, `<div>` elements are treated as paragraph blocks:

```js
if (tag === 'p' || tag === 'div' || tag === 'blockquote') {
  const htmlContent = node.innerHTML?.trim();
  if (htmlContent) {
    addBlock(currentSection, { type: 'paragraph', html: node.toString() });
  }
  continue;
}
```

**Summary**: The parser only recognizes tables via HTML `<table>` elements with `<tr>`/`<td>`/`<th>` tags. `<div>` elements are always treated as paragraphs.

---

## 2. Which Javadoc structures do not match

### Modern Javadoc (JDK 21) uses **div-based tables**, not HTML `<table>` elements

Statistics from the generated Javadoc (926 HTML files):
- **0 files** contain `<table>` HTML elements
- **752 files** contain `div.summary-table` CSS-class-based tables

The actual structure used by JDK 21's `javadoc` tool is:

```html
<div class="summary-table three-column-summary">
  <div class="table-header col-first">Modifier and Type</div>
  <div class="table-header col-second">Method</div>
  <div class="table-header col-last">Description</div>
  <div class="col-first even-row-color">...</div>
  <div class="col-second even-row-color">...</div>
  <div class="col-last even-row-color">...</div>
  <div class="col-first odd-row-color">...</div>
  <div class="col-second odd-row-color">...</div>
  <div class="col-last odd-row-color">...</div>
</div>
```

**Three summary table types exist:**
| Section | Class pattern | Columns |
|---|---|---|
| `nested-class-summary` | `three-column-summary` | Modifier+Type, Class, Description |
| `constructor-summary` | `two-column-summary` | Constructor, Description |
| `method-summary` | `three-column-summary` | Modifier+Type, Method, Description |

**Key CSS classes used (no HTML table elements):**
- Container: `div.summary-table`
- Row markers: `div.even-row-color`, `div.odd-row-color`
- Header cells: `div.table-header`
- Data cells: `div.col-first`, `div.col-second`, `div.col-last`, `div.col-constructor-name`
- Tab filter divs in method-summary: `div.table-tabs`

### What the parser misses

1. **No `<table>` tags found** → `parseTable()` is never called for summary sections.
2. **`<div>` elements with `summary-table` class are treated as paragraphs** → the tabular data becomes flat paragraph blocks instead of structured table blocks.
3. **Method summary tabs are also lost** → the `table-tabs` divs are rendered as paragraphs, and tab-panel content is mixed with other blocks.
4. **Inherited method lists** (e.g., "Methods inherited from class Object") are also `<div>` elements that get treated as paragraphs rather than structured lists.

### Example of broken output

A method signature like `static AbacConfiguration empty()` plus its description "Factory method — empty configuration (no rules)" would be rendered as separate paragraph blocks instead of being grouped into a table row with two columns.

---

## 3. What needs to be fixed

### 3.1. Add div-based table parsing

The parser needs a new function `parseDivTable()` that recognizes `<div class="summary-table ...">` containers and extracts:

- **Headers**: `<div class="table-header col-*">` elements → produce the headers array
- **Data rows**: Group consecutive `<div class="col-* ...">` elements into rows. The pattern is:
  - Row N: `col-first` + `col-second` + `col-last` (three-column) OR `col-constructor-name` + `col-last` (two-column)
  - Row markers: `even-row-color` / `odd-row-color` indicate alternating rows
- **Row grouping**: The number of columns determines how many consecutive `<div>` elements form one row (2 for constructor, 3 for nested/method)

### 3.2. Update the main walk loop

In the walk loop, when encountering a `<div>` element:
- Check if it has class `summary-table` (via `getAttribute('class')`)
- If yes, call `parseDivTable()` instead of treating it as a paragraph
- If no, fall back to the existing paragraph behavior

### 3.3. Handle tab-filtered method summary

The method-summary section has:
- Tab buttons (`div.table-tabs`)
- A tabpanel div containing the `summary-table`
- The tabpanel div is hidden/shown via JavaScript, but the `summary-table` content is always present in the HTML

The parser should:
- Ignore the `table-tabs` div (or extract tab labels as metadata)
- Focus on the `summary-table` div inside the tabpanel

### 3.4. Handle inherited method lists

The `div.inherited-list` block contains a flat list of inherited methods. This could be:
- Rendered as a paragraph (current behavior — acceptable)
- Or parsed into a structured list with a heading

### 3.5. Handle detail sections

The "Constructor Details" and "Method Details" sections contain `<section class="detail">` blocks with:
- A heading (`<h3>`)
- A member signature (`<div class="member-signature">`)
- A description block (`<div class="block">`)
- A `dl.notes` definition list (for `@param`, `@return`, `@see`, etc.)

These are currently rendered as paragraphs and headings, but the structure (heading → signature → description) is lost. A future improvement could parse `detail` sections into structured blocks with signature + description fields.

### 3.6. Specific code changes needed in `parser-html.js`

```js
// New function: parseDivTable
function parseDivTable(divNode) {
  const classAttr = divNode.getAttribute('class') || '';
  const children = divNode.querySelectorAll('div');
  if (!children.length) return null;

  // Determine column count from class name
  let columnCount = 2; // default
  if (classAttr.includes('three-column')) columnCount = 3;

  // Extract headers: div.table-header elements at the start
  const headers = [];
  let i = 0;
  while (i < children.length && children[i].getAttribute('class')?.includes('table-header')) {
    headers.push(children[i].innerHTML?.trim() || '');
    i++;
  }

  // Data rows: group remaining children by columnCount
  const dataRows = [];
  const dataChildren = children.slice(i);
  for (let r = 0; r < dataChildren.length; r += columnCount) {
    const row = [];
    for (let c = 0; c < columnCount && r + c < dataChildren.length; c++) {
      row.push(dataChildren[r + c].innerHTML?.trim() || '');
    }
    if (row.length) dataRows.push(row);
  }

  return { type: 'table', headers, rows: dataRows };
}

// Updated walk loop div handling:
if (tag === 'div') {
  const classAttr = node.getAttribute('class') || '';
  if (classAttr.includes('summary-table')) {
    const table = parseDivTable(node);
    if (table) addBlock(currentSection, table);
  } else {
    // existing paragraph handling
    const htmlContent = node.innerHTML?.trim();
    if (htmlContent) {
      addBlock(currentSection, { type: 'paragraph', html: node.toString() });
    }
  }
  continue;
}
```

### 3.7. Integration test

After the fix, verify with a test case:
- Parse `AbacConfiguration.html` and confirm the output contains a `table` block with headers `["Modifier and Type", "Method", "Description"]` and rows for `empty()`, `equals()`, `hashCode()`, `of()`, `rules()`, `toString()`.
- The constructor summary should produce a 2-column table.
- The nested class summary should produce a 3-column table.

---

## Root cause summary

| Problem | Detail |
|---|---|
| **JDK 21 Javadoc uses div-based tables** | No `<table>`/`<tr>`/`<td>`/`<th>` elements — uses `<div class="summary-table">` with CSS-class-based rows/columns |
| **Parser only handles `<table>` elements** | `parseTable()` is never reached for any Javadoc HTML file |
| **`<div>` elements are always paragraphs** | The summary-table divs become flat paragraph blocks, losing all tabular structure |
| **Method summary tabs are ignored** | Tab-filter divs are rendered as paragraphs, adding noise |
| **Detail sections lack structure** | Method/constructor detail sections have heading + signature + description but are rendered as flat paragraphs |

**Fix required**: Add `parseDivTable()` to handle `<div class="summary-table ...">` elements, and update the walk loop to dispatch div-based tables instead of treating them as paragraphs.
