<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import ShellIcon from './ShellIcon.vue'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  id?: string
  description?: string
  closeLabel?: string
}>(), { id: 'shell-dialog', description: undefined, closeLabel: 'Close dialog' })

const emit = defineEmits<{ 'update:open': [open: boolean] }>()
const panel = ref<HTMLElement>()
let returnTarget: HTMLElement | null = null

watch(() => props.open, async (open) => {
  if (open) {
    returnTarget = document.activeElement instanceof HTMLElement ? document.activeElement : null
    await nextTick()
    focusableElements()[0]?.focus()
  } else {
    returnTarget?.focus()
    returnTarget = null
  }
})

function close(): void { emit('update:open', false) }

function focusableElements(): HTMLElement[] {
  if (!panel.value) return []
  return Array.from(panel.value.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')).filter((element) => !element.hasAttribute('disabled'))
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') { event.preventDefault(); close(); return }
  if (event.key !== 'Tab') return
  const items = focusableElements()
  if (!items.length) { event.preventDefault(); panel.value?.focus(); return }
  const first = items[0]
  const last = items[items.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="shell-dialog-backdrop" @mousedown.self="close">
      <section ref="panel" class="shell-dialog" role="dialog" aria-modal="true" :aria-labelledby="`${id}-title`" :aria-describedby="description ? `${id}-description` : undefined" tabindex="-1" @keydown="onKeydown">
        <header class="shell-dialog__header">
          <div>
            <h2 :id="`${id}-title`">{{ title }}</h2>
            <p v-if="description" :id="`${id}-description`">{{ description }}</p>
          </div>
          <button class="shell-dialog__close" type="button" :aria-label="closeLabel" @click="close"><ShellIcon name="close" /></button>
        </header>
        <div class="shell-dialog__body"><slot /></div>
        <footer v-if="$slots.actions" class="shell-dialog__actions"><slot name="actions" :close="close" /></footer>
      </section>
    </div>
  </Teleport>
</template>

<style>
.shell-dialog-backdrop { position: fixed; z-index: 80; inset: 0; display: grid; place-items: center; padding: var(--shell-space-4); overflow-y: auto; background: rgb(9 26 27 / 68%); }
.shell-dialog { width: min(36rem, 100%); max-height: calc(100vh - 2rem); overflow-y: auto; color: var(--shell-text); background: var(--shell-surface); border: 1px solid var(--shell-border); border-radius: var(--shell-radius-lg); box-shadow: 0 2rem 5rem rgb(0 0 0 / 30%); }
.shell-dialog__header { display: flex; align-items: start; justify-content: space-between; gap: var(--shell-space-4); padding: var(--shell-space-5); border-bottom: 1px solid var(--shell-border); }
.shell-dialog__header h2 { margin: 0; font-size: var(--shell-text-lg); }
.shell-dialog__header p { margin: var(--shell-space-2) 0 0; color: var(--shell-text-muted); line-height: 1.5; }
.shell-dialog__close { width: 2.75rem; height: 2.75rem; display: grid; place-items: center; flex: none; color: var(--shell-text); background: transparent; border: 1px solid transparent; border-radius: 50%; cursor: pointer; }
.shell-dialog__close:hover { background: var(--shell-surface-canvas); }
.shell-dialog__body { padding: var(--shell-space-5); }
.shell-dialog__actions { display: flex; flex-wrap: wrap; justify-content: end; gap: var(--shell-space-3); padding: var(--shell-space-4) var(--shell-space-5); background: var(--shell-surface-canvas); border-top: 1px solid var(--shell-border); }
@media (max-width: 30rem) { .shell-dialog-backdrop { align-items: end; padding: 0; } .shell-dialog { width: 100%; max-height: 92vh; border-radius: var(--shell-radius-lg) var(--shell-radius-lg) 0 0; } }
</style>
