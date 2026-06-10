/**
 * Post-processor for unified document JSON trees.
 *
 * Provides common fix-ups:
 *   - fixImagePaths(tree, basePath)  — resolve relative image paths
 *   - convertAdrLinks(tree)          — transform ADR references to links
 *   - convertPipeTables(tree)        — convert pipe-table paragraphs to typed table blocks
 *   - fixCssReferences(tree, basePath) — resolve relative CSS paths
 *   - fixHtmlLinks(tree) — convert .html hrefs to .json hrefs
 *
 * Each function mutates the tree in-place.
 */

import { parse } from 'node-html-parser';

// ──────────────────────────────────────────────────────────────
// Image path fixing
// ──────────────────────────────────────────────────────────────

/**
 * Resolve a relative image path against a base URL path.
 * Paths with ../ prefixes are resolved by stripping the leading
 * relative components: the ../ moves up from the source file location
 * (which is under the basePath), so we remove the ../ prefix and
 * prepend the basePath.
 *
 * @param {string} src - The relative image path from the source
 * @param {string} basePath - Base URL path for images
 * @returns {string} Resolved absolute path
 */
export function resolveImagePath(src, basePath = '/') {
  if (!src) return src;
  // If already absolute, return as-is
  if (src.startsWith('/') || src.startsWith('http://') || src.startsWith('https://')) {
    return src;
  }
  // Strip leading ../ prefixes — they point up from the source file
  // directory (which is under basePath), so we just remove them and
  // prepend the basePath.
  const stripped = src.replace(/^(?:\.\.\/)+/, '');
  const base = basePath.replace(/^\/?/, '/').replace(/\/+$/, '') + '/';
  return (base + stripped).replace(/\/+/g, '/');
}

/**
 * Replace a relative image src with an absolute path.
 * @param {string} html - HTML string to fix
 * @param {string} basePath - Base URL path to prepend
 * @returns {string} fixed HTML
 */
export function fixImagePathsInHtml(html, basePath = '/') {
  if (!html || typeof html !== 'string') return html;
  // Replace <img src="foo.png"> (relative, no leading /) with resolved path
  return html.replace(
    /<img\s+src="(?!\/|http)([^"]+)"/g,
    (match, src) => `<img src="${resolveImagePath(src, basePath)}"`
  );
}

/**
 * Walk the JSON tree and fix image paths in all html/text properties
 * AND directly on image block src fields.
 * @param {object} obj - JSON tree node
 * @param {string} basePath - Base URL path for images
 */
export function fixImagePaths(obj, basePath = '/') {
  if (!obj || typeof obj !== 'object') return;
  if (Array.isArray(obj)) {
    for (const item of obj) fixImagePaths(item, basePath);
    return;
  }
  // Fix src field directly on image blocks
  if (obj.type === 'image' && obj.src && typeof obj.src === 'string' && !obj.src.startsWith('/')) {
    obj.src = resolveImagePath(obj.src, basePath);
  }
  if (obj.html && typeof obj.html === 'string') {
    obj.html = fixImagePathsInHtml(obj.html, basePath);
  }
  if (obj.text && typeof obj.text === 'string') {
    obj.text = fixImagePathsInHtml(obj.text, basePath);
  }
  if (obj.title && typeof obj.title === 'string') {
    obj.title = fixImagePathsInHtml(obj.title, basePath);
  }
  for (const val of Object.values(obj)) {
    if (typeof val === 'object' && val !== null) fixImagePaths(val, basePath);
  }
}

// ──────────────────────────────────────────────────────────────
// ADR link conversion
// ──────────────────────────────────────────────────────────────

/**
 * Convert ADR references in a text string to <a> links.
 * @param {string} text
 * @returns {string}
 */
export function convertTextAdrLinks(text) {
  if (!text || typeof text !== 'string') return text;
  // "ADR 0008" or "ADR-0008" or "ADR 8" → <a href="/docs/adr/8">ADR 0008</a>
  text = text.replace(
    /ADR[- ]?(\d{1,4})/g,
    '<a href="/docs/adr/$1">ADR $1</a>'
  );
  // "[ADR 0008](...)" style
  text = text.replace(
    /\[ADR[- ]?(\d{1,4})\]\([^)]*\)/g,
    '<a href="/docs/adr/$1">ADR $1</a>'
  );
  return text;
}

/**
 * Walk the JSON tree and convert ADR references in all string properties.
 * @param {object} obj
 */
