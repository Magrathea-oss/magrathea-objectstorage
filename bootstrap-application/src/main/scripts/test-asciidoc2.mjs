import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Test 1: table with blank line separator
const adoc1 = `
= Test Doc

== Section

| Page | URL | Description |
|------|-----|-------------|
| Dashboard | / | Overview |
`;

const doc1 = instance.load(adoc1, { backend: 'html5', doctype: 'article', safe: 'safe' });
console.log('=== Test 1: table with blank lines ===');
const blocks1 = doc1.getBlocks();
for (const block of blocks1) {
  console.log('Block:', block.getContext(), 'style:', block.getStyle());
  if (block.getContext() === 'section') {
    for (const sub of block.getBlocks()) {
      console.log('  Sub:', sub.getContext(), 'style:', sub.getStyle());
      if (sub.getContext() === 'table') {
        console.log('  Table detected!');
        console.log('  HeadRows:', JSON.stringify(sub.getHeadRows()));
        console.log('  BodyRows:', JSON.stringify(sub.getBodyRows()));
      }
    }
  }
}

// Test 2: table without blank line separator
const adoc2 = `= Test Doc

== Section
| Page | URL |
|------|-----|
| Dashboard | / |
`;

const doc2 = instance.load(adoc2, { backend: 'html5', doctype: 'article', safe: 'safe' });
console.log('\n=== Test 2: table without blank line ===');
const blocks2 = doc2.getBlocks();
for (const block of blocks2) {
  console.log('Block:', block.getContext(), 'style:', block.getStyle());
  if (block.getContext() === 'section') {
    for (const sub of block.getBlocks()) {
      console.log('  Sub:', sub.getContext(), 'style:', sub.getStyle());
    }
  }
}

// Test 3: what does the original adoc look like?
console.log('\n=== Test 3: original en/index.adoc section with table ===');
const { readFileSync, existsSync } = await import('fs');
const { resolve } = await import('path');
const magratheaRoot = resolve(scriptDir, '../../..');
const adocPath = resolve(magratheaRoot, 'docs/usermanual/en/index.adoc');
const content = readFileSync(adocPath, 'utf8');
// Find the Pages section
const idx = content.indexOf('=== Pages');
if (idx >= 0) {
  const snippet = content.substring(idx, idx + 400);
  console.log('Snippet:');
  console.log(snippet);
}
