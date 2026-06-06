/**
 * Convert test report Markdown file to structured JSON.
 *
 * Reads docs/test-report.md, parses Markdown structure (headings, paragraphs,
 * tables, lists), and writes to bootstrap-application/src/main/resources/static/docs/test-report.json.
 *
 * Usage: node src/main/scripts/markdown-to-json.mjs
 * (no arguments — paths are resolved relative to the script location)
 */

import { existsSync, readFileSync, mkdirSync, writeFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

// Resolve script directory for relative path calculation
const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, '../../..'); // bootstrap-application root
const magratheaRoot = resolve(projectRoot, '..');   // magrathea-objectstorage root

const TEST_REPORT_PATH = resolve(magratheaRoot, 'docs/test-report.md');
const OUTPUT_DIR = resolve(projectRoot, 'src/main/resources/static/docs');

// ──────────────────────────────────────────────────────────────────
// Simple Markdown parser (no dependencies)
// ──────────────────────────────────────────────────────────────────

/**
 * Escape HTML special characters for safe embedding.
 */
function escapeHtml(text) {
  return text
    .replace(/&/g, '&')
    .replace(/</g, '<')
    .replace(/>/g, '>')
    .replace(/"/g, '"')
    .replace(/'/g, '&#039;');
}

/**
 * Convert inline Markdown to HTML (bold, italic, code, links).
 */
function inlineToHtml(text) {
  let html = escapeHtml(text);
  // Bold: **text** or __text__
  html = html.replace(/(\*\*|__)(.*?)\1/g, '<strong>$2</strong>');
  // Italic: *text* or _text_
  html = html.replace(/(\*|_)(.*?)\1/g, '<em>$2</em>');
  // Code: `text`
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
  // Links: [text](url)
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');
  // Emoji/Unicode characters pass through
  return html;
}

/**
 * Parse a Markdown table row into cells.
 */
function parseTableRow(line) {
  const cells = line.split('|').slice(1, -1);
  return cells.map(c => c.trim());
}

/**
 * Check if a line is a table separator (e.g. |---|---|)
 */
function isTableSeparator(line) {
  return /^\s*\|?[\s:-]+(\|[\s:-]+)*\|?\s*$/.test(line);
}

/**
 * Parse Markdown content into a JSON document structure.
 *
 * @param {string} markdown - Raw Markdown content
 * @returns {object} JSON document tree
 */
function parseMarkdown(markdown) {
  const lines = markdown.split('\n');
  const sections = [];
  let currentSection = null;
  let inTable = false;
  let tableHeaders = null;
  let tableRows = [];
  let inList = false;
  let listItems = [];
  let listStyle = 'ulist';
  let paragraphLines = [];

  function flushParagraph() {
    if (paragraphLines.length === 0) return;
    const text = paragraphLines.join('\n');
    const block = { type: 'paragraph', html: inlineToHtml(text) };
    if (currentSection) {
      currentSection.blocks.push(block);
    } else {
      sections.push({ id: '', title: '', blocks: [block], level: 0 });
    }
    paragraphLines = [];
  }

  function flushTable() {
    if (tableRows.length === 0) return;
    const block = {
      type: 'table',
      headers: tableHeaders || [],
      rows: tableRows,
    };
    if (currentSection) {
      currentSection.blocks.push(block);
    }
    tableHeaders = null;
    tableRows = [];
    inTable = false;
  }

  function flushList() {
    if (listItems.length === 0) return;
    const block = {
      type: 'list',
      style: listStyle,
      items: listItems.map(text => ({
        text: inlineToHtml(text),
        blocks: [],
      })),
    };
    if (currentSection) {
      currentSection.blocks.push(block);
    }
    listItems = [];
    inList = false;
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    // Skip empty lines (except they may end a paragraph)
    if (trimmed === '') {
      flushParagraph();
      flushTable();
      flushList();
      continue;
    }

    // Heading (## or ###)
    const headingMatch = trimmed.match(/^(#{1,3})\s+(.*)$/);
    if (headingMatch) {
      flushParagraph();
      flushTable();
      flushList();

      const level = headingMatch[1].length;
      const title = headingMatch[2].trim();
      const id = '_' + title.toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/_+/g, '_').replace(/^_|_$/g, '');

      // Determine if this is a top-level section (##) or subsection (###)
      const section = {
        id,
        title,
        level,
        blocks: [],
      };

      // If ###, it's a subsection — find last ## section and add as nested
      if (level === 3 && currentSection && currentSection.level === 2) {
        // Add as a nested section block inside current section
        currentSection.blocks.push({
          type: 'section',
          id,
          title,
          level,
          blocks: [],
        });
      } else if (level === 3 && sections.length > 0) {
        // Find last section and nest
        const lastSection = sections[sections.length - 1];
        lastSection.blocks.push({
          type: 'section',
          id,
          title,
          level,
          blocks: [],
        });
      } else {
        // Top-level section
        sections.push(section);
      }

      // Update currentSection pointer
      if (level === 2) {
        currentSection = section;
      }
      continue;
    }

    // Table row
    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      flushParagraph();
      flushList();

      // Check if it's a separator
      if (isTableSeparator(trimmed)) {
        continue;
      }

      const cells = parseTableRow(trimmed);

      // If we're not in a table yet, start one
      if (!inTable) {
        tableHeaders = cells;
        tableRows = [];
        inTable = true;
      } else {
        tableRows.push(cells);
      }
      continue;
    }

    // List item
    const listMatch = trimmed.match(/^[-*]\s+(.*)$/);
    if (listMatch) {
      flushParagraph();
      flushTable();
      inList = true;
      listStyle = 'ulist';
      listItems.push(listMatch[1].trim());
      continue;
    }

    // Check for ordered list
    const orderedListMatch = trimmed.match(/^\d+[.)]\s+(.*)$/);
    if (orderedListMatch) {
      flushParagraph();
      flushTable();
      inList = true;
      listStyle = 'olist';
      listItems.push(orderedListMatch[1].trim());
      continue;
    }

    // Plain text — accumulate paragraph
    paragraphLines.push(trimmed);
  }

  // Flush remaining
  flushParagraph();
  flushTable();
  flushList();

  return { type: 'document', title: '', sections };
}

// ──────────────────────────────────────────────────────────────────
// Main
// ──────────────────────────────────────────────────────────────────

mkdirSync(OUTPUT_DIR, { recursive: true });

if (!existsSync(TEST_REPORT_PATH)) {
  console.error(`ERROR: Test report not found at ${TEST_REPORT_PATH}`);
  process.exit(1);
}

const markdownContent = readFileSync(TEST_REPORT_PATH, 'utf8');

// Extract title from first heading (# Title)
const titleMatch = markdownContent.match(/^#\s+(.*)$/m);
const title = titleMatch ? titleMatch[1].trim() : 'Magrathea ObjectStore — Test Report';

// Parse the document
const jsonTree = parseMarkdown(markdownContent);

// Wrap with metadata
const output = {
  title,
  lang: 'en',
  document: jsonTree,
};

const outputPath = resolve(OUTPUT_DIR, 'test-report.json');
writeFileSync(outputPath, JSON.stringify(output, null, 2), 'utf8');
console.log(`✅ Generated ${outputPath}`);

console.log('Done.');
