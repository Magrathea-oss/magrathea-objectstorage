<script setup lang="ts">
import { useId } from 'vue'

defineProps<{ text: string }>()
const tooltipId = `shell-tooltip-${useId()}`
</script>

<template>
  <span class="shell-tooltip">
    <span class="shell-tooltip__trigger" tabindex="0" :aria-describedby="tooltipId"><slot /></span>
    <span :id="tooltipId" class="shell-tooltip__content" role="tooltip">{{ text }}</span>
  </span>
</template>

<style>
.shell-tooltip { position: relative; display: inline-flex; }
.shell-tooltip__trigger { min-width: 2.75rem; min-height: 2.75rem; display: inline-flex; align-items: center; justify-content: center; border-radius: var(--shell-radius-sm); }
.shell-tooltip__content { position: absolute; z-index: 40; left: 50%; bottom: calc(100% + var(--shell-space-2)); width: max-content; max-width: min(18rem, 80vw); padding: var(--shell-space-2) var(--shell-space-3); color: var(--shell-inverse-text); background: var(--shell-inverse-surface); border: 1px solid var(--shell-inverse-border); border-radius: var(--shell-radius-sm); box-shadow: var(--shell-shadow); font-size: var(--shell-text-xs); line-height: 1.4; opacity: 0; pointer-events: none; transform: translate(-50%, var(--shell-space-1)); transition: opacity var(--shell-motion-fast), transform var(--shell-motion-fast); }
.shell-tooltip:hover .shell-tooltip__content, .shell-tooltip:focus-within .shell-tooltip__content { opacity: 1; transform: translate(-50%, 0); }
@media (hover: none) { .shell-tooltip__content { display: none; } }
@media (prefers-reduced-motion: reduce) { .shell-tooltip__content { transition: none; } }
@media (forced-colors: active) { .shell-tooltip__content { border-color: CanvasText; } }
</style>
