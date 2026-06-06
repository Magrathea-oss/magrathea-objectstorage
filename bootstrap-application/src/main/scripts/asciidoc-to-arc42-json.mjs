/**
 * Convert ARC42 architecture documentation template to structured JSON.
 *
 * Reads docs/arc42/arc42-template.adoc which includes 12 sections from docs/arc42/src/*.adoc,
 * parses via @asciidoctor/core's loadFile() API to extract document structure,
 * and writes to bootstrap-application/src/main/resources/static/docs/arc42.json.
 *
 * Usage: node src/main/scripts/asciidoc-to-arc42-json.mjs
 * (no arguments — paths are resolved relative to the script location)
 */

import { existsSync, mkdirSync, writeFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { createRequire } from 'module';
import { fileURLToPath } from 'url';

// Resolve script directory for relative path calculation
const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, '../../..'); // bootstrap-application root
const magratheaRoot = resolve(projectRoot, '..');   // magrathea-objectstorage root

const ARC42_DIR = resolve(magratheaRoot, 'docs/arc42');
const ARC42_TEMPLATE_PATH = resolve(ARC42_DIR, 'arc42-template.adoc');
const OUTPUT_DIR = resolve(projectRoot, 'src/main/resources/static/docs');

// Load @asciidoctor/core via CJS (avoids ESM resolution issues)
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// ──────────────────────────────────────────────────────────────────
// JSON tree builders (same as asciidoc-to-json.mjs)
// ──────────────────────────────────────────────────────────────────

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
      const content = node.getContent() || '';
      const style = node.getStyle() || '';
      return { type: 'codeblock', content, style };
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

if (!existsSync(ARC42_TEMPLATE_PATH)) {
  console.error(`ERROR: ARC42 template not found at ${ARC42_TEMPLATE_PATH}`);
  process.exit(1);
}

// Parse the document using loadFile() so includes resolve relative to
// the template's directory (docs/arc42/)
const doc = instance.loadFile(ARC42_TEMPLATE_PATH, {
  backend: 'html5',
  doctype: 'article',
  safe: 'safe',
  attributes: {
    'showtitle': true,
    'icons': 'font',
    'sectlinks': true,
    'revnumber': '1.0.0',
    'revdate': new Date().toISOString().slice(0, 10),
    'revremark': 'Generated from ARC42 template',
  },
});

// Build JSON tree
const jsonTree = blockToJson(doc);

// Wrap with metadata
const output = {
  title: 'Magrathea ObjectStore — Architecture Documentation (ARC42)',
  lang: 'en',
  document: jsonTree,
};

const outputPath = resolve(OUTPUT_DIR, 'arc42.json');
writeFileSync(outputPath, JSON.stringify(output, null, 2), 'utf8');
console.log(`✅ Generated ${outputPath}`);

console.log('Done.');