export function convertAdrLinks(obj) {
  if (!obj || typeof obj !== 'object') return;
  if (Array.isArray(obj)) {
    for (const item of obj) convertAdrLinks(item);
    return;
  }
  if (obj.html && typeof obj.html === 'string') obj.html = convertTextAdrLinks(obj.html);
  if (obj.text && typeof obj.text === 'string') obj.text = convertTextAdrLinks(obj.text);
  if (obj.title && typeof obj.title === 'string') {
    obj.title = convertTextAdrLinks(obj.title);
  }
  for (const val of Object.values(obj)) {
    if (typeof val === 'object' && val !== null) convertAdrLinks(val);
  }
}

// ──────────────────────────────────────────────────────────────
// Paragraph image extraction
// ──────────────────────────────────────────────────────────────

/**
 * Extract <img> tag attributes from an HTML string.
 * Uses node-html-parser (already imported) to properly parse the <img> element,
 * so attribute order does not matter. Handles any extra attributes (e.g. title).
 *
 * @param {string} html - HTML string possibly containing <img>
 * @returns {{ src: string, alt: string, width: string, height: string } | null}
 */
export function extractImgAttributes(html) {
  if (!html || typeof html !== 'string') return null;
  // Use node-html-parser to find the img element
  const root = parse(html);
  const img = root.querySelector('img');
  if (!img) return null;
  const src = img.getAttribute('src') || '';
  const alt = img.getAttribute('alt') || '';
  const width = img.getAttribute('width') || '';
  const height = img.getAttribute('height') || '';
  if (!src) return null;
  return { src, alt, width, height };
}

/**
 * Walk the JSON tree and replace paragraph blocks containing only an <img> tag
 * with typed image blocks.
 *
 * If a paragraph has text before/after the img, it is split:
 *   - text-only parts remain as paragraph blocks
 *   - the img part becomes an image block
 *
 * @param {object} obj - JSON tree node (mutated in-place)
 */
export function convertParagraphImages(obj) {
  if (!obj || typeof obj !== 'object') return;
  if (Array.isArray(obj)) {
    for (let i = 0; i < obj.length; i++) {
      const item = obj[i];
      if (item && item.type === 'paragraph' && item.html) {
        const attrs = extractImgAttributes(item.html);
        if (attrs) {
          // Check if the paragraph has text outside the img tag
          const imgTag = item.html.match(/<img[^>]*>/);
          if (imgTag) {
            const before = item.html.slice(0, imgTag.index);
            const after = item.html.slice(imgTag.index + imgTag[0].length);
            const replacement = [];
            if (before.trim()) {
              replacement.push({ type: 'paragraph', html: before });
            }
            replacement.push({
              type: 'image',
              src: attrs.src,
              alt: attrs.alt,
              html: imgTag[0],
              ...(attrs.width ? { width: attrs.width } : {}),
              ...(attrs.height ? { height: attrs.height } : {}),
            });
            if (after.trim()) {
              replacement.push({ type: 'paragraph', html: after });
            }
            // Replace the paragraph with the split blocks
            obj.splice(i, 1, ...replacement);
            // Adjust index to account for inserted blocks
            i += replacement.length - 1;
          }
        }
      } else if (typeof item === 'object') {
        convertParagraphImages(item);
      }
    }
    return;
  }
  for (const val of Object.values(obj)) {
    if (typeof val === 'object' && val !== null) convertParagraphImages(val);
  }
}

// ──────────────────────────────────────────────────────────────
// Paragraph table extraction
// ──────────────────────────────────────────────────────────────

/**
 * Extract <table> tag from an HTML string and parse it into a table block.
 * Uses node-html-parser to parse the table element.
 * Returns null if no <table> tag is found or parsing fails.
 *
 * @param {string} html - HTML string possibly containing <table>
 * @returns {object|null} Table block { type: 'table', headers, rows } or null
 */
