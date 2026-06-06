/**
 * Convert Architecture Decision Record (ADR) Markdown files to structured JSON.
 *
 * Reads docs/adr/*.md, parses Markdown structure (headings, paragraphs,
 * tables, lists), and writes each to bootstrap-application/src/main/resources/static/docs/adr/<nnnn>.json.
 *
 * Usage: node src/main/scripts/adr-to-json.mjs
 * (no arguments — paths are resolved relative to the script location)
 */

import { existsSync, readdirSync, readFileSync, mkdirSync, writeFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

// Resolve script directory for relative path calculation
const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, '../../..'); // bootstrap-application root
const magratheaRoot = resolve(projectRoot, '..');   // magrathea-objectstorage root

const ADR_DIR = resolve(magratheaRoot, 'docs/adr');
const OUTPUT_DIR = resolve(projectRoot, 'src/main/resources/static/docs/adr');

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

      const section = {
        id,
        title,
        level,
        blocks: [],
      };

      // If ###, it's a subsection — find last ## section and add as nested
      if (level === 3 && currentSection && currentSection.level === 2) {
        currentSection.blocks.push({
          type: 'section',
          id,
          title,
          level,
          blocks: [],
        });
      } else if (level === 3 && sections.length > 0) {
        const lastSection = sections[sections.length - 1];
        lastSection.blocks.push({
          type: 'section',
          id,
          title,
          level,
          blocks: [],
        });
      } else {
        sections.push(section);
      }

      if (level === 2) {
        currentSection = section;
      }
      continue;
    }

    // Table row
    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      flushParagraph();
      flushList();

      if (isTableSeparator(trimmed)) {
        continue;
      }

      const cells = parseTableRow(trimmed);

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

    // Ordered list
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

  return { type: 'document', title: extractAdrTitle(markdown), sections };
}

/**
 * Extract ADR number from filename like "0001-multi-module-per-bounded-context.md"
 */
function extractAdrNumber(filename) {
  const match = filename.match(/^(\d{1,4})-/);
  return match ? match[1] : null;
}

/**
 * Extract ADR title from markdown content.
 * Uses the first heading (H1 or H2) found in the document.
 */
function extractAdrTitle(markdown) {
  const headingMatch = markdown.match(/^(#{1,2})\s+(.*)$/m);
  if (headingMatch) return headingMatch[2].trim();
  return 'Architecture Decision Record';
}

/**
 * Extract ADR status from markdown content.
 * Looks for "## Status" followed by a paragraph line.
 */
function extractAdrStatus(markdown) {
  const lines = markdown.split('\n');
  for (let i = 0; i < lines.length; i++) {
    if (/^##\s+Status/i.test(lines[i].trim())) {
      // Look at next non-empty line
      for (let j = i + 1; j < lines.length; j++) {
        const trimmed = lines[j].trim();
        if (trimmed === '') continue;
        if (trimmed.startsWith('#')) break;
        return trimmed;
      }
    }
  }
  return null;
}

// ──────────────────────────────────────────────────────────────────
// Main
// ──────────────────────────────────────────────────────────────────

mkdirSync(OUTPUT_DIR, { recursive: true });

if (!existsSync(ADR_DIR)) {
  console.error(`ERROR: ADR directory not found at ${ADR_DIR}`);
  process.exit(1);
}

const files = readdirSync(ADR_DIR)
  .filter(f => f.endsWith('.md'))
  .sort();

let count = 0;

for (const filename of files) {
  const adrNumber = extractAdrNumber(filename);
  if (!adrNumber) {
    console.warn(`⚠️  Skipping ${filename}: cannot extract ADR number`);
    continue;
  }

  const filePath = resolve(ADR_DIR, filename);
  const markdownContent = readFileSync(filePath, 'utf8');

  const title = extractAdrTitle(markdownContent);
  const status = extractAdrStatus(markdownContent);

  // Parse the document
  const jsonTree = parseMarkdown(markdownContent);

  // Wrap with metadata
  const output = {
    adr: parseInt(adrNumber, 10),
    title,
    status,
    lang: 'en',
    document: jsonTree,
  };

  const outputPath = resolve(OUTPUT_DIR, `${adrNumber}.json`);
  writeFileSync(outputPath, JSON.stringify(output, null, 2), 'utf8');
  console.log(`✅ Generated ${outputPath}`);
  count++;
}

console.log(`Done. ${count} ADRs converted.`);
