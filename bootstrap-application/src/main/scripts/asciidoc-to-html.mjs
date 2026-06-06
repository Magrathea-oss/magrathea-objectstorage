import { readFileSync, mkdirSync, writeFileSync } from 'fs';
import { resolve } from 'path';
import { createRequire } from 'module';

// Resolve input and output paths from CLI args
const inputFile = resolve(process.argv[2]);
const outputDir = resolve(process.argv[3]);

// Read AsciiDoc source
const source = readFileSync(inputFile, 'utf-8');

// Load @asciidoctor/core via CJS (avoid ESM resolution issues)
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Convert to HTML5
const html = instance.convert(source, {
  backend: 'html5',
  safe: 'safe',
  attributes: { 'stylesheet!' : '' }
});

// Ensure output directory exists
mkdirSync(outputDir, { recursive: true });

// Write output file (same basename, .html extension)
const outputFile = resolve(outputDir, 'index.html');
writeFileSync(outputFile, html, 'utf-8');
console.log(`Written ${outputFile}`);
