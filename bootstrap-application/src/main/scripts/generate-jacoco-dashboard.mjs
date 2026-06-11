#!/usr/bin/env node

/**
 * generate-jacoco-dashboard.mjs
 *
 * Generates a JaCoCo coverage dashboard JSON that includes ALL modules.
 * Reads per-module index JSON files (converted from JaCoCo HTML reports)
 * and merges them into a single dashboard with a summary section.
 *
 * Usage:
 *   node generate-jacoco-dashboard.mjs \
 *     --static-dir bootstrap-application/src/main/resources/static/docs/jacoco-json \
 *     --output dashboard.json
 */

import fs from 'fs';
import path from 'path';

const args = process.argv.slice(2);
let staticDir = null;
let outputPath = null;

for (let i = 0; i < args.length; i++) {
  if (args[i] === '--static-dir' && i + 1 < args.length) staticDir = args[++i];
  if (args[i] === '--output' && i + 1 < args.length) outputPath = args[++i];
}

if (!staticDir || !outputPath) {
  console.error('Usage: node generate-jacoco-dashboard.mjs --static-dir <dir> --output <path>');
  process.exit(1);
}

// Known module index files (actual module names from JaCoCo HTML conversion)
const MODULE_INDEX_FILES = [
  { name: 'storage-engine-domain', file: 'storage-engine-domain/index.json' },
  { name: 'storage-engine-application', file: 'storage-engine-application/index.json' },
  { name: 'object-store-reactive-repository-application', file: 'object-store-reactive-repository-application/index.json' },
];

function loadModule(name, relPath) {
  const fullPath = path.join(staticDir, relPath);
  if (!fs.existsSync(fullPath)) {
    console.warn(`WARNING: Module index not found: ${fullPath}`);
    return null;
  }
  const raw = fs.readFileSync(fullPath, 'utf-8');
  return JSON.parse(raw);
}

function extractTable(data) {
  const sections = data?.document?.sections ?? [];
  for (const s of sections) {
    for (const b of (s.blocks ?? [])) {
      if (b.type === 'table') return b;
    }
  }
  return null;
}

// Load all modules
const modules = [];
for (const mi of MODULE_INDEX_FILES) {
  const data = loadModule(mi.name, mi.file);
  if (data) {
    modules.push({ name: mi.name, data });
    console.log(`Loaded: ${mi.name}`);
  }
}

if (modules.length === 0) {
  console.log('WARNING: No module data loaded. Cannot generate dashboard.');
  process.exit(0);
}

// Build dashboard
const dashboard = {
  title: 'JaCoCo Coverage Dashboard',
  lang: 'it',
  document: {
    type: 'document',
    title: 'JaCoCo Coverage Dashboard',
    sections: [],
  },
};

// --- Section 1: Summary ---
const summaryHeaders = [
  'Module',
  'Missed Instructions',
  'Cov.',
  'Missed Branches',
  'Cov.',
  'Missed',
  'Cxty',
  'Missed',
  'Lines',
  'Missed',
  'Methods',
  'Missed',
  'Classes',
];

const summaryRows = [summaryHeaders];
for (const mod of modules) {
  const table = extractTable(mod.data);
  if (table && table.rows.length >= 2) {
    const totalRow = table.rows[1]; // second row is "Total"
    const row = [...totalRow];
    row[0] = mod.name;
    summaryRows.push(row);
  }
}

dashboard.document.sections.push({
  type: 'section',
  id: '_coverage_summary',
  title: 'Coverage Summary',
  level: 1,
  blocks: [
    {
      type: 'table',
      headers: summaryHeaders,
      rows: summaryRows,
    },
  ],
});

// --- Sections 2+: Per-module coverage tables ---
for (const mod of modules) {
  const sections = mod.data.document?.sections ?? [];
  for (const s of sections) {
    dashboard.document.sections.push(s);
  }
}

// Write output
const json = JSON.stringify(dashboard, null, 2);
fs.writeFileSync(outputPath, json, 'utf-8');
console.log(`Dashboard written to: ${outputPath}`);
console.log(`Sections: ${dashboard.document.sections.length}`);
console.log(`Summary modules: ${modules.length}`);
