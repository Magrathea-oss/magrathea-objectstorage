/**
 * Markdown parser for unified document JSON conversion.
 *
 * Parses Markdown text and produces a DocBlockRenderer-compatible JSON tree.
 *
 * Block types produced:
 *   paragraph, section, list, table, image, code
 *
 * This parser has no external dependencies — it uses a simple state-machine
 * approach that handles headings, paragraphs, lists, tables, code blocks,
 * and inline formatting (bold, italic, code, links).
 *
 * @module lib/parser-markdown
 */

// ──────────────────────────────────────────────────────────────
// Inline HTML conversion
// ──────────────────────────────────────────────────────────────

/**
 * Escape HTML special characters for safe embedding.
 */
function escapeHtml(text) {
  if (!text) return '';
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
  if (!text) return '';
  let html = escapeHtml(text);
  // Bold: **text** or __text__
  html = html.replace(/(\*\*|__)(.*?)\1/g, '<strong>$2</strong>');
  // Italic: *text* or _text_
  html = html.replace(/(\*|_)(.*?)\1/g, '<em>$2</em>');
  // Code: `text`
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
  // Links: [text](url)
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');
  // Images: ![alt](url)
  html = html.replace(
    /!\[([^\]]*)\]\(([^)]+)\)/g,
    (match, alt, src) => `<img src="${src}" alt="${alt}">`
  );
  return html;
}

// ──────────────────────────────────────────────────────────────
// Table parsing
// ──────────────────────────────────────────────────────────────

function parseTableRow(line) {
  const cells = line.split('|').slice(1, -1);
  return cells.map(c => c.trim());
}

function isTableSeparator(line) {
  return /^\s*\|?[\s:-]+(\|[\s:-]+)*\|?\s*$/.test(line);
}

// ──────────────────────────────────────────────────────────────
// Code block detection
// ──────────────────────────────────────────────────────────────

const FENCED_CODE_RE = /^(```|~~~)\s*(\w*)\s*$/;

// ──────────────────────────────────────────────────────────────
// Main parser
// ──────────────────────────────────────────────────────────────

/**
 * Parse Markdown content into a unified JSON document tree.
 *
 * @param {string} markdown - Raw Markdown content
 * @returns {object} JSON document tree
 */
export function parseMarkdown(markdown) {
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
  let inCodeFence = false;
  let codeFenceType = '';
  let codeLines = [];

  function flushParagraph() {
    if (paragraphLines.length === 0) return;
    const text = paragraphLines.join('\n');
    const block = { type: 'paragraph', html: inlineToHtml(text) };
    if (currentSection) {
      currentSection.blocks.push(block);
    } else {
      // Orphan paragraph — create implicit section
      sections.push({ id: '_intro', title: '', level: 0, blocks: [block] });
    }
    paragraphLines = [];
  }

  function flushTable() {
    if (tableRows.length === 0 && !tableHeaders) return;
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

  function flushCodeFence() {
    if (codeLines.length === 0) return;
    const content = codeLines.join('\n');
    const escapedContent = escapeHtml(content);
    const lang = codeFenceType || '';
    const block = {
      type: 'code',
      html: `<pre><code${lang ? ` class="${lang}"` : ''}>${escapedContent}</code></pre>`,
    };
    if (currentSection) {
      currentSection.blocks.push(block);
    }
    codeLines = [];
    inCodeFence = false;
    codeFenceType = '';
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    // Skip empty lines (except they may end a paragraph)
    if (trimmed === '') {
      if (!inCodeFence) {
        flushParagraph();
        flushTable();
        flushList();
      } else {
        codeLines.push('');
      }
      continue;
    }

    // Code fence detection
    const fenceMatch = trimmed.match(FENCED_CODE_RE);
    if (fenceMatch) {
      if (inCodeFence) {
        // End of code fence
        flushCodeFence();
      } else {
        // Start of code fence
        flushParagraph();
        flushTable();
        flushList();
        inCodeFence = true;
        codeFenceType = fenceMatch[2];
      }
      continue;
    }

    // Inside code fence — accumulate
    if (inCodeFence) {
      codeLines.push(line);
      continue;
    }

    // Heading (## or ### or #)
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

      // If ###, it's a subsection — nest inside current ## section
      if (level === 3 && currentSection && currentSection.level === 2) {
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
      if (level === 2 || level === 1) {
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

    // Unordered list item
    const listMatch = trimmed.match(/^[-*]\s+(.*)$/);
    if (listMatch) {
      flushParagraph();
      flushTable();
      inList = true;
      listStyle = 'ulist';
      listItems.push(listMatch[1].trim());
      continue;
    }

    // Ordered list item
    const orderedListMatch = trimmed.match(/^\d+[.)]\s+(.*)$/);
    if (orderedListMatch) {
      flushParagraph();
      flushTable();
      inList = true;
      listStyle = 'olist';
      listItems.push(orderedListMatch[1].trim());
      continue;
    }

    // Image line (standalone)
    const imageMatch = trimmed.match(/^!\[([^\]]*)\]\(([^)]+)\)\s*$/);
    if (imageMatch) {
      flushParagraph();
      flushTable();
      flushList();
      const alt = imageMatch[1];
      const src = imageMatch[2];
      const block = {
        type: 'image',
        html: `<img src="${src}" alt="${alt}">`,
      };
      if (currentSection) {
        currentSection.blocks.push(block);
      }
      continue;
    }

    // Plain text — accumulate paragraph
    paragraphLines.push(trimmed);
  }

  // Flush remaining
  flushParagraph();
  flushTable();
  flushList();
  flushCodeFence();

  return { type: 'document', title: '', sections };
}
