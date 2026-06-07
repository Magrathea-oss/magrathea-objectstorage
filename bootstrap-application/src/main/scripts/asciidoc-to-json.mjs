/**
 * Convert all multilingual AsciiDoc user manual files to structured JSON.
 *
 * Reads docs/usermanual/{lang}/index.adoc for each language,
 * uses the unified converter library (lib/converter.js),
 * and writes to bootstrap-application/src/main/resources/static/docs/index.{lang}.json.
 *
 * Usage: node src/main/scripts/asciidoc-to-json.mjs
 * (no arguments — paths are resolved relative to the script location)
 */

import { existsSync, readFileSync, mkdirSync, writeFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import { convert } from './lib/converter.js';

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

  // Convert using the unified library
  const jsonTree = convert(adocContent, 'asciidoc', {
    imageBasePath: '/docs/',
    attributes: {
      'showtitle': true,
      'icons': 'font',
      'sectlinks': true,
      'source-highlighter': 'highlight.js',
      'stylesheet!': '',
    },
  });

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
