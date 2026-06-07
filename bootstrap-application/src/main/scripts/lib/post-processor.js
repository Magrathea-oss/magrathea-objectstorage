/**
 * Post-processor for unified document JSON trees.
 *
 * Provides common fix-ups:
 *   - fixImagePaths(tree, basePath)  — resolve relative image paths
 *   - convertAdrLinks(tree)          — transform ADR references to links
 *   - convertPipeTables(tree)        — convert pipe-table paragraphs to typed table blocks
 *   - fixCssReferences(tree, basePath) — resolve relative CSS paths
 *
 * Each function mutates the tree in-place.
 */

// ──────────────────────────────────────────────────────────────
// Image path fixing
// ──────────────────────────────────────────────────────────────

/**
 * Replace a relative image src with an absolute path.
 * @param {string} html - HTML string to fix
 * @param {string} basePath - Base URL path to prepend
 * @returns {string} fixed HTML
 */
export function fixImagePathsInHtml(html, basePath = '/') {
  if (!html || typeof html !== 'string') return html;
  // Replace <img src="foo.png"> (relative, no leading /) with <img src="/base/foo.png">
  return html.replace(
    /<img\s+src="(?!\/)([^"]+)"/g,
    (match, src) => `<img src="${basePath}${src}"`
  );
}

/**
 * Walk the JSON tree and fix image paths in all html/text properties.
 * @param {object} obj - JSON tree node
 * @param {string} basePath - Base URL path for images
 */
export function fixImagePaths(obj, basePath = '/') {
  if (!obj || typeof obj !== 'object') return;
  if (Array.isArray(obj)) {
    for (const item of obj) fixImagePaths(item, basePath);
    return;
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
  const { imageBasePath = '/', cssBasePath = '/' } = options;

  convertAdrLinks(tree);
  convertPipeTables(tree);
  fixImagePaths(tree, imageBasePath);
  fixCssReferences(tree, cssBasePath);
}
