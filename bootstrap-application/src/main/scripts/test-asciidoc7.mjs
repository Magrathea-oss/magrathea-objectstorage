import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Test pipe table with various attributes
const adoc1 = `= Test
== Section

[%header]
|===
| Page | URL | Description
| Dashboard | / | Overview
|===
`;

const doc1 = instance.load(adoc1, { backend: 'html5', doctype: 'article', safe: 'safe' });
console.log('=== Test with |=== format ===');
for (const block of doc1.getBlocks()) {
  if (block.getContext() === 'section') {
    for (const sub of block.getBlocks()) {
      console.log('Sub:', sub.getContext(), 'style:', sub.getStyle());
      if (sub.getContext() === 'table') {
        console.log('HeadRows:', JSON.stringify(sub.getHeadRows()));
        console.log('BodyRows:', JSON.stringify(sub.getBodyRows()));
      }
    }
  }
}

// Now let's check if there's an option to enable pipe tables
// Look at the AsciiDoctor constructor options
console.log('\n=== Checking available options ===');
const opts = instance.defaultOptions();
console.log('Default options:', Object.keys(opts));
