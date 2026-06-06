import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Test the exact format we want to use
const adoc = `= Test
== Section

|===
| Page | URL | Description
| Dashboard | / | Overview
| Buckets | /buckets | S3 buckets
|===
`;

const doc = instance.load(adoc, { backend: 'html5', doctype: 'article', safe: 'safe' });

function dump(node, indent) {
  const ctx = node.getContext();
  console.log(indent + ctx + ' style=' + node.getStyle());
  const blocks = node.getBlocks();
  if (blocks) {
    for (const b of blocks) dump(b, indent + '  ');
  }
  if (ctx === 'table') {
    console.log(indent + '  HeadRows:', JSON.stringify(node.getHeadRows()));
    console.log(indent + '  BodyRows:', JSON.stringify(node.getBodyRows()));
    const body = node.getBodyRows();
    if (body && body.length > 0) {
      for (let ri = 0; ri < body.length; ri++) {
        const row = body[ri];
        for (let ci = 0; ci < row.length; ci++) {
          const cell = row[ci];
          console.log(indent + '  Cell[' + ri + ',' + ci + ']: getText=' + cell.getText());
        }
      }
    }
    const head = node.getHeadRows();
    if (head && head.length > 0) {
      for (let ri = 0; ri < head.length; ri++) {
        const row = head[ri];
        for (let ci = 0; ci < row.length; ci++) {
          const cell = row[ci];
          console.log(indent + '  HEAD Cell[' + ri + ',' + ci + ']: getText=' + cell.getText());
        }
      }
    }
  }
}
dump(doc, '');
