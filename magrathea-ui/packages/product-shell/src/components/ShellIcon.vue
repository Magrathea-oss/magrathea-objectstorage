<script setup lang="ts">
import type { ProductNavigationIconId } from '../contracts'

withDefaults(defineProps<{
  name: ProductNavigationIconId | 'menu' | 'close' | 'chevron-down' | 'sun' | 'moon' | 'system' | 'check' | 'error' | 'empty'
  label?: string
}>(), { label: undefined })
</script>

<template>
  <svg class="shell-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"
    :aria-hidden="label ? undefined : 'true'" :aria-label="label" :role="label ? 'img' : undefined">
    <template v-if="name === 'overview'">
      <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>
    </template>
    <template v-else-if="name === 'activity'">
      <path d="M3 12h4l2.4-6 4.2 12 2.2-6H21"/>
    </template>
    <template v-else-if="name === 'layers'">
      <path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5M3 16l9 5 9-5"/>
    </template>
    <template v-else-if="name === 'checklist'">
      <path d="m4 6 1.5 1.5L8 5M11 6h9M4 12l1.5 1.5L8 11M11 12h9M4 18l1.5 1.5L8 17M11 18h9"/>
    </template>
    <template v-else-if="name === 'gauge'">
      <path d="M4.2 18a9 9 0 1 1 15.6 0"/><path d="m12 13 4-4M8 18h8"/>
    </template>
    <template v-else-if="name === 'operations'">
      <path d="M4 7h10M18 7h2M4 17h2M10 17h10"/><circle cx="16" cy="7" r="2"/><circle cx="8" cy="17" r="2"/>
    </template>
    <template v-else-if="name === 'settings'">
      <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6 1.7 1.7 0 0 0 10 3v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/>
    </template>
    <template v-else-if="name === 'documentation'">
      <path d="M5 3h10l4 4v14H5V3Z"/><path d="M15 3v5h4M8 12h8M8 16h8"/>
    </template>
    <template v-else-if="name === 'information'">
      <circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/>
    </template>
    <template v-else-if="name === 'warning' || name === 'error'">
      <path d="M10.3 4.3 2.8 18a2 2 0 0 0 1.8 3h14.8a2 2 0 0 0 1.8-3L13.7 4.3a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4M12 17h.01"/>
    </template>
    <template v-else-if="name === 'menu'"><path d="M4 7h16M4 12h16M4 17h16"/></template>
    <template v-else-if="name === 'close'"><path d="m6 6 12 12M18 6 6 18"/></template>
    <template v-else-if="name === 'chevron-down'"><path d="m7 9 5 5 5-5"/></template>
    <template v-else-if="name === 'sun'"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></template>
    <template v-else-if="name === 'moon'"><path d="M20.5 14.2A8.5 8.5 0 0 1 9.8 3.5 8.5 8.5 0 1 0 20.5 14.2Z"/></template>
    <template v-else-if="name === 'system'"><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8M12 17v4"/></template>
    <template v-else-if="name === 'check'"><path d="m5 12 4 4L19 6"/></template>
    <template v-else-if="name === 'empty'"><path d="M4 7h16v12H4zM8 3h8M9 11h6"/></template>
  </svg>
</template>

<style>
.shell-icon { width: 1.25rem; height: 1.25rem; display: block; flex: none; }
</style>