export function extractTableFromHtml(html) {
  if (!html || typeof html !== 'string') return null;
  const tableMatch = html.match(/<table[^>]*>.*<\/table>/s);
  if (!tableMatch) return null;
  const tableHtml = tableMatch[0];
  try {
    const root = parse(tableHtml);
    const tableNode = root.querySelector('table');
    if (!tableNode) return null;

    const rows = tableNode.querySelectorAll('tr');
    if (!rows.length) return null;

    // Headers from <th> in first row
    const firstRowCells = rows[0].querySelectorAll('th, td');
    const headers = firstRowCells.map(cell => ({
      text: cell.innerHTML?.trim() || '',
      colspan: parseInt(cell.getAttribute('colspan') || '1', 10),
      rowspan: parseInt(cell.getAttribute('rowspan') || '1', 10),
    }));

    // Data rows (skip first row if it was all <th>)
    const isHeaderRow = rows[0].querySelectorAll('th').length > 0;
    const dataStart = isHeaderRow ? 1 : 0;
    const dataRows = [];

    for (let i = dataStart; i < rows.length; i++) {
      const cells = rows[i].querySelectorAll('td, th');
      const row = cells.map(cell => ({
        text: cell.innerHTML?.trim() || '',
        colspan: parseInt(cell.getAttribute('colspan') || '1', 10),
        rowspan: parseInt(cell.getAttribute('rowspan') || '1', 10),
      }));
      if (row.length) dataRows.push(row);
    }

    return { type: 'table', headers, rows: dataRows };
  } catch (e) {
    return null;
  }
}

/**
 * Walk the JSON tree and replace paragraph blocks containing <table> tags
 * with typed table blocks.
 *
 * If a paragraph has text before/after the table, it is split:
 *   - text-only parts remain as paragraph blocks
 *   - the table part becomes a table block
 *
 * @param {object} obj - JSON tree node (mutated in-place)
 */
export function convertParagraphTables(obj) {
  if (!obj || typeof obj !== 'object') return;
  if (Array.isArray(obj)) {
    for (let i = 0; i < obj.length; i++) {
      const item = obj[i];
      if (item && item.type === 'paragraph' && item.html) {
        const table = extractTableFromHtml(item.html);
        if (table) {
          // Check if the paragraph has text outside the <table> tag
          const tableTag = item.html.match(/<table[^>]*>[\s\S]*?<\/table>/);
          if (tableTag) {
            const before = item.html.slice(0, tableTag.index);
            const after = item.html.slice(tableTag.index + tableTag[0].length);
            const replacement = [];
            if (before.trim()) {
              replacement.push({ type: 'paragraph', html: before });
            }
            replacement.push(table);
            if (after.trim()) {
              replacement.push({ type: 'paragraph', html: after });
            }
            // Replace the paragraph with the split blocks
            obj.splice(i, 1, ...replacement);
            // Adjust index to account for inserted blocks
            i += replacement.length - 1;
          }
        }
      } else if (typeof item === 'object') {
        convertParagraphTables(item);
      }
    }
    return;
  }
  for (const val of Object.values(obj)) {
    if (typeof val === 'object' && val !== null) convertParagraphTables(val);
  }
}

// ──────────────────────────────────────────────────────────────
// Pipe-table conversion
// ──────────────────────────────────────────────────────────────

/**
 * Parse a raw pipe-table string into {headers, rows}.
 * Returns null if the text is not a pipe table.
 * @param {string} text
 * @returns {{headers: string[], rows: string[][]} | null}
 */
export function parsePipeTable(text) {
  if (!text || typeof text !== 'string') return null;
  const lines = text.split('\n').filter(l => l.trim() !== '');
  if (lines.length < 2) return null;
  const firstLine = lines[0].trim();
  if (!firstLine.startsWith('|') || (firstLine.match(/\|/g) || []).length < 2) return null;
  const secondLine = lines[1].trim();
  if (!secondLine.startsWith('|') || !secondLine.includes('-')) return null;

  function parseRow(line) {
    return line.split('|').map(cell => cell.trim());
  }

  const headerCells = parseRow(firstLine);
  const headers = headerCells[0] === '' ? headerCells.slice(1) : headerCells;
  if (headers[headers.length - 1] === '') headers.pop();

  const dataLines = lines.slice(2);
  const rows = dataLines.map(line => {
    const cells = parseRow(line);
    const row = cells[0] === '' ? cells.slice(1) : cells;
    if (row[row.length - 1] === '') row.pop();
    return row;
  }).filter(r => r.length > 0);

  return { headers, rows };
}

/**
 * Walk the JSON tree and replace paragraph blocks that are pipe tables
 * with typed table blocks.
 * @param {object} obj
 */
