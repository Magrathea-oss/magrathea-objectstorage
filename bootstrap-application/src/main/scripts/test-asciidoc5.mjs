import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Test various table formats
const tests = [
  // Format 1: pipes with blank line before
  `= Test\n== Section\n\n| A | B |\n|---|---|\n| 1 | 2 |\n`,
  // Format 2: pipes with spaces around
  `= Test\n== Section\n\n| A | B |\n| 1 | 2 |\n`,
  // Format 3: asciidoc table with title
  `= Test\n== Section\n\n.Page table\n| A | B |\n| 1 | 2 |\n`,
  // Format 4: table inside section
  `= Test\n\n== Section\n\n| A | B |\n| 1 | 2 |\n\n== Section2\n\nHello\n`,
  // Format 5: without separator line
  `= Test\n== Section\n\n| A | B |\n| 1 | 2 |\n`,
  // Format 6: with table prefix
  `= Test\n== Section\n\n[%header]\n| A | B |\n| 1 | 2 |\n`,
];

for (let i = 0; i < tests.length; i++) {
  console.log(`\n=== Test ${i+1} ===`);
  console.log('Input:', JSON.stringify(tests[i]));
  const doc = instance.load(tests[i], { backend: 'html5', doctype: 'article', safe: 'safe' });
  const blocks = doc.getBlocks();
  for (const block of blocks) {
    console.log('Block:', block.getContext());
    if (block.getContext() === 'section') {
      const subs = block.getBlocks();
      for (const sub of subs) {
        console.log('  Sub:', sub.getContext(), 'style:', sub.getStyle());
      }
    }
  }
}
