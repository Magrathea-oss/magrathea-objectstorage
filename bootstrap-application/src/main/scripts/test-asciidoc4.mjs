import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Minimal table test
const adoc = `= Test
== Section
| A | B |
|---|---|
| 1 | 2 |
`;

const doc = instance.load(adoc, { backend: 'html5', doctype: 'article', safe: 'safe' });
const blocks = doc.getBlocks();
for (const block of blocks) {
  console.log('Block:', block.getContext());
  if (block.getContext() === 'section') {
    const subs = block.getBlocks();
    for (const sub of subs) {
      console.log('  Sub:', sub.getContext(), 'style:', sub.getStyle());
      console.log('  Sub blocks:', sub.getBlocks() ? sub.getBlocks().length : 0);
      console.log('  Sub content:', typeof sub.getContent === 'function' ? sub.getContent()?.substring(0,200) : 'N/A');
    }
  }
}
