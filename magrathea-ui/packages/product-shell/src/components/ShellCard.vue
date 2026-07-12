<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  eyebrow?: string
  interactive?: boolean
}>(), { title: undefined, eyebrow: undefined, interactive: false })
</script>

<template>
  <article class="shell-card" :class="{ 'shell-card--interactive': interactive }">
    <header v-if="title || eyebrow || $slots.actions" class="shell-card__header">
      <div>
        <p v-if="eyebrow" class="shell-card__eyebrow">{{ eyebrow }}</p>
        <h2 v-if="title">{{ title }}</h2>
      </div>
      <div v-if="$slots.actions" class="shell-card__actions"><slot name="actions" /></div>
    </header>
    <div class="shell-card__body"><slot /></div>
    <footer v-if="$slots.footer" class="shell-card__footer"><slot name="footer" /></footer>
  </article>
</template>

<style>
.shell-card { min-width: 0; overflow: hidden; background: var(--shell-surface-raised); border: 1px solid var(--shell-border); border-radius: var(--shell-radius-lg); box-shadow: var(--shell-shadow); }
.shell-card--interactive { transition: border-color var(--shell-motion-fast), transform var(--shell-motion-fast); }
.shell-card--interactive:hover { border-color: var(--shell-brand); transform: translateY(-2px); }
.shell-card__header { display: flex; align-items: start; justify-content: space-between; gap: var(--shell-space-3); padding: var(--shell-space-5) var(--shell-space-5) 0; }
.shell-card__header h2 { margin: 0; font-size: var(--shell-text-lg); }
.shell-card__eyebrow { margin: 0 0 var(--shell-space-1); color: var(--shell-brand); font-size: var(--shell-text-xs); font-weight: 800; letter-spacing: 0.1em; text-transform: uppercase; }
.shell-card__body { padding: var(--shell-space-5); line-height: 1.6; }
.shell-card__body > :first-child { margin-top: 0; }
.shell-card__body > :last-child { margin-bottom: 0; }
.shell-card__actions { flex: none; }
.shell-card__footer { padding: var(--shell-space-4) var(--shell-space-5); background: color-mix(in srgb, var(--shell-surface-canvas) 65%, transparent); border-top: 1px solid var(--shell-border); }
</style>
