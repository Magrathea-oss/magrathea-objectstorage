/**
 * Convert all multilingual AsciiDoc user manual files to HTML.
 *
 * Reads docs/usermanual/index.{lang}.adoc for each language,
 * converts to HTML5 via @asciidoctor/core,
 * wraps in a glassmorphism-themed document matching the frontend style,
 * and writes to bootstrap-application/src/main/resources/static/docs/index.{lang}.html.
 *
 * Usage: node src/main/scripts/asciidoc-to-html.mjs
 * (no arguments — paths are resolved relative to the script location)
 */

import { existsSync, readFileSync, mkdirSync, writeFileSync, copyFileSync } from 'fs';
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

// Glassmorphism CSS matching frontend custom properties
const CSS_STYLE = `
    :root {
      color-scheme: dark;
      --bg-dark: #0f0f1a;
      --bg-card: rgba(255, 255, 255, 0.05);
      --text-primary: #e8e8f0;
      --text-secondary: #9898b0;
      --accent: #7c5cff;
      --accent-teal: #36d6b8;
      --font-mono: 'SF Mono', 'Fira Code', monospace;
    }
    body {
      margin: 0;
      padding: 0;
      background: var(--bg-dark);
      color: var(--text-primary);
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 0.95rem;
      line-height: 1.6;
    }
    .document {
      padding: 2rem;
      max-width: 900px;
      margin: 0 auto;
    }
    h1, h2, h3, h4 {
      color: var(--text-primary);
      margin-top: 1.5em;
      margin-bottom: 0.5em;
      font-weight: 600;
    }
    h1 { font-size: 1.8rem; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 0.5rem; }
    h2 { font-size: 1.4rem; }
    h3 { font-size: 1.15rem; }
    a { color: var(--accent-teal); text-decoration: none; }
    a:hover { text-decoration: underline; }
    code {
      background: var(--bg-card);
      padding: 0.15em 0.4em;
      border-radius: 4px;
      font-size: 0.9em;
      font-family: var(--font-mono);
    }
    pre {
      background: rgba(0,0,0,0.3);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 8px;
      padding: 1rem;
      overflow-x: auto;
    }
    table {
      border-collapse: collapse;
      width: 100%;
      margin: 1em 0;
    }
    th, td {
      border: 1px solid rgba(255,255,255,0.1);
      padding: 0.6rem 1rem;
      text-align: left;
    }
    th {
      background: rgba(255,255,255,0.06);
      font-weight: 600;
    }
    ul, ol { padding-left: 1.5em; }
    li { margin: 0.3em 0; }
    .paragraph { margin: 0.6em 0; }
    .sect1 { margin-bottom: 2em; }
    .sect2 { margin-bottom: 1.5em; }
    .admonitionblock {
      margin: 1em 0;
      padding: 1rem;
      border-radius: 8px;
      background: var(--bg-card);
      border-left: 4px solid var(--accent);
    }
    .admonitionblock .title { font-weight: 600; color: var(--accent); }
    .listingblock .title { font-weight: 600; color: var(--text-secondary); margin-bottom: 0.3em; }
    .colist { margin: 0.5em 0; }
`;

// Load @asciidoctor/core via CJS (avoids ESM resolution issues)
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Ensure output directory exists
mkdirSync(OUTPUT_DIR, { recursive: true });

for (const lang of LANGUAGES) {
  const adocPath = resolve(DOCS_DIR, `index.${lang}.adoc`);
  if (!existsSync(adocPath)) {
    console.warn(`WARNING: ${adocPath} not found, skipping`);
    continue;
  }

  const adocContent = readFileSync(adocPath, 'utf8');

  // Convert to HTML5
  const html = instance.convert(adocContent, {
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

  // Wrap in glassmorphism-themed document
  const fullHtml = `<!DOCTYPE html>
<html lang="${lang}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${TITLES[lang]}</title>
  <style>${CSS_STYLE}</style>
</head>
<body>
  <div class="document">
    ${html}
  </div>
</body>
</html>`;

  const outputPath = resolve(OUTPUT_DIR, `index.${lang}.html`);
  writeFileSync(outputPath, fullHtml, 'utf8');
  console.log(`✅ Generated ${outputPath}`);
}

// Copy index.en.html → index.html for backward compatibility
const enHtml = resolve(OUTPUT_DIR, 'index.en.html');
const indexHtml = resolve(OUTPUT_DIR, 'index.html');
if (existsSync(enHtml)) {
  copyFileSync(enHtml, indexHtml);
  console.log(`✅ Copied ${enHtml} → ${indexHtml}`);
}

console.log('Done.');
