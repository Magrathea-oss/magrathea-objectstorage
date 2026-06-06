import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

const adoc = `
= Test Doc

== Section

| Page | URL | Description |
|------|-----|-------------|
| Dashboard | / | Overview |
| Buckets | /buckets | S3 buckets |

== List

* First item
* Second item
* Third item
`;

const doc = instance.load(adoc, { backend: 'html5', doctype: 'article', safe: 'safe' });
const blocks = doc.getBlocks();

for (const block of blocks) {
  console.log('Block context:', block.getContext(), 'style:', block.getStyle());
  
  if (block.getContext() === 'section') {
    const subblocks = block.getBlocks();
    for (const sub of subblocks) {
      console.log('  Sub context:', sub.getContext(), 'style:', sub.getStyle());
      
      if (sub.getContext() === 'table') {
        console.log('  HeadRows:', JSON.stringify(sub.getHeadRows()));
        console.log('  BodyRows:', JSON.stringify(sub.getBodyRows()));
        const body = sub.getBodyRows();
        if (body && body.length > 0) {
          const row = body[0];
          console.log('  Row type:', typeof row, Array.isArray(row));
          if (Array.isArray(row)) {
            for (const cell of row) {
              console.log('  Cell:', typeof cell, Object.keys(cell));
              if (typeof cell === 'object') {
                console.log('    text prop:', cell.text);
                console.log('    getText method:', typeof cell.getText);
                if (typeof cell.getText === 'function') console.log('    getText():', cell.getText());
              }
            }
          }
        }
      }
      
      if (sub.getContext() === 'ulist') {
        const items = sub.getItems();
        console.log('  Items count:', items ? items.length : 0);
        if (items && items.length > 0) {
          const item = items[0];
          console.log('  Item type:', typeof item, Object.keys(item));
          console.log('  item.text:', item.text);
          console.log('  item.getText:', typeof item.getText);
          if (typeof item.getText === 'function') console.log('  item.getText():', item.getText());
          console.log('  item.getBlocks():', item.getBlocks());
        }
      }
    }
  }
}
