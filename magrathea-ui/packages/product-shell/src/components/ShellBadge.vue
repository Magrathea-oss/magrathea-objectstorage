<script setup lang="ts">
withDefaults(defineProps<{
  tone?: 'neutral' | 'positive' | 'warning' | 'danger' | 'info'
  label: string
}>(), { tone: 'neutral' })
</script>

<template>
  <span class="shell-badge" :class="`shell-badge--${tone}`">
    <span class="shell-badge__dot" aria-hidden="true"></span>{{ label }}
  </span>
</template>

<style>
.shell-badge { display: inline-flex; align-items: center; gap: var(--shell-space-2); width: fit-content; padding: 0.25rem 0.55rem; color: var(--shell-text); background: var(--shell-surface-canvas); border: 1px solid var(--shell-border); border-radius: 999px; font-size: var(--shell-text-xs); font-weight: 800; line-height: 1.3; }
.shell-badge__dot { width: 0.45rem; height: 0.45rem; background: currentColor; border-radius: 50%; }
.shell-badge--positive { color: var(--shell-positive); background: var(--shell-positive-soft); border-color: color-mix(in srgb, var(--shell-positive) 35%, transparent); }
.shell-badge--warning { color: var(--shell-warning); background: var(--shell-warning-soft); border-color: color-mix(in srgb, var(--shell-warning) 35%, transparent); }
.shell-badge--danger { color: var(--shell-danger); background: var(--shell-danger-soft); border-color: color-mix(in srgb, var(--shell-danger) 35%, transparent); }
.shell-badge--info { color: var(--shell-info); background: var(--shell-info-soft); border-color: color-mix(in srgb, var(--shell-info) 35%, transparent); }
@media (forced-colors: active) { .shell-badge { border-color: currentColor; } }
</style>
