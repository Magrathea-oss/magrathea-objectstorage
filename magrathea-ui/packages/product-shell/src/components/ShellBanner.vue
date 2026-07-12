<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  tone?: 'info' | 'positive' | 'warning' | 'danger'
  live?: 'off' | 'polite' | 'assertive'
}>(), { tone: 'info', live: 'off' })
</script>

<template>
  <section class="shell-banner" :class="`shell-banner--${tone}`" :aria-live="live" :role="tone === 'danger' ? 'alert' : undefined">
    <span class="shell-banner__icon" aria-hidden="true">{{ tone === 'positive' ? '✓' : tone === 'warning' ? '!' : tone === 'danger' ? '×' : 'i' }}</span>
    <div><h2>{{ title }}</h2><div class="shell-banner__content"><slot /></div></div>
    <div v-if="$slots.actions" class="shell-banner__actions"><slot name="actions" /></div>
  </section>
</template>

<style>
.shell-banner { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: start; gap: var(--shell-space-3); padding: var(--shell-space-4); color: var(--shell-info); background: var(--shell-info-soft); border: 1px solid color-mix(in srgb, var(--shell-info) 35%, transparent); border-left-width: 4px; border-radius: var(--shell-radius); }
.shell-banner--positive { color: var(--shell-positive); background: var(--shell-positive-soft); border-color: var(--shell-positive); }
.shell-banner--warning { color: var(--shell-warning); background: var(--shell-warning-soft); border-color: var(--shell-warning); }
.shell-banner--danger { color: var(--shell-danger); background: var(--shell-danger-soft); border-color: var(--shell-danger); }
.shell-banner__icon { width: 1.5rem; height: 1.5rem; display: grid; place-items: center; color: white; background: currentColor; border-radius: 50%; font-weight: 900; }
.shell-banner h2 { margin: 0; color: var(--shell-text); font-size: var(--shell-text-md); }
.shell-banner__content { margin-top: var(--shell-space-1); color: var(--shell-text); line-height: 1.5; }
.shell-banner__content > :first-child { margin-top: 0; }
.shell-banner__content > :last-child { margin-bottom: 0; }
.shell-banner__actions { grid-column: 2; display: flex; flex-wrap: wrap; gap: var(--shell-space-2); }
@media (min-width: 48rem) { .shell-banner { grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; } .shell-banner__actions { grid-column: auto; } }
@media (forced-colors: active) { .shell-banner { border-color: CanvasText; } }
</style>
