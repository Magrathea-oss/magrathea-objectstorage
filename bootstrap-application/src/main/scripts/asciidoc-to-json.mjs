/**
 * Convert all multilingual AsciiDoc user manual files to structured JSON.
 *
 * Reads docs/usermanual/{lang}/index.adoc for each language,
 * parses via @asciidoctor/core's load() API to extract document structure,
 * serialises as a JSON document tree,
 * and writes to bootstrap-application/src/main/resources/static/docs/index.{lang}.json.
 *
 * Usage: node src/main/scripts/asciidoc-to-json.mjs
 * (no arguments — paths are resolved relative to the script location)
 */

import { existsSync, readFileSync, mkdirSync, writeFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { createRequire } from 'module';
import { fileURLToPath } from 'url';

// Resolve script directory for relative path calculation
const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, '../../..'); // bootstrap-application root
const magratheaRoot = resolve(projectRoot, '..');   // magrathea-objectstorage root

const DOCS_DIR = resolve(magratheaRoot, 'docs/usermanual');
const OUTPUT_DIR = resolve(projectRoot, 'src/main/resources/static/docs');

const LANGUAGES = ['en', 'it', 'es', 'de', 'cn'];

// Title per language
const TITLES = {
  en: 'Magrathea ObjectStore — User Manual',
  it: 'Magrathea ObjectStore — Manuale Utente',
  es: 'Magrathea ObjectStore — Manual de Usuario',
  de: 'Magrathea ObjectStore — Benutzerhandbuch',
  cn: 'Magrathea ObjectStore — 用户手册',
};

// Load @asciidoctor/core via CJS (avoids ESM resolution issues)
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// ──────────────────────────────────────────────────────────────────
// JSON tree builders
// ──────────────────────────────────────────────────────────────────

// Context values observed from @asciidoctor/core:
//   document, section, paragraph, ulist, olist, colist, dlist,
//   table, listing, admonition, embedded, etc.

/** List-like contexts whose items are accessed via getItems() */
const LIST_CONTEXTS = new Set(['ulist', 'olist', 'colist', 'dlist']);

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
      return { type: 'codeblock', content, style };
    }

    case 'admonition': {
      const admonitionType = node.getType() || 'note';
      // getContent() returns the rendered HTML
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

// ──────────────────────────────────────────────────────────────────
// Main
// ──────────────────────────────────────────────────────────────────

mkdirSync(OUTPUT_DIR, { recursive: true });

for (const lang of LANGUAGES) {
  const adocPath = resolve(DOCS_DIR, lang, 'index.adoc');
  if (!existsSync(adocPath)) {
    console.warn(`WARNING: ${adocPath} not found, skipping`);
    continue;
  }

  const adocContent = readFileSync(adocPath, 'utf8');

  // Parse the document into a structured object tree
  const doc = instance.load(adocContent, {
    backend: 'html5',
    doctype: 'article',
    safe: 'safe',
    attributes: {
      'showtitle': true,
      'icons': 'font',
      'sectlinks': true,
      'source-highlighter': 'highlight.js',
      'stylesheet!': '',
    },
  });

  // Build JSON tree
  const jsonTree = blockToJson(doc);

  // Wrap with metadata
  const output = {
    title: TITLES[lang],
    lang,
    document: jsonTree,
  };

  const outputPath = resolve(OUTPUT_DIR, `index.${lang}.json`);
  writeFileSync(outputPath, JSON.stringify(output, null, 2), 'utf8');
  console.log(`✅ Generated ${outputPath}`);
}

console.log('Done.');
