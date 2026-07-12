<script setup lang="ts">
import { computed, useId } from 'vue'
import ShellIcon from './ShellIcon.vue'
import ShellSkeleton from './ShellSkeleton.vue'
import type { RecoveryAction, StandardPageState } from '../application-state'

const props = withDefaults(defineProps<{
  state: Exclude<StandardPageState, 'ready'>
  heading?: string
  message?: string
  actionLabel?: string
}>(), { heading: undefined, message: undefined, actionLabel: undefined })

const emit = defineEmits<{ recover: [action: RecoveryAction] }>()

const defaults: Record<Exclude<StandardPageState, 'ready'>, { heading: string; message: string; action: RecoveryAction; actionLabel?: string }> = {
  loading: { heading: 'Loading', message: 'Content is being prepared.', action: 'none' },
  empty: { heading: 'Nothing here yet', message: 'There is no content to show.', action: 'none' },
  error: { heading: 'Something went wrong', message: 'The content could not be loaded.', action: 'retry', actionLabel: 'Try again' },
  offline: { heading: 'You are offline', message: 'Check your connection and try again.', action: 'retry', actionLabel: 'Try again' },
  unavailable: { heading: 'Temporarily unavailable', message: 'This content is not available right now.', action: 'retry', actionLabel: 'Try again' },
  unauthorized: { heading: 'Sign-in required', message: 'Sign in to continue.', action: 'sign-in', actionLabel: 'Sign in' },
  'not-found': { heading: 'Page not found', message: 'The page may have moved or no longer exists.', action: 'go-back', actionLabel: 'Go back' },
}

const detail = computed(() => defaults[props.state])
const headingId = `shell-page-state-${useId()}`
</script>

<template>
  <section class="shell-page-state" :class="`shell-page-state--${state}`" :aria-labelledby="headingId"
    :aria-live="state === 'error' || state === 'offline' || state === 'unavailable' || state === 'unauthorized' ? 'assertive' : 'polite'"
    :aria-busy="state === 'loading'">
    <template v-if="state === 'loading'">
      <h2 :id="headingId" class="shell-sr-only">{{ heading || detail.heading }}</h2>
      <ShellSkeleton :label="heading || detail.heading" :lines="4" />
    </template>
    <template v-else>
      <span class="shell-page-state__symbol" aria-hidden="true"><ShellIcon :name="state === 'empty' || state === 'not-found' ? 'empty' : 'error'" /></span>
      <h2 :id="headingId">{{ heading || detail.heading }}</h2>
      <p>{{ message || detail.message }}</p>
      <button v-if="detail.action !== 'none'" class="shell-button" type="button" @click="emit('recover', detail.action)">
        {{ actionLabel || detail.actionLabel }}
      </button>
    </template>
  </section>
</template>

<style>
.shell-page-state { width: 100%; min-height: 15rem; display: grid; place-items: center; align-content: center; gap: var(--shell-space-3); padding: clamp(1.5rem, 6vw, 4rem); text-align: center; color: var(--shell-text); background: var(--shell-surface); border: 1px solid var(--shell-border); border-radius: var(--shell-radius-lg); box-shadow: var(--shell-shadow); }
.shell-page-state h2, .shell-page-state p { margin: 0; }
.shell-page-state p { max-width: 48ch; color: var(--shell-text-muted); line-height: 1.55; }
.shell-page-state__symbol { width: 3rem; height: 3rem; display: grid; place-items: center; color: var(--shell-brand); background: var(--shell-brand-soft); border: 1px solid color-mix(in srgb, var(--shell-brand) 30%, var(--shell-border)); border-radius: 50%; }
.shell-page-state--error .shell-page-state__symbol, .shell-page-state--offline .shell-page-state__symbol, .shell-page-state--unavailable .shell-page-state__symbol { color: var(--shell-danger); background: var(--shell-danger-soft); }
</style>
