import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Test with [%header] option
const adoc = `= Test
== Section

[%header]
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
    const head = node.getHeadRows();
    const body = node.getBodyRows();
    console.log(indent + '  HeadRows length:', head ? head.length : 0);
    console.log(indent + '  BodyRows length:', body ? body.length : 0);
    if (head && head.length > 0) {
      for (let ri = 0; ri < head.length; ri++) {
        const row = head[ri];
        for (let ci = 0; ci < row.length; ci++) {
          const cell = row[ci];
          console.log(indent + '  HEAD Cell[' + ri + ',' + ci + ']: ' + cell.getText());
        }
      }
    }
    if (body && body.length > 0) {
      for (let ri = 0; ri < body.length; ri++) {
        const row = body[ri];
        for (let ci = 0; ci < row.length; ci++) {
          const cell = row[ci];
          console.log(indent + '  BODY Cell[' + ri + ',' + ci + ']: ' + cell.getText());
        }
      }
    }
  }
}
dump(doc, '');
