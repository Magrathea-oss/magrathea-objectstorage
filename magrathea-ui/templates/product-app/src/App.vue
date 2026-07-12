<script setup lang="ts">
import { ref } from 'vue'
import {
  defaultProductIdentity,
  ProductShell,
  ShellBadge,
  ShellBanner,
  ShellCard,
  ShellDataTable,
  ShellDialog,
  ShellField,
  ShellPageState,
  productShellArtifact,
  type ProductNavigationEntry,
  type ShellTableColumn,
} from '@magrathea/product-shell'

const dialogOpen = ref(false)
const displayName = ref('')
const navigation: ProductNavigationEntry[] = [
  { id: 'overview', labelKey: 'Overview', route: '/', order: 10 },
  { id: 'activity', labelKey: 'Activity', route: '/activity', order: 20 },
  { id: 'settings', labelKey: 'Settings', route: '/settings', order: 30 },
]
const columns: ShellTableColumn[] = [
  { key: 'name', label: 'Workspace' },
  { key: 'owner', label: 'Owner' },
  { key: 'status', label: 'Status' },
  { key: 'updated', label: 'Last updated', align: 'end' },
]
const rows = [
  { id: 'north', name: 'North star', owner: 'Product team', status: 'Ready', updated: 'Today' },
  { id: 'field', name: 'Field notes', owner: 'Research team', status: 'Review', updated: 'Yesterday' },
  { id: 'launch', name: 'Launch plan', owner: 'Delivery team', status: 'Ready', updated: 'Monday' },
]
</script>

<template>
  <ProductShell :identity="defaultProductIdentity" :navigation="navigation" active-route="/" page-title="A clear view of your work"
    page-description="The Magrathea shell gives every product a calm, accessible foundation while extensions supply their own behavior."
    :breadcrumbs="[{ label: 'Home', href: '/' }, { label: 'Overview' }]">
    <template #page-actions>
      <button class="shell-button" type="button" @click="dialogOpen = true">Create workspace</button>
    </template>

    <div class="template-layout shell-stack">
      <ShellBanner title="Welcome to Magrathea">
        This copyable template demonstrates the default product-neutral shell. Replace the sample content by registering your own extension.
      </ShellBanner>

      <section class="template-metrics" aria-label="Workspace summary">
        <ShellCard eyebrow="In progress" title="12 active">
          <p>Workspaces with updates this week.</p>
          <template #footer><ShellBadge label="On track" tone="positive" /></template>
        </ShellCard>
        <ShellCard eyebrow="Needs attention" title="3 reviews">
          <p>Items waiting for a teammate.</p>
          <template #footer><ShellBadge label="Review" tone="warning" /></template>
        </ShellCard>
        <ShellCard eyebrow="Latest release" title="Version 0.1">
          <p>{{ productShellArtifact.name }}</p>
          <template #footer><ShellBadge label="Available" tone="info" /></template>
        </ShellCard>
      </section>

      <ShellDataTable caption="Recent workspaces" :columns="columns" :rows="rows">
        <template #cell-status="{ value }"><ShellBadge :label="String(value)" :tone="value === 'Ready' ? 'positive' : 'warning'" /></template>
      </ShellDataTable>

      <section class="template-states" aria-labelledby="states-title">
        <div><p class="template-kicker">Consistent feedback</p><h2 id="states-title">States that explain what happens next</h2></div>
        <ShellPageState state="empty" heading="Your next idea starts here" message="Create a workspace when you are ready." />
      </section>
    </div>
  </ProductShell>

  <ShellDialog id="create-workspace" v-model:open="dialogOpen" title="Create a workspace" description="Use a concise name that teammates will recognize.">
    <form id="workspace-form" class="shell-stack" @submit.prevent="dialogOpen = false">
      <ShellField id="workspace-name" label="Workspace name" hint="For example, Quarterly planning" required>
        <template #default="field"><input :id="field.id" v-model="displayName" :aria-describedby="field.describedby" required autocomplete="off"></template>
      </ShellField>
    </form>
    <template #actions="{ close }">
      <button class="shell-button shell-button--secondary" type="button" @click="close">Cancel</button>
      <button class="shell-button" type="submit" form="workspace-form">Create</button>
    </template>
  </ShellDialog>
</template>

<style>
.template-layout { padding-bottom: var(--shell-space-7); }
.template-metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 15rem), 1fr)); gap: var(--shell-space-4); }
.template-states { display: grid; gap: var(--shell-space-4); margin-top: var(--shell-space-5); }
.template-states h2 { max-width: 24ch; margin: 0; font-size: clamp(1.4rem, 3vw, 2rem); }
.template-kicker { margin: 0 0 var(--shell-space-2); color: var(--shell-brand); font-size: var(--shell-text-xs); font-weight: 800; letter-spacing: 0.12em; text-transform: uppercase; }
@media (min-width: 75rem) { .template-states { grid-template-columns: minmax(15rem, 0.7fr) minmax(25rem, 1.3fr); align-items: center; } }
</style>
