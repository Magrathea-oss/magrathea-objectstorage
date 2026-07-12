<template>
  <template v-for="(block, bi) in blocks" :key="bi">
    <p v-if="block.type === 'paragraph'" class="docs-paragraph" v-html="block.html"></p>

    <div v-else-if="block.type === 'code'" class="docs-code" v-html="block.html"></div>

    <div v-else-if="block.type === 'section'" class="docs-subsection">
      <h3 v-if="block.title" :id="block.id" class="docs-subsection-title">{{ block.title }}</h3>
      <DocBlockRenderer :blocks="block.blocks || []" />
    </div>

    <div v-else-if="block.type === 'list'" class="docs-list-wrapper">
      <ul class="docs-list">
        <li v-for="(item, li) in (block.items || [])" :key="'l'+li" >
          <template v-if="typeof item === 'string'">{{ item }}</template>
          <span v-else-if="item.text !== undefined" v-html="item.text"></span>
          <DocBlockRenderer v-else-if="item.blocks" :blocks="item.blocks" />
          <template v-else>{{ item }}</template>
        </li>
      </ul>
    </div>

    <div v-else-if="block.type === 'image'" class="docs-image">
      <img :src="block.src || extractSrc(block.html)" :alt="block.alt || ''" class="docs-image-img" />
    </div>

    <div v-else-if="block.type === 'table'" class="docs-table-wrapper">
      <table class="docs-table">
        <thead v-if="block.headers && block.headers.length">
          <tr>
            <th v-for="(h, hi) in block.headers" :key="'h'+hi"
              :colspan="h.colspan || 1"
              :rowspan="h.rowspan || 1"
              v-html="h.text || h"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, ri) in (block.rows || [])" :key="'r'+ri">
            <td v-for="(cell, ci) in row" :key="'c'+ci"
              :colspan="cell.colspan || 1"
              :rowspan="cell.rowspan || 1"
              v-html="cell.text || cell"></td>
          </tr>
        </tbody>
      </table>
    </div>
  </template>
</template>

<script>
export default {
  name: 'DocBlockRenderer'
}
</script>

<script setup>
defineProps({
  blocks: {
    type: Array,
    default: () => []
  }
})

function extractSrc(html) {
  if (!html) return '';
  const match = html.match(/src="([^"]+)"/);
  return match ? match[1] : '';
}
</script>

<style scoped>
.docs-subsection {
  margin-left: 1rem;
}

.docs-subsection-title {
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 1em;
  margin-bottom: 0.5em;
}

.docs-paragraph {
  margin: 0.6em 0;
  color: var(--text-secondary);
}

.docs-table-wrapper {
  margin: 1em 0;
  overflow-x: auto;
}

.docs-table {
  border-collapse: collapse;
  width: 100%;
}

.docs-table th,
.docs-table td {
  border: 1px solid rgba(255,255,255,0.1);
  padding: 0.6rem 1rem;
  text-align: left;
}

.docs-table th {
  background: rgba(255,255,255,0.06);
  font-weight: 600;
  color: var(--text-primary);
}

.docs-table td {
  color: var(--text-secondary);
}

.docs-list {
  padding-left: 1.5em;
  margin: 0.6em 0;
}

.docs-list li {
  margin: 0.3em 0;
  color: var(--text-secondary);
}

.docs-code {
  margin: 1em 0;
  overflow-x: auto;
  background: var(--bg-card);
  border: 1px solid var(--border-glass);
  border-radius: 8px;
  padding: 1rem;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 0.85rem;
  line-height: 1.5;
}

.docs-image {
  margin: 1em 0;
  text-align: center;
}
.docs-image-img {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.3);
}
</style>
