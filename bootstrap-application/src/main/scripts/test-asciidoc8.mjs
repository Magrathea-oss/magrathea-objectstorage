import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Simple |=== table test
const adoc = `= Test
== Section

|===
| Page | URL
| Dashboard | /
|===
`;

const doc = instance.load(adoc, { backend: 'html5', doctype: 'article', safe: 'safe' });

function dump(node, indent) {
  const ctx = node.getContext();
  console.log(indent + ctx + ' id=' + (node.getId() || ''));
  const blocks = node.getBlocks();
  if (blocks) {
    for (const b of blocks) dump(b, indent + '  ');
  }
}
dump(doc, '');
