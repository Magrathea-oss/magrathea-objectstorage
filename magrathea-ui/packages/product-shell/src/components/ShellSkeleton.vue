<script setup lang="ts">
withDefaults(defineProps<{ lines?: number; label?: string }>(), { lines: 3, label: 'Loading content' })
</script>

<template>
  <div class="shell-skeleton" role="status" :aria-label="label">
    <span class="shell-sr-only">{{ label }}</span>
    <span v-for="line in lines" :key="line" class="shell-skeleton__line" :style="{ width: `${100 - ((line - 1) % 3) * 14}%` }"></span>
  </div>
</template>

<style>
.shell-skeleton { display: grid; gap: var(--shell-space-3); width: 100%; overflow: hidden; }
.shell-skeleton__line { height: 0.9rem; display: block; border-radius: 999px; background: linear-gradient(90deg, var(--shell-brand-soft) 20%, var(--shell-surface-canvas) 45%, var(--shell-brand-soft) 70%); background-size: 240% 100%; animation: shell-shimmer 1.4s ease-in-out infinite; }
@keyframes shell-shimmer { from { background-position: 100% 0; } to { background-position: -100% 0; } }
@media (prefers-reduced-motion: reduce) { .shell-skeleton__line { animation: none; background: var(--shell-brand-soft); } }
@media (forced-colors: active) { .shell-skeleton__line { border: 1px solid CanvasText; } }
</style>
