/**
 * Unified document converter — entry point.
 *
 * Converts HTML, Markdown, or AsciiDoc to a unified JSON format
 * compatible with DocBlockRenderer.
 *
 * Usage:
 *   import { convert } from './lib/converter.js';
 *   const json = convert(input, 'markdown', { imageBasePath: '/docs/' });
 *
 * @module lib/converter
 */

import { readFileSync, existsSync } from 'fs';
import { parseHtml } from './parser-html.js';
import { parseMarkdown } from './parser-markdown.js';
import { parseAsciiDoc, parseAsciiDocFile } from './parser-asciidoc.js';
import { applyPostProcessors } from './post-processor.js';

// ──────────────────────────────────────────────────────────────
// Format detection
// ──────────────────────────────────────────────────────────────

/**
 * Detect format from file extension or content.
 * @param {string} input - The content or a path hint
 * @param {string} [formatHint] - Optional explicit format
 * @returns {string} 'html', 'markdown', or 'asciidoc'
 * @throws {Error} if format cannot be determined
 */
export function detectFormat(input, formatHint) {
  if (formatHint) {
    const lower = formatHint.toLowerCase();
    if (lower === 'html' || lower === 'htm') return 'html';
    if (lower === 'md' || lower === 'markdown' || lower === 'mdown') return 'markdown';
    if (lower === 'adoc' || lower === 'asciidoc' || lower === 'asciidoctor') return 'asciidoc';
  }

  // Detect from content
  if (typeof input !== 'string') throw new Error('Cannot detect format: input must be a string');

  // Check for HTML markers
  if (input.includes('<!DOCTYPE html') || input.includes('<html') || input.includes('<head>') || input.includes('<body')) {
    return 'html';
  }

  // Check for AsciiDoc markers (=== is a level-2 heading in AsciiDoc)
  if (input.includes('= ') || input.includes('===')) {
    // Check for typical AsciiDoc patterns: = heading, :attribute:
    const firstLine = input.trim().split('\n')[0] || '';
    if (firstLine.startsWith('=') || input.includes('::') || input.includes(';')) {
      return 'asciidoc';
    }
  }

  // Default to markdown (most common for documentation)
  if (input.includes('##') || input.includes('```') || input.includes('|')) {
    return 'markdown';
  }

  // Fallback — try to detect from first heading pattern
  const firstLine = input.trim().split('\n')[0] || '';
  if (firstLine.startsWith('# ')) return 'markdown';
  if (firstLine.startsWith('= ')) return 'asciidoc';
  if (firstLine.startsWith('<')) return 'html';

  // Default
  return 'markdown';
}

// ──────────────────────────────────────────────────────────────
// Main convert function
// ──────────────────────────────────────────────────────────────

/**
 * Convert input content to unified JSON format.
 *
 * @param {string} input - The content to convert (HTML, Markdown, or AsciiDoc)
 * @param {string} [format] - Explicit format ('html', 'markdown', 'asciidoc').
 *                             If omitted, format is auto-detected.
 * @param {object} [options] - Additional options
 * @param {string} [options.imageBasePath='/'] - Base path for relative image URLs
 * @param {string} [options.cssBasePath='/'] - Base path for relative CSS URLs
 * @param {object} [options.attributes] - Additional AsciiDoctor attributes (for AsciiDoc only)
 * @param {object} [options.metadata] - Override metadata (title, lang)
 * @returns {object} Unified JSON document tree
 */
export function convert(input, format, options = {}) {
  if (!input || typeof input !== 'string') {
    throw new Error('Input must be a non-empty string');
  }

  const detectedFormat = format || detectFormat(input);
  let jsonTree;

  switch (detectedFormat) {
    case 'asciidoc':
      jsonTree = parseAsciiDoc(input, { attributes: options.attributes });
      break;
    case 'markdown':
      jsonTree = parseMarkdown(input);
      break;
    case 'html':
      jsonTree = parseHtml(input);
      break;
    default:
      throw new Error(`Unknown format: ${detectedFormat}`);
  }

  // Apply post-processors
  applyPostProcessors(jsonTree, options);

  // Wrap with metadata if provided
  if (options.metadata) {
    const { title, lang } = options.metadata;
    if (title !== undefined) jsonTree.title = title;
    if (lang !== undefined) jsonTree.lang = lang;
  }

  return jsonTree;
}

/**
/**
 * Convert a file by reading its content and delegating to convert().
 * For AsciiDoc files with includes, uses parseAsciiDocFile() which
 * resolves includes relative to the file's directory.
 *
 * @param {string} filePath - Path to the file
 * @param {string} [format] - Explicit format (auto-detected from extension if omitted)
 * @param {object} [options] - Additional options passed to convert()
 * @returns {object} Unified JSON document tree
 */
export function convertFile(filePath, format, options = {}) {
  if (!existsSync(filePath)) {
    throw new Error(`File not found: ${filePath}`);
  }

  const detectedFormat = format || detectFromExtension(filePath);

  // For AsciiDoc files with includes, use the file-based parser
  if (detectedFormat === 'asciidoc') {
    const jsonTree = parseAsciiDocFile(filePath, { attributes: options.attributes });
    applyPostProcessors(jsonTree, options);
    return jsonTree;
  }

  // For HTML and Markdown, read and convert
  const content = readFileSync(filePath, 'utf8');
  return convert(content, detectedFormat, options);
}

/**
 * Detect format from file extension.
 * @param {string} filePath
 * @returns {string}
 */
function detectFromExtension(filePath) {
  const ext = filePath.split('.').pop()?.toLowerCase() || '';
  if (ext === 'html' || ext === 'htm') return 'html';
  if (ext === 'md' || ext === 'markdown' || ext === 'mdown') return 'markdown';
  if (ext === 'adoc' || ext === 'asciidoc') return 'asciidoc';
  return 'markdown';
}

// Re-export parsers for direct use
export { parseHtml } from './parser-html.js';
export { parseMarkdown } from './parser-markdown.js';
export { parseAsciiDoc } from './parser-asciidoc.js';
