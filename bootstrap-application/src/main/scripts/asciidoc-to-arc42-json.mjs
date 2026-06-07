/**
 * Convert ARC42 architecture documentation template to structured JSON.
 *
 * Reads docs/arc42/arc42-template.adoc which includes 12 sections from docs/arc42/src/*.adoc,
 * uses the unified converter library (lib/converter.js) with file-based parsing,
 * and writes to bootstrap-application/src/main/resources/static/docs/arc42.json.
 *
 * Usage: node src/main/scripts/asciidoc-to-arc42-json.mjs
 * (no arguments — paths are resolved relative to the script location)
 */

import { existsSync, mkdirSync, writeFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import { convertFile } from './lib/converter.js';

// Resolve script directory for relative path calculation
const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, '../../..'); // bootstrap-application root
const magratheaRoot = resolve(projectRoot, '..');   // magrathea-objectstorage root

const ARC42_DIR = resolve(magratheaRoot, 'docs/arc42');
const ARC42_TEMPLATE_PATH = resolve(ARC42_DIR, 'arc42-template.adoc');
const OUTPUT_DIR = resolve(projectRoot, 'src/main/resources/static/docs');

// ──────────────────────────────────────────────────────────────────
// Main
// ──────────────────────────────────────────────────────────────────

mkdirSync(OUTPUT_DIR, { recursive: true });

if (!existsSync(ARC42_TEMPLATE_PATH)) {
  console.error(`ERROR: ARC42 template not found at ${ARC42_TEMPLATE_PATH}`);
  process.exit(1);
}

// Convert using the unified library's file-based parser
const jsonTree = convertFile(ARC42_TEMPLATE_PATH, 'asciidoc', {
  imageBasePath: '/docs/',
  attributes: {
    'showtitle': true,
    'icons': 'font',
    'sectlinks': true,
    'revnumber': '1.0.0',
    'revdate': new Date().toISOString().slice(0, 10),
    'revremark': 'Generated from ARC42 template',
  },
});

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
