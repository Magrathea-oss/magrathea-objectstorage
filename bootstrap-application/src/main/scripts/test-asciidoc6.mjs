import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Test the "|===" table format
const adoc = `= Test
== Section

|===
| A | B
| 1 | 2
|===
`;

const doc = instance.load(adoc, { backend: 'html5', doctype: 'article', safe: 'safe' });
const blocks = doc.getBlocks();
console.log('=== Test with |=== format ===');
for (const block of blocks) {
  console.log('Block:', block.getContext());
  if (block.getContext() === 'section') {
    const subs = block.getBlocks();
    for (const sub of subs) {
      console.log('  Sub:', sub.getContext(), 'style:', sub.getStyle());
      if (sub.getContext() === 'table') {
        console.log('  HeadRows:', JSON.stringify(sub.getHeadRows()));
        console.log('  BodyRows:', JSON.stringify(sub.getBodyRows()));
      }
    }
  }
}
