<script setup lang="ts">
import { useId } from 'vue'
import ShellIcon from './ShellIcon.vue'

withDefaults(defineProps<{
  title: string
  summary?: string
  open?: boolean
}>(), { summary: undefined, open: false })

const contentId = `shell-disclosure-${useId()}`
</script>

<template>
  <details class="shell-disclosure" :open="open">
    <summary :aria-controls="contentId">
      <span class="shell-disclosure__heading"><strong>{{ title }}</strong><span v-if="summary">{{ summary }}</span></span>
      <ShellIcon name="chevron-down" />
    </summary>
    <div :id="contentId" class="shell-disclosure__content"><slot /></div>
  </details>
</template>

<style>
.shell-disclosure { min-width: 0; color: var(--shell-text); background: var(--shell-surface-raised); border: 1px solid var(--shell-border); border-radius: var(--shell-radius); }
.shell-disclosure summary { min-height: 2.75rem; display: flex; align-items: center; justify-content: space-between; gap: var(--shell-space-4); padding: var(--shell-space-4); cursor: pointer; list-style: none; }
.shell-disclosure summary::-webkit-details-marker { display: none; }
.shell-disclosure__heading { min-width: 0; display: grid; gap: var(--shell-space-1); }
.shell-disclosure__heading strong { font-size: var(--shell-text-sm); }
.shell-disclosure__heading span { color: var(--shell-text-muted); font-size: var(--shell-text-sm); line-height: 1.45; }
.shell-disclosure summary .shell-icon { transition: transform var(--shell-motion-fast); }
.shell-disclosure[open] summary .shell-icon { transform: rotate(180deg); }
.shell-disclosure__content { padding: 0 var(--shell-space-4) var(--shell-space-4); color: var(--shell-text-muted); line-height: 1.6; }
.shell-disclosure__content > :first-child { margin-top: 0; }
.shell-disclosure__content > :last-child { margin-bottom: 0; }
@media (prefers-reduced-motion: reduce) { .shell-disclosure summary .shell-icon { transition: none; } }
@media (forced-colors: active) { .shell-disclosure { border-color: CanvasText; } }
</style>
