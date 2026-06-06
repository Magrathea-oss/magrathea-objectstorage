import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

const { readFileSync, existsSync } = await import('fs');
const { resolve } = await import('path');
const magratheaRoot = resolve(scriptDir, '../../..', '..'); // bootstrap-application/scripts -> ../../.. = bootstrap-application, then .. = magrathea-objectstorage
// Actually let me just use absolute path
const adocPath = '/home/paperboy/workspace/magrathea-objectstorage/docs/usermanual/en/index.adoc';
const content = readFileSync(adocPath, 'utf8');

const doc = instance.load(content, {
  backend: 'html5',
  doctype: 'article',
  safe: 'safe',
  attributes: {
    'showtitle': true,
    'icons': 'font',
    'sectlinks': true,
    'source-highlighter': 'highlight.js',
    'stylesheet!': '',
  },
});

function traverse(node, depth) {
  const ctx = node.getContext();
  const style = node.getStyle();
  const indent = '  '.repeat(depth);
  console.log(indent + ctx + ' style=' + style + ' id=' + (node.getId() || ''));
  
  if (ctx === 'table') {
    console.log(indent + '  HeadRows:', node.getHeadRows());
    console.log(indent + '  BodyRows:', node.getBodyRows());
    const body = node.getBodyRows();
    if (body && body.length > 0) {
      for (let ri = 0; ri < body.length; ri++) {
        const row = body[ri];
        console.log(indent + '  Row[' + ri + ']:', Array.isArray(row), row.length);
        for (let ci = 0; ci < row.length; ci++) {
          const cell = row[ci];
          console.log(indent + '    Cell[' + ci + ']:', typeof cell, Object.keys(cell));
          console.log(indent + '      text prop:', cell.text);
          console.log(indent + '      getText():', cell.getText());
          console.log(indent + '      getContent():', cell.getContent());
        }
      }
    }
  }
  
  if (LIST_CONTEXTS.has(ctx)) {
    const items = node.getItems();
    console.log(indent + '  Items:', items ? items.length : 0);
    if (items && items.length > 0) {
      for (let i = 0; i < items.length; i++) {
        const item = items[i];
        console.log(indent + '  Item[' + i + ']:', Object.keys(item));
        console.log(indent + '    text prop:', item.text);
        console.log(indent + '    getText():', item.getText());
        console.log(indent + '    blocks:', item.getBlocks());
      }
    }
  }
  
  const subBlocks = node.getBlocks();
  if (subBlocks) {
    for (const sub of subBlocks) {
      traverse(sub, depth + 1);
    }
  }
}

const LIST_CONTEXTS = new Set(['ulist', 'olist', 'colist', 'dlist']);

console.log('=== Full document tree ===');
traverse(doc, 0);
