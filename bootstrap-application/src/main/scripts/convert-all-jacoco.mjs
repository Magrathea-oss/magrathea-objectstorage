/**
 * Convert ALL JaCoCo HTML files to JSON recursively, preserving directory structure.
 *
 * Usage:
 *   node bootstrap-application/src/main/scripts/convert-all-jacoco.mjs
 *
 * Reads HTML from bootstrap-application/target/site/jacoco-aggregate/<module>/
 * Writes JSON to   bootstrap-application/src/main/resources/static/docs/jacoco-json/<module>/
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync, readdirSync, statSync } from 'fs';
import { resolve, dirname, extname, basename, join, relative } from 'path';
import { fileURLToPath } from 'url';
import { convert } from './lib/converter.js';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const magratheaRoot = resolve(scriptDir, '../../../..');

const SOURCE_BASE = resolve(magratheaRoot, 'bootstrap-application/target/site/jacoco-aggregate');
const OUTPUT_BASE = resolve(magratheaRoot, 'bootstrap-application/src/main/resources/static/docs/jacoco-json');

// ──────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────

/**
 * Recursively find all files matching a pattern.
 * @param {string} dir - Directory to search
 * @param {RegExp} pattern - File pattern
 * @returns {string[]} Array of relative paths (relative to dir)
 */
function findFiles(dir, pattern) {
  const results = [];
  function walk(currentDir, relPath) {
    let entries;
    try {
      entries = readdirSync(currentDir, { withFileTypes: true });
    } catch { return; }
    for (const entry of entries) {
      const fullPath = join(currentDir, entry.name);
      const entryRelPath = relPath ? join(relPath, entry.name) : entry.name;
      if (entry.isDirectory()) {
        walk(fullPath, entryRelPath);
      } else if (entry.isFile() && pattern.test(entry.name)) {
        results.push(entryRelPath);
      }
    }
  }
  walk(dir, '');
  return results;
}

// ──────────────────────────────────────────────────────────────
// Main
// ──────────────────────────────────────────────────────────────

console.log('=== JaCoCo HTML → JSON Converter ===');
console.log(`Source:  ${SOURCE_BASE}`);
console.log(`Output:  ${OUTPUT_BASE}`);
console.log('');

// Get all module directories
const moduleDirs = readdirSync(SOURCE_BASE, { withFileTypes: true })
  .filter(entry => entry.isDirectory())
  .map(entry => entry.name);

let totalConverted = 0;
let totalFailed = 0;

for (const moduleName of moduleDirs) {
  const moduleSourceDir = join(SOURCE_BASE, moduleName);
  const moduleOutputDir = join(OUTPUT_BASE, moduleName);

  // Find all HTML files in this module (recursive)
  const htmlFiles = findFiles(moduleSourceDir, /\.html$/i);
  if (htmlFiles.length === 0) continue;

  console.log(`[${moduleName}] ${htmlFiles.length} HTML files`);

  for (const relPath of htmlFiles) {
    const inputFile = join(moduleSourceDir, relPath);
    const outputRelPath = relPath.replace(/\.html$/i, '.json');
    const outputFile = join(moduleOutputDir, outputRelPath);

    try {
      // Ensure output directory exists
      mkdirSync(dirname(outputFile), { recursive: true });

      const htmlContent = readFileSync(inputFile, 'utf8');

      // Convert to JSON
      const jsonTree = convert(htmlContent, 'html', {
        imageBasePath: '/docs/',
        cssBasePath: '/docs/',
        fixHtmlLinks: true,
      });

      // Wrap with metadata
      const output = {
        title: jsonTree.title || basename(inputFile),
        lang: jsonTree.lang || 'en',
        document: jsonTree,
      };

      writeFileSync(outputFile, JSON.stringify(output, null, 2), 'utf8');
      totalConverted++;
    } catch (err) {
      console.error(`  ❌ FAILED: ${inputFile} — ${err.message}`);
      totalFailed++;
    }
  }
}

console.log('');
console.log(`✅ Total converted: ${totalConverted}`);
console.log(`❌ Total failed:    ${totalFailed}`);

// ──────────────────────────────────────────────────────────────
// Copy to target/classes for runtime access
// ──────────────────────────────────────────────────────────────

const targetDir = resolve(magratheaRoot, 'bootstrap-application/target/classes/static/docs/jacoco-json');
console.log(`\nCopying to target: ${targetDir}`);

function copyRecursive(src, dst) {
  if (!existsSync(src)) return;
  const entries = readdirSync(src, { withFileTypes: true });
  mkdirSync(dst, { recursive: true });
  for (const entry of entries) {
    const srcPath = join(src, entry.name);
    const dstPath = join(dst, entry.name);
    if (entry.isDirectory()) {
      copyRecursive(srcPath, dstPath);
    } else if (entry.isFile()) {
      writeFileSync(dstPath, readFileSync(srcPath));
    }
  }
}

copyRecursive(OUTPUT_BASE, targetDir);
console.log('✅ Copied to target/classes');
