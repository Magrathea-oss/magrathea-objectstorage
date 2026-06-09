/**
 * HTML parser for unified document JSON conversion.
 *
 * Uses node-html-parser (npm) to parse HTML and produce
 * a DocBlockRenderer-compatible JSON tree.
 *
 * Block types produced:
 *   paragraph, section, list, table, image, code
 *
 * @module lib/parser-html
 */

import { parse } from 'node-html-parser';

// ──────────────────────────────────────────────────────────────
// Tags to ignore entirely (navigation, scripts, styles)
// ──────────────────────────────────────────────────────────────

const SKIP_TAGS = new Set([
  'nav', 'script', 'style', 'header', 'footer', 'noscript',
]);

// ──────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────

/**
 * Escape HTML special characters for safe embedding in <code> blocks.
 */
function escapeHtml(text) {
  if (!text) return '';
  return text
    .replace(/&/g, '&')
    .replace(/</g, '<')
    .replace(/>/g, '>')
    .replace(/"/g, '"')
    .replace(/'/g, '&#039;');
}

/**
 * Convert inline text to HTML, preserving existing HTML tags.
 */
function toHtml(node) {
  if (!node) return '';
  return node.toString();
}

/**
 * Depth-first traversal yielding all element nodes (nodeType === 1).
 * Optionally skips children of nodes in skipDescendantsOf.
 * @param {object} node - Root node to walk from
 * @param {Set<object>} [skipDescendantsOf] - Set of nodes whose children to skip
 * @returns {object[]} Array of element nodes in document order
 */
function walk(node, skipDescendantsOf) {
  const result = [];
  if (!node) return result;
  if (node.nodeType === 1) result.push(node);
  if (node.childNodes && !(skipDescendantsOf && skipDescendantsOf.has(node))) {
    for (const child of node.childNodes) {
      result.push(...walk(child, skipDescendantsOf));
    }
  }
  return result;
}

/**
 * Check if a node or any of its ancestors is a navigation/skip element.
 * @param {object} node - Element node
 * @returns {boolean} true if inside nav, header, footer, etc.
 */
function isNavigation(node) {
  let el = node;
  while (el) {
    const tag = el.tagName?.toLowerCase();
    if (tag && SKIP_TAGS.has(tag)) return true;
    el = el.parentNode;
  }
  return false;
}

// ──────────────────────────────────────────────────────────────
// Section / heading detection
// ──────────────────────────────────────────────────────────────

/**
 * Extract the document title from <title> or first <h1>.
 * @param {HTMLElement} root
 * @returns {string}
 */
function extractTitle(root) {
  const titleTag = root.querySelector('title');
  if (titleTag) {
    const text = titleTag.textContent || titleTag.text || '';
    return text.trim();
  }
  const h1 = root.querySelector('h1');
  if (h1) {
    return h1.textContent?.trim() || '';
  }
  return '';
}

/**
 * Extract the lang from <html lang="...">.
 * @param {HTMLElement} root
 * @returns {string}
 */
function extractLang(root) {
  const htmlTag = root.querySelector('html');
  if (htmlTag) {
    const lang = htmlTag.getAttribute('lang');
    if (lang) return lang;
  }
  return 'en';
}

// ──────────────────────────────────────────────────────────────
// Table parser
// ──────────────────────────────────────────────────────────────

/**
 * Extract colspan/rowspan from a cell element, returning
 * { text, colspan, rowspan } if attributes present, else plain string.
 * @param {object} cell - Parsed <th> or <td> element
 * @returns {string|object} Plain string if no span, object with text/colspan/rowspan
 */
function extractCell(cell) {
  const text = cell.innerHTML?.trim() || '';
  const colspanAttr = cell.getAttribute('colspan');
  const rowspanAttr = cell.getAttribute('rowspan');
  const colspan = colspanAttr ? parseInt(colspanAttr, 10) : 1;
  const rowspan = rowspanAttr ? parseInt(rowspanAttr, 10) : 1;
  if (colspan > 1 || rowspan > 1) {
    return { text, colspan, rowspan };
  }
  return text;
}

/**
 * Parse a <table> element into headers + rows.
 * Headers/cells are strings by default, but become objects
 * { text, colspan, rowspan } when colspan or rowspan > 1.
 * @param {object} tableNode - Parsed <table> element
 * @returns {object|null} { type: 'table', headers, rows } or null
 */
function parseTable(tableNode) {
  const rows = tableNode.querySelectorAll('tr');
  if (!rows.length) return null;

  // Headers from <th> in first row
  const firstRowCells = rows[0].querySelectorAll('th, td');
  const headers = firstRowCells.map(cell => extractCell(cell));

  // Data rows (skip first row if it was all <th>)
  const isHeaderRow = rows[0].querySelectorAll('th').length > 0;
  const dataStart = isHeaderRow ? 1 : 0;
  const dataRows = [];

  for (let i = dataStart; i < rows.length; i++) {
    const cells = rows[i].querySelectorAll('td, th');
    const row = cells.map(cell => extractCell(cell));
    if (row.length) dataRows.push(row);
  }

  return { type: 'table', headers, rows: dataRows };
}

// ──────────────────────────────────────────────────────────────
// Div-table parser (Javadoc 21 summary-table)
// ──────────────────────────────────────────────────────────────

/**
 * Parse a <div class="summary-table ..."> element into headers + rows.
 * Javadoc 21 uses div-based tables where:
 *   - Children with class "table-header" form the header row
 *   - Remaining div children are grouped by header count to form data rows
 * @param {object} divNode - Parsed <div class="summary-table"> element
 * @returns {object|null} { type: 'table', headers, rows } or null
 */
function parseDivTable(divNode) {
  const children = divNode.childNodes.filter(
    child => child.nodeType === 1 && child.tagName?.toLowerCase() === 'div'
  );
  if (!children.length) return null;

  // Extract header cells: divs with class "table-header"
  const headerDivs = children.filter(c => {
    const cls = c.getAttribute('class') || '';
    return cls.includes('table-header');
  });

  const headers = headerDivs.map(c => extractCell(c));
  if (!headers.length) return null;

  const numCols = headers.length;

  // Data rows: group remaining divs by numCols
  const dataDivs = children.filter(c => {
    const cls = c.getAttribute('class') || '';
    return !cls.includes('table-header');
  });

  const rows = [];
  for (let i = 0; i + numCols - 1 < dataDivs.length; i += numCols) {
    const row = [];
    for (let j = 0; j < numCols; j++) {
      row.push(extractCell(dataDivs[i + j]));
    }
    // Skip empty rows
    if (row.some(cell => typeof cell === 'string' ? cell.length > 0 : cell.text.length > 0)) {
      rows.push(row);
    }
  }

  return { type: 'table', headers, rows };
}

// ──────────────────────────────────────────────────────────────
// List parser
// ──────────────────────────────────────────────────────────────

/**
 * Parse a <ul> or <ol> element into list items.
 * @param {object} listNode - Parsed <ul> or <ol> element
 * @returns {object} { type: 'list', style, items }
 */
function parseList(listNode) {
  const tag = listNode.tagName?.toLowerCase();
  const style = tag === 'ul' ? 'ulist' : 'olist';
  const liElements = listNode.querySelectorAll('li');
  const items = liElements.map(li => ({
    text: li.innerHTML?.trim() || '',
    blocks: [],
  }));
  return { type: 'list', style, items };
}

// ──────────────────────────────────────────────────────────────
// Block accumulator
// ──────────────────────────────────────────────────────────────

/**
 * Add a block to a section (if section is active).
 * @param {object|null} section - Current section object
 * @param {object} block - Block to add
 */
function addBlock(section, block) {
  if (section) section.blocks.push(block);
}

// ──────────────────────────────────────────────────────────────
// Main parse function
// ──────────────────────────────────────────────────────────────

/**
 * Process the entire DOM and produce a unified JSON document tree.
 * Uses depth-first traversal to handle nested elements properly.
 *
 * @param {string} html - Raw HTML content
 * @returns {object} Document JSON tree
 */
export function parseHtml(html) {
  const root = parse(html);

  const document = {
    type: 'document',
    title: extractTitle(root),
    sections: [],
  };

  const lang = extractLang(root);
  if (lang !== 'en') document.lang = lang;

  // Target main content area (skip nav/header/footer)
  const main = root.querySelector('main') || root.querySelector('body') || root;

  // Collect all summary-table divs so we can skip their children during walk
  const summaryTableDivs = new Set();
  for (const el of walk(main)) {
    if (el.tagName?.toLowerCase() === 'div') {
      const cls = el.getAttribute('class') || '';
      if (cls.includes('summary-table')) {
        summaryTableDivs.add(el);
      }
    }
  }

  let currentSection = null;
  let sectionLevel = 0;

  // Walk all element nodes depth-first, skipping children of summary-table divs
  for (const node of walk(main, summaryTableDivs)) {
    // Skip navigation / unwanted elements
    if (SKIP_TAGS.has(node.tagName?.toLowerCase())) continue;
    if (isNavigation(node)) continue;

    const tag = node.tagName?.toLowerCase();

    // ── Headings → create sections ──
    if (tag === 'h1' || tag === 'h2' || tag === 'h3' || tag === 'h4' ||
        tag === 'h5' || tag === 'h6') {
      const level = parseInt(tag.charAt(1), 10);
      const text = node.textContent?.trim() || '';
      const id = node.getAttribute('id') || '_' + text.toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_|_$/g, '');

      // Finalize previous section whenever we encounter a new heading
      if (currentSection) {
        document.sections.push(currentSection);
      }

      currentSection = { type: 'section', id, title: text, level, blocks: [] };
      sectionLevel = level;
      continue;
    }

    // ── Paragraphs / divs → paragraph blocks (or div-tables) ──
    if (tag === 'p' || tag === 'div' || tag === 'blockquote') {
      // Check if this div is a Javadoc 21 summary-table
      if (tag === 'div') {
        const cls = node.getAttribute('class') || '';
        if (cls.includes('summary-table')) {
          const table = parseDivTable(node);
          if (table) addBlock(currentSection, table);
          continue;
        }
      }
      const htmlContent = node.innerHTML?.trim();
      if (htmlContent) {
        addBlock(currentSection, { type: 'paragraph', html: node.toString() });
      }
      continue;
    }

    // ── Tables ──
    if (tag === 'table') {
      const table = parseTable(node);
      if (table) addBlock(currentSection, table);
      continue;
    }

    // ── Lists ──
    if (tag === 'ul' || tag === 'ol') {
      const list = parseList(node);
      addBlock(currentSection, list);
      continue;
    }

    // ── Images ──
    if (tag === 'img') {
      const src = node.getAttribute('src') || '';
      const alt = node.getAttribute('alt') || '';
      const width = node.getAttribute('width') || '';
      const height = node.getAttribute('height') || '';
      const attrs = width ? (height ? ` width="${width}" height="${height}"` : ` width="${width}"`) : '';
      const htmlContent = `<img src="${src}" alt="${alt}"${attrs}>`;
      addBlock(currentSection, { type: 'image', html: htmlContent });
      continue;
    }

    // ── Code blocks ──
    if (tag === 'pre') {
      const code = node.querySelector('code');
      if (code) {
        const content = escapeHtml(code.textContent || '');
        const langAttr = code.getAttribute('class') || '';
        addBlock(currentSection, { type: 'code', html: `<pre><code>${content}</code></pre>` });
      } else {
        const content = escapeHtml(node.textContent || '');
        addBlock(currentSection, { type: 'code', html: `<pre>${content}</pre>` });
      }
      continue;
    }

    if (tag === 'code') {
      addBlock(currentSection, { type: 'code', html: node.toString() });
      continue;
    }
  }

  // Push final section
  if (currentSection) {
    document.sections.push(currentSection);
  }

  return document;
}
