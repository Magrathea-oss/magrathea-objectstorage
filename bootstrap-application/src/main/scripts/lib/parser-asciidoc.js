/**
 * AsciiDoc parser for unified document JSON conversion.
 *
 * Uses @asciidoctor/core to parse AsciiDoc and produce
 * a DocBlockRenderer-compatible JSON tree.
 *
 * Block types produced:
 *   paragraph, section, list, table, image, code, admonition
 *
 * @module lib/parser-asciidoc
 */

import { createRequire } from 'module';
import { fileURLToPath } from 'url';

// Load @asciidoctor/core via CJS (avoids ESM resolution issues)
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// ──────────────────────────────────────────────────────────────
// JSON tree builders
// ──────────────────────────────────────────────────────────────

/** List-like contexts whose items are accessed via getItems() */
const LIST_CONTEXTS = new Set(['ulist', 'olist', 'colist', 'dlist']);

/**
 * Convert a single AsciiDoctor node to a JSON block.
 * @param {object} node - AsciiDoctor document/section/block node
 * @returns {object|null}
 */
function blockToJson(node) {
  if (!node) return null;

  const ctx = node.getContext();

  switch (ctx) {
    case 'document': {
      const title = node.getTitle() || '';
      return {
        type: 'document',
        title,
        sections: blocksToJson(node.getBlocks()),
      };
    }

    case 'section': {
      const id = node.getId() || '';
      const title = node.getTitle() || '';
      const level = node.getLevel() || 1;
      return {
        type: 'section',
        id,
        title,
        level,
        blocks: blocksToJson(node.getBlocks()),
      };
    }

    case 'paragraph': {
      // getContent() returns the rendered inline HTML
      return { type: 'paragraph', html: node.getContent() || '' };
    }

    case 'ulist':
    case 'olist':
    case 'colist':
    case 'dlist': {
      const style = node.getStyle() || ctx;
      const items = node.getItems() || [];
      return {
        type: 'list',
        style,
        items: items.map(item => {
          // Use getText() for rendered HTML, fall back to text property
          const text = typeof item.getText === 'function' ? item.getText() : (item.text || '');
          return {
            text,
            blocks: blocksToJson(item.getBlocks()),
          };
        }),
      };
    }

    case 'table': {
      const headRows = node.getHeadRows() || [];
      const bodyRows = node.getBodyRows() || [];
      const headers = headRows.length
        ? headRows[0].map(cell => (typeof cell === 'string' ? cell : cell.getText()))
        : [];
      const rows = bodyRows.map(row =>
        row.map(cell => (typeof cell === 'string' ? cell : cell.getText()))
      );
      return { type: 'table', headers, rows };
    }

    case 'listing': {
      // getContent() returns the raw code text
      const content = node.getContent() || '';
      const style = node.getStyle() || '';
      return { type: 'code', html: `<pre><code>${content}</code></pre>` };
    }

    case 'image': {
      const target = node.getAttribute('target') || '';
      const alt = node.getAttribute('alt') || '';
      const width = node.getAttribute('width') || '';
      const height = node.getAttribute('height') || '';
      const attrs = width ? (height ? ` width="${width}" height="${height}"` : ` width="${width}"`) : '';
      const html = `<img src="${target}" alt="${alt}"${attrs}>`;
      return { type: 'image', src: target, alt, html };
    }

    case 'admonition': {
      const admonitionType = node.getType() || 'note';
      const html = node.getContent() || '';
      return {
        type: 'admonition',
        admonitionType,
        html,
        blocks: blocksToJson(node.getBlocks()),
      };
    }

    default: {
      // Fallback: try to extract HTML content
      const html = node.getContent ? node.getContent() || '' : '';
      return html ? { type: ctx || 'unknown', html } : null;
    }
  }
}

function blocksToJson(blocks) {
  if (!blocks || !blocks.length) return [];
  return blocks.map(b => blockToJson(b)).filter(Boolean);
}

// ──────────────────────────────────────────────────────────────
// Public API
// ──────────────────────────────────────────────────────────────

/**
 * Parse AsciiDoc text into a unified JSON document tree.
 *
 * @param {string} asciidoc - Raw AsciiDoc content
 * @param {object} [options] - Optional parsing options
 * @param {object} [options.attributes] - Additional AsciiDoctor attributes
 * @returns {object} JSON document tree
 */
export function parseAsciiDoc(asciidoc, options = {}) {
  const { attributes = {} } = options;

  const doc = instance.load(asciidoc, {
    backend: 'html5',
    doctype: 'article',
    safe: 'safe',
    attributes: {
      'showtitle': true,
      'icons': 'font',
      'sectlinks': true,
      ...attributes,
    },
  });

  return blockToJson(doc);
}

/**
 * Parse AsciiDoc from a file path, supporting includes.
 *
 * @param {string} filePath - Absolute path to .adoc file
 * @param {object} [options] - Optional parsing options
 * @param {object} [options.attributes] - Additional AsciiDoctor attributes
 * @returns {object} JSON document tree
 */
export function parseAsciiDocFile(filePath, options = {}) {
  const { attributes = {} } = options;

  const doc = instance.loadFile(filePath, {
    backend: 'html5',
    doctype: 'article',
    safe: 'safe',
    attributes: {
      'showtitle': true,
      'icons': 'font',
      'sectlinks': true,
      ...attributes,
    },
  });

  return blockToJson(doc);
}
