/**
 * Convert test report Markdown file to structured JSON.
 *
 * Reads docs/test-report.md, uses the unified converter library (lib/converter.js),
 * and writes to bootstrap-application/src/main/resources/static/docs/test-report.json.
 *
 * Usage: node src/main/scripts/markdown-to-json.mjs
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

const TEST_REPORT_PATH = resolve(magratheaRoot, 'docs/test-report.md');
const OUTPUT_DIR = resolve(projectRoot, 'src/main/resources/static/docs');

// ──────────────────────────────────────────────────────────────────
// Main
// ──────────────────────────────────────────────────────────────────

mkdirSync(OUTPUT_DIR, { recursive: true });

if (!existsSync(TEST_REPORT_PATH)) {
  console.error(`ERROR: Test report not found at ${TEST_REPORT_PATH}`);
  process.exit(1);
}

const markdownContent = readFileSync(TEST_REPORT_PATH, 'utf8');

// Convert using the unified library
const jsonTree = convert(markdownContent, 'markdown', {
  imageBasePath: '/docs/',
});

// Extract title from first heading (# Title)
const titleMatch = markdownContent.match(/^#\s+(.*)$/m);
const title = titleMatch ? titleMatch[1].trim() : 'Magrathea ObjectStore — Test Report';

// Wrap with metadata
const output = {
  title,
  lang: 'en',
  document: jsonTree,
};

const outputPath = resolve(OUTPUT_DIR, 'test-report.json');
writeFileSync(outputPath, JSON.stringify(output, null, 2), 'utf8');
console.log(`✅ Generated ${outputPath}`);

console.log('Done.');