export function convertPipeTables(obj) {
  if (!obj || typeof obj !== 'object') return;
  if (Array.isArray(obj)) {
    for (let i = 0; i < obj.length; i++) {
      const item = obj[i];
      if (item && item.type === 'paragraph' && item.html) {
        const table = parsePipeTable(item.html);
        if (table) {
          obj[i] = { type: 'table', headers: table.headers, rows: table.rows };
        }
      } else if (typeof item === 'object') {
        convertPipeTables(item);
      }
    }
    return;
  }
  for (const val of Object.values(obj)) {
    if (typeof val === 'object' && val !== null) convertPipeTables(val);
  }
}

// ──────────────────────────────────────────────────────────────
// CSS reference fixing
// ──────────────────────────────────────────────────────────────

/**
 * Replace relative CSS paths in <link rel="stylesheet"> tags.
 * @param {string} html
 * @param {string} basePath
 * @returns {string}
 */
export function fixCssReferencesInHtml(html, basePath = '/') {
  if (!html || typeof html !== 'string') return html;
  return html.replace(
    /<link\s+rel="stylesheet"\s+href="(?!\/)([^"]+)"/g,
    (match, href) => `<link rel="stylesheet" href="${basePath}${href}"`
  );
}

/**
 * Walk the JSON tree and fix CSS references.
 * @param {object} obj
 * @param {string} basePath
 */
export function fixCssReferences(obj, basePath = '/') {
  if (!obj || typeof obj !== 'object') return;
  if (Array.isArray(obj)) {
    for (const item of obj) fixCssReferences(item, basePath);
    return;
  }
  if (obj.html && typeof obj.html === 'string') {
    obj.html = fixCssReferencesInHtml(obj.html, basePath);
  }
  for (const val of Object.values(obj)) {
    if (typeof val === 'object' && val !== null) fixCssReferences(val, basePath);
  }
}

// ──────────────────────────────────────────────────────────────
// HTML link fixing (.html → .json)
// ──────────────────────────────────────────────────────────────

/**
 * Replace .html hrefs with .json hrefs in HTML strings.
 * @param {string} html - HTML string to fix
 * @returns {string} fixed HTML
 */
export function fixHtmlLinksInHtml(html) {
  if (!html || typeof html !== 'string') return html;
  // Replace href="...html" followed by optional query string and/or fragment
  // with href="...json" preserving query/fragment
  return html.replace(
    /href="([^"]+?)\.html((\?[^"]*)?(#[^"]*)?)?"/g,
    (match, path, suffix) => {
      const rest = suffix || '';
      return `href="${path}.json${rest}"`;
    }
  );
}

/**
 * Walk the JSON tree and fix HTML href references (.html → .json).
 * @param {object} obj - JSON tree node
 */
export function fixHtmlLinks(obj) {
  if (!obj || typeof obj !== 'object') return;
  if (Array.isArray(obj)) {
    for (let i = 0; i < obj.length; i++) {
      const item = obj[i];
      if (typeof item === 'string') {
        obj[i] = fixHtmlLinksInHtml(item);
      } else {
        fixHtmlLinks(item);
      }
    }
    return;
  }
  if (obj.html && typeof obj.html === 'string') {
    obj.html = fixHtmlLinksInHtml(obj.html);
  }
  if (obj.text && typeof obj.text === 'string') {
    obj.text = fixHtmlLinksInHtml(obj.text);
  }
  if (obj.title && typeof obj.title === 'string') {
    obj.title = fixHtmlLinksInHtml(obj.title);
  }
  for (const val of Object.values(obj)) {
    if (typeof val === 'object' && val !== null) fixHtmlLinks(val);
  }
}

// ──────────────────────────────────────────────────────────────
// Aggregate post-processor
// ──────────────────────────────────────────────────────────────

/**
 * Apply all standard post-processors to a JSON tree.
 * @param {object} tree - The document JSON tree (mutated in-place)
 * @param {object} options
 * @param {string} [options.imageBasePath='/'] - Base path for relative image URLs
 * @param {string} [options.cssBasePath='/'] - Base path for relative CSS URLs
 */
export function applyPostProcessors(tree, options = {}) {
  const { imageBasePath = '/', cssBasePath = '/', fixHtmlLinks: doFixHtmlLinks = false } = options;

  convertAdrLinks(tree);
  convertParagraphImages(tree);
  convertParagraphTables(tree);
  convertPipeTables(tree);
  fixImagePaths(tree, imageBasePath);
  fixCssReferences(tree, cssBasePath);
  if (doFixHtmlLinks) {
    fixHtmlLinks(tree);
  }
}
