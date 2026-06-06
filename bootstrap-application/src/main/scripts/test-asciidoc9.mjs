import { createRequire } from 'module';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const asciidoctorFactory = require('@asciidoctor/core');
const instance = asciidoctorFactory();

// Test with the actual adoc file
const { readFileSync } = await import('fs');
const content = readFileSync('/home/paperboy/workspace/magrathea-objectstorage/docs/usermanual/en/index.adoc', 'utf8');

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
  
  // Check if paragraph has pipe table content
  if (ctx === 'paragraph') {
    const content = node.getContent();
    if (content && content.includes('|')) {
      console.log(indent + '  CONTENT (first 200):', content.substring(0, 200));
      // Also check the raw lines
      const lines = node.getLines();
      if (lines) console.log(indent + '  LINES:', lines);
    }
  }
  
  if (ctx === 'ulist' || ctx === 'olist') {
    const items = node.getItems();
    if (items && items.length > 0) {
      for (let i = 0; i < items.length; i++) {
        const item = items[i];
        console.log(indent + '  Item[' + i + ']:');
        console.log(indent + '    text:', JSON.stringify(item.text));
        console.log(indent + '    getText():', JSON.stringify(item.getText()));
        console.log(indent + '    getContent():', typeof item.getContent === 'function' ? JSON.stringify(item.getContent()) : 'N/A');
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

console.log('=== Full document tree ===');
traverse(doc, 0);
