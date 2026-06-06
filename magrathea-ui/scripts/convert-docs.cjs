#!/usr/bin/env node
/**
 * Convert AsciiDoc user manual files to HTML.
 * Generates docs/index.{lang}.html in the bootstrap-application static resources.
 * 
 * Usage: node scripts/convert-docs.js
 */

const asciidoctor = require('@asciidoctor/core')()
const fs = require('fs')
const path = require('path')

const PROJECT_ROOT = path.resolve(__dirname, '../../')
const DOCS_DIR = path.resolve(PROJECT_ROOT, 'docs/usermanual')
const OUTPUT_DIR = path.resolve(PROJECT_ROOT, 'bootstrap-application/src/main/resources/static/docs')

const LANGUAGES = ['en', 'it', 'es', 'de', 'cn']

// Ensure output directory exists
if (!fs.existsSync(OUTPUT_DIR)) {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true })
}

for (const lang of LANGUAGES) {
  const adocPath = path.join(DOCS_DIR, `index.${lang}.adoc`)
  if (!fs.existsSync(adocPath)) {
    console.warn(`WARNING: ${adocPath} not found, skipping`)
    continue
  }

  const adocContent = fs.readFileSync(adocPath, 'utf8')

  // Convert to HTML — use div backend so we get only the content div,
  // not a full HTML document, which is cleaner for embedding inside the Vue app
  const html = asciidoctor.convert(adocContent, {
    doctype: 'article',
    attributes: {
      'showtitle': true,
      'icons': 'font',
      'sectlinks': true,
      'source-highlighter': 'highlight.js'
    }
  })

  // Wrap in a minimal HTML document with glassmorphism-friendly styling
  const fullHtml = `<!DOCTYPE html>
<html lang="${lang}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Magrathea ObjectStore — ${lang === 'en' ? 'User Manual' : lang === 'it' ? 'Manuale Utente' : lang === 'es' ? 'Manual de Usuario' : lang === 'de' ? 'Benutzerhandbuch' : '用户手册'}</title>
  <style>
    :root {
      color-scheme: dark;
      font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
    }
    body {
      margin: 0;
      padding: 0;
      background: transparent;
      color: #e0e0e0;
      font-size: 0.95rem;
      line-height: 1.6;
    }
    .document {
      padding: 2rem;
      max-width: 900px;
      margin: 0 auto;
    }
    h1, h2, h3, h4 {
      color: #f0f0f0;
      margin-top: 1.5em;
      margin-bottom: 0.5em;
      font-weight: 600;
    }
    h1 { font-size: 1.8rem; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 0.5rem; }
    h2 { font-size: 1.4rem; }
    h3 { font-size: 1.15rem; }
    a { color: #7ec8e3; text-decoration: none; }
    a:hover { text-decoration: underline; }
    code {
      background: rgba(255,255,255,0.08);
      padding: 0.15em 0.4em;
      border-radius: 4px;
      font-size: 0.9em;
      font-family: 'Fira Code', 'Cascadia Code', monospace;
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
  </style>
</head>
<body>
  <div class="document">
    ${html}
  </div>
</body>
</html>`

  const outputPath = path.join(OUTPUT_DIR, `index.${lang}.html`)
  fs.writeFileSync(outputPath, fullHtml, 'utf8')
  console.log(`✅ Generated ${outputPath}`)
}

// Also copy index.en.html → index.html for backward compatibility
const enHtml = path.join(OUTPUT_DIR, 'index.en.html')
const indexHtml = path.join(OUTPUT_DIR, 'index.html')
if (fs.existsSync(enHtml)) {
  fs.copyFileSync(enHtml, indexHtml)
  console.log(`✅ Copied ${enHtml} → ${indexHtml}`)
}

console.log('Done.')
