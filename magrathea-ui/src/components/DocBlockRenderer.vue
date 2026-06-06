<template>
  <template v-for="(block, bi) in blocks" :key="bi">
    <p v-if="block.type === 'paragraph'" class="docs-paragraph" v-html="block.html"></p>

    <div v-else-if="block.type === 'section'" class="docs-subsection">
      <h3 v-if="block.title" :id="block.id" class="docs-subsection-title">{{ block.title }}</h3>
      <DocBlockRenderer :blocks="block.blocks || []" />
    </div>

    <div v-else-if="block.type === 'list'" class="docs-list-wrapper">
      <ul class="docs-list">
        <li v-for="(item, li) in (block.items || [])" :key="'l'+li" v-html="item"></li>
      </ul>
    </div>

    <div v-else-if="block.type === 'table'" class="docs-table-wrapper">
      <table class="docs-table">
        <thead v-if="block.headers && block.headers.length">
          <tr>
            <th v-for="(h, hi) in block.headers" :key="'h'+hi" v-html="h"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, ri) in (block.rows || [])" :key="'r'+ri">
            <td v-for="(cell, ci) in row" :key="'c'+ci" v-html="cell"></td>
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
</style>
