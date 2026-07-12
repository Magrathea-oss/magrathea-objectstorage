<script setup lang="ts">
import type { ShellTableColumn } from '../presentation'

withDefaults(defineProps<{
  caption: string
  columns: readonly ShellTableColumn[]
  rows: readonly Record<string, unknown>[]
  rowKey?: string
}>(), { rowKey: 'id' })
</script>

<template>
  <div class="shell-table-wrap">
    <table class="shell-table">
      <caption>{{ caption }}</caption>
      <thead><tr><th v-for="column in columns" :key="column.key" scope="col" :class="`shell-table--${column.align || 'start'}`">{{ column.label }}</th></tr></thead>
      <tbody>
        <tr v-for="(row, rowIndex) in rows" :key="String(row[rowKey] ?? rowIndex)">
          <td v-for="column in columns" :key="column.key" :data-label="column.label" :class="`shell-table--${column.align || 'start'}`">
            <slot :name="`cell-${column.key}`" :row="row" :value="row[column.key]">{{ row[column.key] }}</slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style>
.shell-table-wrap { width: 100%; overflow-x: auto; background: var(--shell-surface); border: 1px solid var(--shell-border); border-radius: var(--shell-radius); }
.shell-table { width: 100%; border-collapse: collapse; font-size: var(--shell-text-sm); }
.shell-table caption { padding: var(--shell-space-4); text-align: left; font-size: var(--shell-text-md); font-weight: 800; }
.shell-table :where(th, td) { padding: var(--shell-space-3) var(--shell-space-4); text-align: left; vertical-align: middle; border-top: 1px solid var(--shell-border); }
.shell-table th { color: var(--shell-text-muted); background: var(--shell-surface-canvas); font-size: var(--shell-text-xs); letter-spacing: 0.06em; text-transform: uppercase; }
.shell-table tbody tr:hover { background: color-mix(in srgb, var(--shell-brand-soft) 35%, transparent); }
.shell-table--center { text-align: center !important; }
.shell-table--end { text-align: right !important; }
@media (max-width: 35rem) {
  .shell-table-wrap { overflow: visible; border: 0; background: transparent; }
  .shell-table, .shell-table tbody, .shell-table tr, .shell-table td { display: block; }
  .shell-table thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); }
  .shell-table caption { background: var(--shell-surface); border: 1px solid var(--shell-border); border-radius: var(--shell-radius) var(--shell-radius) 0 0; }
  .shell-table tr { margin-bottom: var(--shell-space-3); padding: var(--shell-space-2); background: var(--shell-surface); border: 1px solid var(--shell-border); border-radius: var(--shell-radius); }
  .shell-table td { display: grid; grid-template-columns: minmax(6rem, 38%) minmax(0, 1fr); gap: var(--shell-space-3); border: 0; text-align: left !important; }
  .shell-table td::before { content: attr(data-label); color: var(--shell-text-muted); font-size: var(--shell-text-xs); font-weight: 800; letter-spacing: 0.04em; text-transform: uppercase; }
}
</style>
