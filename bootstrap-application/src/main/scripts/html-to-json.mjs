/**
 * Convert HTML file(s) to unified JSON format.
 *
 * Usage:
 *   node src/main/scripts/html-to-json.mjs --input <path> [--output <path>]
 *
 * If --input is a file, converts that file.
 * If --input is a glob pattern, converts all matching files.
 * If --output is provided, writes JSON there (or a directory for multiple files).
 * Otherwise prints to stdout.
 *
 * Uses the unified converter library (lib/converter.js).
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs';
import { resolve, dirname, extname, basename } from 'path';
import { fileURLToPath } from 'url';
import { convert } from './lib/converter.js';

const scriptDir = dirname(fileURLToPath(import.meta.url));

// ──────────────────────────────────────────────────────────────
// Argument parsing
// ──────────────────────────────────────────────────────────────

function parseArgs() {
  const args = process.argv.slice(2);
  let inputPath = null;
  let outputPath = null;
  let fixLinks = false;

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--input' && i + 1 < args.length) {
      inputPath = args[++i];
    } else if (args[i] === '--output' && i + 1 < args.length) {
      outputPath = args[++i];
    } else if (args[i] === '--fix-links') {
      fixLinks = true;
    }
  }

  if (!inputPath) {
    console.error('Usage: node html-to-json.mjs --input <path> [--output <path>] [--fix-links]');
    process.exit(1);
  }

  return { inputPath, outputPath, fixLinks };
}

// ──────────────────────────────────────────────────────────────
// Main
// ──────────────────────────────────────────────────────────────

const { inputPath, outputPath, fixLinks } = parseArgs();

// Resolve relative to magrathea-objectstorage root
const magratheaRoot = resolve(scriptDir, '../../../..');
const resolvedInput = inputPath.includes('*')
  ? inputPath
  : inputPath.startsWith('/')
    ? inputPath
    : resolve(magratheaRoot, inputPath);

// Check for glob pattern
if (resolvedInput.includes('*')) {
  console.error('Glob patterns not supported in this simple implementation.');
  console.error('Please provide a direct file path.');
  process.exit(1);
}

if (!existsSync(resolvedInput)) {
  console.error(`ERROR: File not found: ${resolvedInput}`);
  process.exit(1);
}

const htmlContent = readFileSync(resolvedInput, 'utf8');

// Convert to JSON
const jsonTree = convert(htmlContent, 'html', {
  imageBasePath: '/docs/',
  cssBasePath: '/docs/',
  fixHtmlLinks: fixLinks,
});

// Wrap with metadata
const output = {
  title: jsonTree.title || basename(resolvedInput),
  lang: jsonTree.lang || 'en',
  document: jsonTree,
};

if (outputPath) {
  const resolvedOutput = outputPath.startsWith('/')
    ? outputPath
    : resolve(magratheaRoot, outputPath);

  // Ensure output directory exists
  mkdirSync(dirname(resolvedOutput), { recursive: true });

  writeFileSync(resolvedOutput, JSON.stringify(output, null, 2), 'utf8');
  console.log(`✅ Generated ${resolvedOutput}`);
} else {
  console.log(JSON.stringify(output, null, 2));
}

console.log('Done.');
