<script setup lang="ts">
withDefaults(defineProps<{
  id: string
  label: string
  hint?: string
  error?: string
  required?: boolean
}>(), { hint: undefined, error: undefined, required: false })
</script>

<template>
  <div class="shell-field">
    <label :for="id">{{ label }}<span v-if="required" aria-hidden="true"> *</span></label>
    <slot :id="id" :describedby="[hint ? `${id}-hint` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ') || undefined" :invalid="Boolean(error)" />
    <p v-if="hint" :id="`${id}-hint`" class="shell-field__hint">{{ hint }}</p>
    <p v-if="error" :id="`${id}-error`" class="shell-field__error" role="alert">{{ error }}</p>
  </div>
</template>
